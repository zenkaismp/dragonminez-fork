package com.dragonminez.server.storage;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.GeneralServerConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.*;
import java.sql.*;
import java.util.UUID;
import java.util.regex.Pattern;

public class DatabaseManager implements IDataStorage {
	private static final Pattern VALID_TABLE_NAME = Pattern.compile("^[A-Za-z0-9_]+$");
	private static final String DEFAULT_TABLE = "player_data";

	private HikariDataSource dataSource;
	private boolean isConnected = false;

	public DatabaseManager() {}

	private static String sanitizeTableName(String tableName) {
		if (tableName != null && VALID_TABLE_NAME.matcher(tableName).matches()) {
			return tableName;
		}
		LogUtil.error(Env.SERVER, "Invalid storage table name '" + tableName + "'; falling back to '" + DEFAULT_TABLE + "'.");
		return DEFAULT_TABLE;
	}

	@Override
	public void init() {
		GeneralServerConfig.StorageConfig config = ConfigManager.getServerConfig().getStorage();
		if (!hasValidCredentials(config)) {
			LogUtil.error(Env.SERVER, "DATABASE ERROR: Missing credentials (Host, DB Name, User or Password).");
			LogUtil.error(Env.SERVER, "FALLBACK: System will use Default Local NBT Storage.");
			isConnected = false;
			return;
		}

		LogUtil.info(Env.SERVER, "Connecting to Database: " + config.getHost() + ":" + config.getPort());

		HikariConfig hikariConfig = new HikariConfig();
		String jdbcUrl = "jdbc:mariadb://" + config.getHost() + ":" + config.getPort() + "/" + config.getDatabase();

		// useAffectedRows: o retorno de executeUpdate passa a ser as linhas ALTERADAS, nao as
		// encontradas. Sem isto o default do Connector/J (found-rows) faz um UPDATE no-op
		// devolver 1, e qualquer deteccao de "gravou de verdade?" mente.
		jdbcUrl = jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "useAffectedRows=true";
		hikariConfig.setJdbcUrl(jdbcUrl);
		hikariConfig.setUsername(config.getUsername());
		hikariConfig.setPassword(config.getPassword());
		hikariConfig.setMaximumPoolSize(config.getPoolSize());
		hikariConfig.setPoolName("DragonMineZ-Pool");

		hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
		hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
		hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
		hikariConfig.setConnectionTimeout(5000);

		try {
			dataSource = new HikariDataSource(hikariConfig);
			// Otimista ANTES do createTable: ele (e o ensureRevColumn dentro dele) DERRUBAM a
			// flag quando o schema falha. A versao antiga setava true DEPOIS, sobrescrevendo o
			// false deles — servidor "conectado" com schema quebrado, todo load FAILED.
			isConnected = true;
			createTable(sanitizeTableName(config.getTable()));
			if (isConnected) {
				LogUtil.info(Env.SERVER, "Database connected successfully!");
			} else {
				LogUtil.error(Env.SERVER, "FALLBACK: schema nao pode ser preparado — usando NBT local.");
			}
		} catch (Exception e) {
			LogUtil.error(Env.SERVER, "CRITICAL: Failed to connect to database: " + e.getMessage());
			LogUtil.error(Env.SERVER, "FALLBACK: System will use Default Local NBT Storage to prevent data loss.");
			isConnected = false;
		}
	}

	private boolean hasValidCredentials(GeneralServerConfig.StorageConfig config) {
		return !config.getHost().isEmpty() &&
				!config.getDatabase().isEmpty() &&
				!config.getUsername().isEmpty() &&
				!config.getPassword().isEmpty();
	}

	private void createTable(String tableName) {
		String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
				"uuid VARCHAR(36) PRIMARY KEY, " +
				"name VARCHAR(64), " +
				"data MEDIUMBLOB, " +
				"rev BIGINT NOT NULL DEFAULT 0, " +
				"last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
				");";

		try (Connection conn = dataSource.getConnection();
			 PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.execute();
		} catch (SQLException e) {
			LogUtil.error(Env.SERVER, "Error creating table: " + e.getMessage());
			isConnected = false;
			return;
		}
		ensureRevColumn(tableName);
	}

	/**
	 * Migracao da coluna {@code rev} pra banco que ja existia antes do compare-and-swap.
	 *
	 * <p>O {@code CREATE TABLE IF NOT EXISTS} acima nao toca em tabela existente, entao sem isto
	 * um servidor que ja rodava subiria com o SELECT novo pedindo uma coluna que nao existe e o
	 * load falharia PRA TODO MUNDO — o pior resultado possivel numa mudanca cujo objetivo e
	 * proteger dado.</p>
	 *
	 * <p>Aditivo e idempotente: linha antiga nasce com {@code rev = 0}, que e exatamente o valor
	 * que o primeiro save espera. Roda uma vez por boot e custa uma consulta ao catalogo.</p>
	 */
	private void ensureRevColumn(String tableName) {
		try (Connection conn = dataSource.getConnection()) {
			try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, tableName, "rev")) {
				if (rs.next()) return;
			}
			try (PreparedStatement stmt = conn.prepareStatement(
					"ALTER TABLE " + tableName + " ADD COLUMN rev BIGINT NOT NULL DEFAULT 0")) {
				stmt.execute();
				LogUtil.info(Env.SERVER, "[Storage] coluna 'rev' criada em " + tableName
						+ " — save agora e compare-and-swap (dado velho nao sobrescreve o novo).");
			}
		} catch (SQLException e) {
			LogUtil.error(Env.SERVER, "[Storage] NAO foi possivel criar a coluna 'rev' em " + tableName
					+ ": " + e.getMessage() + ". O load vai falhar ate isso ser resolvido — rode a mao: "
					+ "ALTER TABLE " + tableName + " ADD COLUMN rev BIGINT NOT NULL DEFAULT 0;");
			isConnected = false;
		}
	}

	@Override
	public boolean saveData(UUID uuid, String name, CompoundTag tag) {
		if (!isConnected || dataSource == null) return false;

		String tableName = sanitizeTableName(ConfigManager.getServerConfig().getStorage().getTable());

		String sql = "INSERT INTO " + tableName + " (uuid, name, data) VALUES (?, ?, ?) " +
				"ON DUPLICATE KEY UPDATE name = ?, data = ?, last_updated = CURRENT_TIMESTAMP";

		byte[] dataBytes = nbtToBytes(tag);
		if (dataBytes == null) {
			LogUtil.error(Env.SERVER, "[Storage] NBT de " + name + " nao serializou — escrita ABORTADA.");
			return false;
		}

		try (Connection conn = dataSource.getConnection();
			 PreparedStatement stmt = conn.prepareStatement(sql)) {

			stmt.setString(1, uuid.toString());
			stmt.setString(2, name);
			stmt.setBytes(3, dataBytes);
			stmt.setString(4, name);
			stmt.setBytes(5, dataBytes);

			stmt.executeUpdate();
			return true;
		} catch (SQLException e) {
			LogUtil.error(Env.SERVER, "Failed to save player " + name + " to DB: " + e.getMessage());
			return false;
		}
	}

	@Override
	public CompoundTag loadData(UUID uuid) {
		return load(uuid).data();
	}

	/**
	 * Reads one player, telling apart "no row" from "could not read". Every early return below used to
	 * be a bare {@code null}, which the caller could only read as "new player" — so a dropped
	 * connection or a corrupt blob looked exactly like a first join.
	 */
	@Override
	public LoadResult load(UUID uuid) {
		if (!isConnected || dataSource == null) {
			LogUtil.error(Env.SERVER, "Load for " + uuid + " asked while the database is not connected.");
			return LoadResult.failed();
		}

		String tableName = sanitizeTableName(ConfigManager.getServerConfig().getStorage().getTable());
		// A revisao vem JUNTO com o dado, na mesma consulta: e ela que o save devolve como
		// condicao do UPDATE. Ler em duas queries abriria a propria corrida que isto conserta.
		String sql = "SELECT data, rev FROM " + tableName + " WHERE uuid = ?";

		try (Connection conn = dataSource.getConnection();
			 PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setString(1, uuid.toString());

			try (ResultSet rs = stmt.executeQuery()) {
				if (!rs.next()) return LoadResult.absent();     // consulta OK, jogador novo mesmo
				long rev = rs.getLong("rev");
				try (InputStream is = rs.getBinaryStream("data")) {
					if (is == null) {
						LogUtil.error(Env.SERVER, "Row for " + uuid + " exists but its data column is NULL.");
						return LoadResult.failed();            // linha existe: NAO e jogador novo
					}
					return LoadResult.loaded(NbtIo.readCompressed(is), rev);
				} catch (IOException e) {
					LogUtil.error(Env.SERVER, "Error decompressing NBT for " + uuid + ": " + e.getMessage());
					return LoadResult.failed();
				}
			}
		} catch (SQLException e) {
			LogUtil.error(Env.SERVER, "Failed to load player " + uuid + " from DB: " + e.getMessage());
			return LoadResult.failed();
		}
	}

	/**
	 * Save com compare-and-swap: so grava se a linha ainda estiver na revisao que ESTE servidor
	 * leu. Nao custa consulta a mais — e o mesmo UPDATE, com uma condicao no WHERE.
	 *
	 * <p>Duas formas, escolhidas pelo que o load respondeu:</p>
	 * <ul>
	 *   <li>{@code expectedRev >= 0} — a linha EXISTE e eu li a versao dela. UPDATE condicional;
	 *       0 linhas = outro backend gravou no meio, meu dado e o velho. O zero e INCLUIDO de
	 *       proposito: linha legada (de antes da coluna rev, ou gravada por um build antigo da
	 *       rede) nasce com rev = 0 e tem que ser atualizavel — a primeira versao tratava 0 como
	 *       "jogador novo" e essas linhas ficavam INESCREVIVEIS pra sempre.</li>
	 *   <li>{@code expectedRev < 0} — o load respondeu ABSENT: nao havia linha. INSERT puro, e
	 *       chave duplicada (a linha nasceu enquanto eu carregava) vira CONFLICT pela EXCECAO de
	 *       integridade — deterministico em qualquer modo do driver, ao contrario do retorno de
	 *       um {@code ON DUPLICATE KEY UPDATE} no-op, que em found-rows respondia 1 e virava um
	 *       "ok" falso com o banco intacto.</li>
	 * </ul>
	 */
	@Override
	public SaveOutcome saveData(UUID uuid, String name, CompoundTag tag, long expectedRev) {
		if (!isConnected || dataSource == null) return SaveOutcome.failed();

		String tableName = sanitizeTableName(ConfigManager.getServerConfig().getStorage().getTable());
		byte[] dataBytes = nbtToBytes(tag);
		if (dataBytes == null || dataBytes.length == 0) {
			// Serializacao falhou. Gravar um blob vazio passaria no CAS, avancaria a revisao e
			// DESTRUIRIA o dado bom — e todo load seguinte morreria no readCompressed.
			LogUtil.error(Env.SERVER, "[Storage] NBT de " + name + " nao serializou — escrita ABORTADA "
					+ "(gravar vazio destruiria o dado bom).");
			return SaveOutcome.failed();
		}

		try (Connection conn = dataSource.getConnection()) {
			if (expectedRev < 0) {
				long newRev = 1L;
				String sql = "INSERT INTO " + tableName + " (uuid, name, data, rev) VALUES (?, ?, ?, ?)";
				try (PreparedStatement stmt = conn.prepareStatement(sql)) {
					stmt.setString(1, uuid.toString());
					stmt.setString(2, name);
					stmt.setBytes(3, dataBytes);
					stmt.setLong(4, newRev);
					stmt.executeUpdate();
					return SaveOutcome.ok(newRev);
				} catch (java.sql.SQLIntegrityConstraintViolationException dup) {
					return SaveOutcome.conflict();
				}
			}

			long newRev = expectedRev + 1;
			String sql = "UPDATE " + tableName + " SET name = ?, data = ?, rev = ?, "
					+ "last_updated = CURRENT_TIMESTAMP WHERE uuid = ? AND rev = ?";
			try (PreparedStatement stmt = conn.prepareStatement(sql)) {
				stmt.setString(1, name);
				stmt.setBytes(2, dataBytes);
				stmt.setLong(3, newRev);
				stmt.setString(4, uuid.toString());
				stmt.setLong(5, expectedRev);
				return stmt.executeUpdate() > 0 ? SaveOutcome.ok(newRev) : SaveOutcome.conflict();
			}
		} catch (SQLException e) {
			LogUtil.error(Env.SERVER, "Failed to save player " + name + " to DB: " + e.getMessage());
			return SaveOutcome.failed();
		}
	}

	/**
	 * Revisao ATUAL da linha, direto do banco: -1 = linha nao existe, -2 = consulta falhou.
	 * E a saida do CONFLICT — o StorageManager ressincroniza a revisao local com isto e o
	 * PROXIMO autosave volta a gravar, em vez de recusar a sessao inteira.
	 */
	@Override
	public long fetchRev(UUID uuid) {
		if (!isConnected || dataSource == null) return -2L;
		String tableName = sanitizeTableName(ConfigManager.getServerConfig().getStorage().getTable());
		try (Connection conn = dataSource.getConnection();
			 PreparedStatement stmt = conn.prepareStatement(
					 "SELECT rev FROM " + tableName + " WHERE uuid = ?")) {
			stmt.setString(1, uuid.toString());
			try (ResultSet rs = stmt.executeQuery()) {
				return rs.next() ? rs.getLong("rev") : -1L;
			}
		} catch (SQLException e) {
			LogUtil.error(Env.SERVER, "[Storage] fetchRev de " + uuid + " falhou: " + e.getMessage());
			return -2L;
		}
	}

	@Override
	public void shutdown() {
		if (dataSource != null && !dataSource.isClosed()) {
			dataSource.close();
			LogUtil.info(Env.SERVER, "Database connection closed.");
		}
	}

	@Override
	public String getName() {
		return "DATABASE (MariaDB/MySQL)";
	}

	/** null = serializacao falhou. NUNCA devolva vazio: vazio gravado destroi o blob bom. */
	private byte[] nbtToBytes(CompoundTag tag) {
		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			NbtIo.writeCompressed(tag, outputStream);
			byte[] out = outputStream.toByteArray();
			return out.length > 0 ? out : null;
		} catch (IOException e) {
			LogUtil.error(Env.SERVER, "Error serializing NBT: " + e.getMessage());
			return null;
		}
	}
}