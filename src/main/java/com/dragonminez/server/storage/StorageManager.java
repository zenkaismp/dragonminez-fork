package com.dragonminez.server.storage;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.GeneralServerConfig;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.util.TransformationsHelper;
import net.minecraft.nbt.CompoundTag;
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

	public static void init() {
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

	public static void shutdown() {
		if (autoSaveScheduler != null && !autoSaveScheduler.isShutdown()) {
			autoSaveScheduler.shutdown();
		}
		if (activeStorage != null) {
			activeStorage.shutdown();
		}
		if (dbExecutor != null) {
			shutdownDbExecutor();
			dbExecutor = null;
		}
	}

	private static void shutdownDbExecutor() {
		dbExecutor.shutdown();
		try {
			if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
				dbExecutor.shutdownNow();
			}
		} catch (InterruptedException e) {
			dbExecutor.shutdownNow();
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

		CompletableFuture.supplyAsync(() -> activeStorage.load(uuid), dbExecutor)
				.thenAccept(result -> ServerLifecycleHooks.getCurrentServer().execute(() -> {
					pendingLoads.remove(uuid);
					if (player.connection == null) {
						LogUtil.info(Env.SERVER, "[Load] {} saiu antes da resposta do storage.", name);
						return;
					}
					switch (result.status()) {
						case LOADED -> {
							LogUtil.info(Env.SERVER, "[Load] {} — dados encontrados, aplicando.", name);
							applyLoadedData(player, result.data());
						}
						case ABSENT -> {
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
		if (activeStorage == null) return;

		StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
			if (!stats.isDataLoaded() && !stats.getStatus().isHasCreatedCharacter()) return;
			CompoundTag dataToSave = stats.save();

			MinecraftForge.EVENT_BUS.post(new DMZEvent.PlayerDataSaveEvent(player, dataToSave));

			String name = player.getScoreboardName();
			UUID uuid = player.getUUID();

			saveChains.compute(uuid, (id, previous) -> {
				CompletableFuture<Void> previousStage = previous != null ? previous : CompletableFuture.completedFuture(null);
				CompletableFuture<Void> chained = previousStage.thenRunAsync(() -> {
					try {
						activeStorage.saveData(uuid, name, dataToSave);
					} catch (Exception e) {
						LogUtil.error(Env.SERVER, "Failed to save data async for " + name, e);
					}
				}, dbExecutor);
				chained.whenComplete((v, ex) -> saveChains.remove(uuid, chained));
				return chained;
			});
		});
	}

	private static void startAutoSave() {
		autoSaveScheduler = Executors.newSingleThreadScheduledExecutor();
		autoSaveScheduler.scheduleAtFixedRate(StorageManager::performAutoSave, 5, 5, TimeUnit.MINUTES);
	}

	private static void performAutoSave() {
		if (ServerLifecycleHooks.getCurrentServer() == null || activeStorage == null) return;

		LogUtil.info(Env.SERVER, "Auto-Saving data...");
		for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
			savePlayer(player);
		}
	}
}