package com.dragonminez.server.storage;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.GeneralServerConfig;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.util.TransformationsHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.UUID;
import java.util.concurrent.*;

public class StorageManager {
	private static IDataStorage activeStorage;
	private static ScheduledExecutorService autoSaveScheduler;
	private static ExecutorService dbExecutor;
	private static final ConcurrentHashMap<UUID, CompletableFuture<Void>> saveChains = new ConcurrentHashMap<>();

	/**
	 * Revisao do registro que ESTE servidor leu, por jogador. E o outro lado do compare-and-swap:
	 * o save so grava se o banco ainda estiver nesta revisao.
	 *
	 * <p>NUNCA e limpa no logout, de proposito: todo login refaz o put no load, e nenhum save
	 * roda antes do load (isDataLoaded barra). Um forget pos-save era corrida com relog rapido:
	 * apagava a revisao que o login novo tinha acabado de carregar e todo save virava CONFLICT.</p>
	 */
	private static final ConcurrentHashMap<UUID, Long> revisions = new ConcurrentHashMap<>();

	public static void init() {
		stopping = false; // reload passa por shutdown()+init(): reabre os saves
		GeneralServerConfig.StorageConfig.StorageType type = ConfigManager.getServerConfig().getStorage().getStorageType();

		switch (type) {
			case DATABASE -> activeStorage = new DatabaseManager();
			case JSON -> activeStorage = new JsonStorage();
			case NBT -> {
				LogUtil.info(Env.SERVER, "Using default NBT storage (Vanilla).");
				activeStorage = null;
			}
		}

		if (activeStorage != null) {
			activeStorage.init();
			int threads = ConfigManager.getServerConfig().getStorage().getThreadPoolSize();
			if (dbExecutor != null) {
				shutdownDbExecutor();
			}
			dbExecutor = Executors.newFixedThreadPool(threads);
			LogUtil.info(Env.SERVER, "Storage initialized with " + threads + " async threads.");
			startAutoSave();
		}
	}

	public static void reload() {
		LogUtil.info(Env.SERVER, "Reloading Storage Subsystem...");

		if (ServerLifecycleHooks.getCurrentServer() != null) {
			LogUtil.info(Env.SERVER, "Saving online players before storage switch...");
			for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
				savePlayer(player);
			}
		}

		shutdown();
		init();
		LogUtil.info(Env.SERVER, "Storage Subsystem reloaded. Active: " + (activeStorage == null ? "NBT (Vanilla)" : activeStorage.getName()));
	}

	/**
	 * Desligamento do storage — a ORDEM aqui e o conserto da armadilha do /stop.
	 *
	 * <p>O ServerStoppingEvent dispara ANTES do PlayerList desconectar os jogadores. A versao
	 * antiga derrubava o executor e fechava o pool logo aqui; quando os logouts finalmente
	 * disparavam o save, o {@code thenRunAsync(..., null)} estourava NPE e a escrita NUNCA
	 * acontecia — TP setado segundos antes do /stop simplesmente evaporava, e o proximo servidor
	 * carregava o estado velho. Documentado na auditoria de 2026-07-26 como armadilha #3;
	 * reproduzido em teste real (set TP, 5s, /stop, dado perdido).</p>
	 *
	 * <p>Agora: (1) salva TODO MUNDO online e ESPERA as escritas pousarem, com a infra ainda de
	 * pe; (2) marca {@code stopping}, que transforma os saves de logout que vem em seguida em
	 * no-op — os dados deles acabaram de ser gravados e nenhum tick roda entre uma coisa e
	 * outra; (3) so entao drena o executor e fecha o pool, NESTA ordem, porque fechar o pool
	 * com escrita em voo no executor era outra forma de perder dado.</p>
	 */
	public static void shutdown() {
		flushOnlinePlayers();
		stopping = true;
		if (autoSaveScheduler != null && !autoSaveScheduler.isShutdown()) {
			autoSaveScheduler.shutdown();
		}
		if (dbExecutor != null) {
			shutdownDbExecutor();
			dbExecutor = null;
		}
		if (activeStorage != null) {
			activeStorage.shutdown();
		}
	}

	/**
	 * True entre o flush do desligamento e o proximo {@link #init}. Com a flag acesa o
	 * {@link #savePlayerAsync} vira no-op: os logouts do /stop chegam DEPOIS do flush, os dados
	 * ja estao no banco e a infra ja esta desligada — tentar de novo so geraria um erro por
	 * jogador em cima de um pool fechado.
	 */
	private static volatile boolean stopping = false;

	/** Salva todos os online e BLOQUEIA ate as escritas pousarem (teto 15s). So no desligamento. */
	private static void flushOnlinePlayers() {
		if (activeStorage == null || dbExecutor == null) return;
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) return;
		java.util.List<CompletableFuture<Void>> pending = new java.util.ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			pending.add(savePlayerAsync(player));
		}
		if (pending.isEmpty()) return;
		try {
			CompletableFuture.allOf(pending.toArray(new CompletableFuture[0])).get(15, TimeUnit.SECONDS);
			LogUtil.info(Env.SERVER, "[Storage] " + pending.size()
					+ " jogador(es) salvos antes do desligamento.");
		} catch (TimeoutException e) {
			LogUtil.error(Env.SERVER, "[Storage] flush de desligamento passou de 15s com "
					+ pending.size() + " jogador(es) na fila — o drain do executor da mais 30s; o "
					+ "que nao pousar nesse total e descartado COM log de quantas se perderam.");
		} catch (Exception e) {
			LogUtil.error(Env.SERVER, "[Storage] flush de desligamento falhou.", e);
		}
	}

	private static void shutdownDbExecutor() {
		dbExecutor.shutdown();
		try {
			// 30s, nao 5: no /stop com MySQL lento a fila ainda tem as escritas FINAIS dos
			// jogadores, e o shutdownNow as descarta. 30s de boot mais lento e barato; o
			// progresso de quem estava online nao tem preco.
			if (!dbExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
				int dropped = dbExecutor.shutdownNow().size();
				LogUtil.error(Env.SERVER, "[Storage] executor nao drenou em 30s — " + dropped
						+ " escrita(s) DESCARTADA(S). Esses jogadores regridem ao ultimo save que "
						+ "pousou. O MySQL esta lento demais pra fila deste desligamento.");
			}
		} catch (InterruptedException e) {
			int dropped = dbExecutor.shutdownNow().size();
			if (dropped > 0) {
				LogUtil.error(Env.SERVER, "[Storage] drain interrompido — " + dropped
						+ " escrita(s) descartada(s).");
			}
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Players whose external storage lookup is still in flight. While a player is in here nobody may
	 * tell the client "your data is loaded" — see {@link #isLoadPending(UUID)}.
	 */
	private static final java.util.Set<UUID> pendingLoads = ConcurrentHashMap.newKeySet();

	/**
	 * True between login and the moment the storage backend answers for this player.
	 *
	 * <p>Exists because the login sync would otherwise ship the DEFAULT stats to the client: the client
	 * marks the data as loaded, sees {@code hasCreatedCharacter = false} and opens character creation on
	 * top of a character that exists in the database. On a single server with a local {@code .dat} the
	 * capability is already populated by login time and this never shows; across a network, where the
	 * world folder has never seen the player, it shows every join.</p>
	 */
	/**
	 * Portao opcional ANTES do load: espera enquanto outro backend ainda esta gravando este
	 * jogador.
	 *
	 * <p>Numa rede, trocar de servidor e um logout seguido de um login em maquinas diferentes.
	 * O save de la e o load de ca sao duas operacoes assincronas sem nada em comum, entao o
	 * destino consultava o banco enquanto a origem ainda escrevia e carregava o estado anterior.
	 * O compare-and-swap impede o dado velho de ser GRAVADO, mas nao impede ele de ser LIDO —
	 * e um jogador com stats velhos na tela ja e o bug, mesmo que o banco fique intacto.</p>
	 *
	 * <p>O mod nao conhece Redis nem a topologia da rede, entao quem sabe esperar e o addon: ele
	 * registra a espera aqui. Sem addon o default nao espera nada e o comportamento e o de
	 * sempre.</p>
	 */
	private static volatile java.util.function.Function<UUID, CompletableFuture<Void>> loadGate =
			uuid -> CompletableFuture.completedFuture(null);

	public static void setLoadGate(java.util.function.Function<UUID, CompletableFuture<Void>> gate) {
		loadGate = gate != null ? gate : uuid -> CompletableFuture.completedFuture(null);
	}

	/**
	 * Escrita pendente deste jogador NESTE servidor (ja completa se nao ha nenhuma). O addon usa
	 * pra so derrubar a cerca do Redis quando o save do logout de fato POUSOU — derrubar antes
	 * deixava o servidor de destino ler o banco no meio da escrita.
	 */
	public static CompletableFuture<Void> pendingSave(UUID uuid) {
		return saveChains.getOrDefault(uuid, CompletableFuture.completedFuture(null));
	}

	public static boolean isLoadPending(UUID uuid) {
		return pendingLoads.contains(uuid);
	}

	public static void loadPlayer(ServerPlayer player) {
		if (activeStorage == null) {
			// NBT/vanilla: os dados vieram do .dat junto com o jogador, nao ha nada a esperar.
			LogUtil.info(Env.SERVER, "[Load] {} — storage NBT (vanilla), nada a carregar.",
					player.getName().getString());
			return;
		}

		final UUID uuid = player.getUUID();
		final String name = player.getName().getString();
		pendingLoads.add(uuid);
		LogUtil.info(Env.SERVER, "[Load] {} ({}) — consultando {}; criacao de personagem em espera.",
				name, uuid, activeStorage.getName());

		// Antes de tudo, a PROPRIA fila: relog rapido no MESMO servidor podia ler o banco em
		// paralelo com a escrita do logout anterior ainda na fila (pool > 1 thread) — dado velho
		// na tela e revisao envenenada. O load espera o save pendente DESTE jogador terminar.
		// Depois, o portao da rede (SaveFence do addon) — e so entao a consulta: esperar depois
		// de ler nao adiantaria nada.
		CompletableFuture<Void> localPending =
				saveChains.getOrDefault(uuid, CompletableFuture.completedFuture(null));
		localPending.exceptionally(ex -> null)
				.thenCompose(v0 -> loadGate.apply(uuid))
				.exceptionally(ex -> {
					// Portao quebrado nao pode prender ninguem fora do jogo: segue pro load.
					LogUtil.error(Env.SERVER, "[Load] " + name + " — o portao de load falhou; "
							+ "seguindo direto pro storage.", ex);
					return null;
				})
				.thenComposeAsync(v -> CompletableFuture.supplyAsync(() -> activeStorage.load(uuid), dbExecutor))
				.thenAccept(result -> ServerLifecycleHooks.getCurrentServer().execute(() -> {
					pendingLoads.remove(uuid);
					if (player.connection == null) {
						LogUtil.info(Env.SERVER, "[Load] {} saiu antes da resposta do storage.", name);
						return;
					}
					switch (result.status()) {
						case LOADED -> {
							// Guarda a revisao ANTES de aplicar: e ela que autoriza o proximo save.
							revisions.put(uuid, result.rev());
							LogUtil.info(Env.SERVER, "[Load] {} — dados encontrados (rev {}), aplicando.",
									name, result.rev());
							applyLoadedData(player, result.data());
						}
						case ABSENT -> {
							// Sem registro: -1 = "nao havia linha". O save vira INSERT puro, e chave
							// duplicada (linha nasceu enquanto eu carregava) vira CONFLICT. NAO use 0
							// aqui: 0 e revisao LEGITIMA de linha legada (pre-coluna rev), e o
							// sentinela colidindo tornava essas linhas inescreviveis pra sempre.
							revisions.put(uuid, -1L);
							// O storage RESPONDEU que nao existe registro: jogador novo de verdade.
							// Solta o sync que o login segurou, senao o cliente nunca sabe que pode
							// abrir a criacao de personagem.
							LogUtil.info(Env.SERVER, "[Load] {} — sem registro no storage: jogador novo, "
									+ "liberando a criacao de personagem.", name);
							NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
						}
						case FAILED -> LogUtil.error(Env.SERVER, "[Load] {} — o storage NAO respondeu "
								+ "(veja o erro acima). A criacao de personagem fica bloqueada para ele: "
								+ "os dados podem existir e nao podem ser sobrescritos. Conserte o storage "
								+ "e peca para ele reentrar.", name);
					}
				}))
				.exceptionally(ex -> {
					// Rede de seguranca: load() ja converte falha em FAILED, entao so se chega aqui com
					// algo inesperado. Mesma regra — nao libera nada.
					ServerLifecycleHooks.getCurrentServer().execute(() -> pendingLoads.remove(uuid));
					LogUtil.error(Env.SERVER, "[Load] " + name + " — erro inesperado carregando dados; "
							+ "criacao de personagem bloqueada para ele.", ex);
					return null;
				});
	}

	private static void applyLoadedData(ServerPlayer player, CompoundTag loadedData) {
		// Antes do load: quem quiser MEXER no NBT que sera aplicado tem esta janela.
		MinecraftForge.EVENT_BUS.post(new DMZEvent.PlayerDataLoadEvent(player, loadedData));

		StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
			try {
				stats.load(loadedData);
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			}

			if (stats.getPlayerQuestData().isSagaLocked("saiyan_saga")) {
				stats.getPlayerQuestData().setSagaUnlocked("saiyan_saga", true);
			}

			TransformationsHelper.ensureSelectedFormDefault(stats);
			TransformationsHelper.ensureSelectedStackFormDefault(stats);

			// Dados ja na capability e o sync ainda nao saiu: e AQUI que um addon concede/ajusta o que
			// depende deles. Quem faz isso no login le os defaults e tem o proprio trabalho apagado
			// por este load — e refaz tudo no login seguinte, para sempre.
			MinecraftForge.EVENT_BUS.post(new DMZEvent.PlayerDataAppliedEvent(player));

			NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
			LogUtil.info(Env.SERVER, "Async data loaded for: " + player.getName().getString());
		});
	}

	public static void savePlayer(ServerPlayer player) {
		savePlayerAsync(player);
	}

	/**
	 * Igual ao {@link #savePlayer}, mas devolve QUANDO a escrita terminou.
	 *
	 * <p>Existe pela troca de servidor. O save sempre foi disparado no
	 * {@code PlayerLoggedOutEvent} e ninguem esperava por ele; quem manda o jogador pra outro
	 * backend desconectava na hora, e o servidor de destino frequentemente consultava o banco
	 * ANTES desta escrita pousar. Resultado: o destino carregava o estado anterior, e cinco
	 * minutos depois o autosave DELE gravava esse estado velho por cima — o ganho sumia de vez.
	 * Agora o caminho da troca encadeia o disconnect neste future.</p>
	 *
	 * <p>Nunca completa excepcionalmente: falha vira log e o future fecha mesmo assim, senao um
	 * banco fora do ar prenderia o jogador no servidor de origem.</p>
	 *
	 * @return future que fecha no fim da escrita (ja completo se nao havia nada a salvar)
	 */
	public static CompletableFuture<Void> savePlayerAsync(ServerPlayer player) {
		if (activeStorage == null) return CompletableFuture.completedFuture(null);
		// Logout disparado pelo /stop: o flush do shutdown() acabou de gravar todo mundo e a
		// infra ja esta caindo. Repetir aqui so renderia um erro por jogador num pool fechado.
		if (stopping) return CompletableFuture.completedFuture(null);

		StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
		if (stats == null) return CompletableFuture.completedFuture(null);
		if (!stats.isDataLoaded() && !stats.getStatus().isHasCreatedCharacter()) {
			return CompletableFuture.completedFuture(null);
		}

		CompoundTag dataToSave = stats.save();
		MinecraftForge.EVENT_BUS.post(new DMZEvent.PlayerDataSaveEvent(player, dataToSave));

		String name = player.getScoreboardName();
		UUID uuid = player.getUUID();

		// Snapshot do executor: se o shutdown anular no meio (corrida rara), a escrita roda
		// INLINE na thread de quem chamou em vez de NPEar — o NPE era exatamente como o /stop
		// perdia dado. Runnable::run como Executor = executa na hora, sem fila.
		java.util.concurrent.Executor saveExecutor = dbExecutor != null ? dbExecutor : Runnable::run;
		return saveChains.compute(uuid, (id, previous) -> {
			CompletableFuture<Void> previousStage = previous != null ? previous : CompletableFuture.completedFuture(null);
			CompletableFuture<Void> chained = previousStage.thenRunAsync(() -> {
				try {
					// -1 quando nao ha entrada: "nao sei" vira INSERT puro, que NUNCA sobrescreve
					// (dup = CONFLICT barulhento) — fail-safe na direcao certa.
					long expected = revisions.getOrDefault(uuid, -1L);
					IDataStorage.SaveOutcome outcome = activeStorage.saveData(uuid, name, dataToSave, expected);
					if (outcome.isOk()) {
						revisions.put(uuid, outcome.newRev());
					} else if (outcome.isConflict()) {
						// A protecao funcionando: outro backend gravou depois do meu load, entao ESTA
						// escrita esta velha e nao entra. Mas conflito nao pode ser TERMINAL: sem
						// ressincronizar a revisao, todo save da sessao (autosave e logout) seria
						// recusado em silencio e a sessao inteira se perderia. Recarrega a revisao
						// atual do banco — o RAM deste servidor e cumulativo, entao o proximo
						// autosave leva tudo que esta escrita levaria, e passa.
						long fresh = activeStorage.fetchRev(uuid);
						if (fresh >= -1L) revisions.put(uuid, fresh);
						LogUtil.error(Env.SERVER, "[Storage] save de " + name + " pulado por conflito de "
								+ "revisao (eu tinha " + expected + ", banco esta em " + fresh + "): outro "
								+ "servidor gravou depois do meu load. Revisao ressincronizada — o proximo "
								+ "autosave grava. Se isto REPETIR pro mesmo jogador, ha duas sessoes "
								+ "ativas dele na rede.");
					}
				} catch (Exception e) {
					LogUtil.error(Env.SERVER, "Failed to save data async for " + name, e);
				}
			}, saveExecutor);
			chained.whenComplete((v, ex) -> saveChains.remove(uuid, chained));
			return chained;
		});
	}

	private static void startAutoSave() {
		autoSaveScheduler = Executors.newSingleThreadScheduledExecutor();
		// try/catch OBRIGATORIO: scheduleAtFixedRate cancela a tarefa PRA SEMPRE na primeira
		// excecao que escapar, em silencio — "todo mundo perdendo progresso desde ontem" e a
		// cara exata desse cancelamento (ja aconteceu com o autosave dos goals).
		autoSaveScheduler.scheduleAtFixedRate(() -> {
			try {
				performAutoSave();
			} catch (Throwable t) {
				LogUtil.error(Env.SERVER, "[Storage] autosave falhou nesta rodada (a proxima roda normal).", t);
			}
		}, 5, 5, TimeUnit.MINUTES);
	}

	private static void performAutoSave() {
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null || activeStorage == null) return;

		LogUtil.info(Env.SERVER, "Auto-Saving data...");
		// O SNAPSHOT roda na main thread de proposito. Na thread do scheduler, o stats.save()
		// serializava a capability VIVA enquanto a main thread a mutava, e o getPlayers() era
		// iterado fora da main — o mesmo CME que ja matou o autosave dos goals uma vez. So a
		// ESCRITA continua async (savePlayerAsync manda pro dbExecutor). Bonus: com todos os
		// chamadores na main thread, a ordem da fila de saves e a ordem dos snapshots.
		server.execute(() -> {
			if (activeStorage == null) return; // shutdown entre o agendamento e a execucao
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				savePlayerAsync(player);
			}
		});
	}
}