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

	/**
	 * Jogadores com mudanca CRITICA ainda nao gravada (TP mudou, quest completou). O varredor
	 * salva os marcados a cada 10s — contra kill seco/queda, a janela de perda desses eventos
	 * cai de ate 5 minutos (autosave) pra ate ~10 segundos. Marcar e barato (Set.add); o custo
	 * real e no maximo UM blob por jogador marcado a cada varredura, por mais que ele treine.
	 */
	private static final java.util.Set<UUID> dirty = ConcurrentHashMap.newKeySet();

	/** Mudanca critica: entra na proxima varredura de 10s. Barato e thread-safe; pode chamar de qualquer lado. */
	public static void markDirty(UUID uuid) {
		if (uuid != null) dirty.add(uuid);
	}

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
	// CONTADOR, nao Set: relog rapido cria DOIS loads em voo pro mesmo uuid, e num Set o
	// callback do primeiro (que so confere player.connection e sai) removia a entrada do
	// segundo — isLoadPending virava false com load pendente, e o client recebia o sync
	// default: a tela de criacao de personagem abrindo por cima de personagem existente.
	private static final ConcurrentHashMap<UUID, Integer> pendingLoads = new ConcurrentHashMap<>();

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
		return pendingLoads.containsKey(uuid);
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
		pendingLoads.merge(uuid, 1, Integer::sum);
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
					pendingLoads.compute(uuid, (k, v) -> v == null || v <= 1 ? null : v - 1);
					// ZENKAI (auditoria do TP zerado): re-busca o jogador VIVO por UUID em vez de
					// usar a referencia capturada no login. Se o jogador MORREU durante o load, a
					// referencia antiga e a entidade morta: `connection` continua preenchida (o
					// vanilla transfere pro clone sem anular o campo do morto) e as caps foram
					// invalidadas — o apply virava no-op MUDO, o dado bom do banco era descartado
					// e a revisao ja armada validava o save do clone vazio. O caminho de CONFLICT
					// ja re-buscava; agora o caminho feliz tambem.
					ServerPlayer vivo = ServerLifecycleHooks.getCurrentServer()
							.getPlayerList().getPlayer(uuid);
					if (vivo == null) {
						LogUtil.info(Env.SERVER, "[Load] {} saiu antes da resposta do storage.", name);
						return;
					}
					switch (result.status()) {
						case LOADED -> {
							// Guarda a revisao ANTES de aplicar: e ela que autoriza o proximo save.
							revisions.put(uuid, result.rev());
							LogUtil.info(Env.SERVER, "[Load] {} — dados encontrados (rev {}), aplicando.",
									name, result.rev());
							applyLoadedData(vivo, result.data());
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
							NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(vivo), vivo);
						}
						case FAILED -> {
							// ZENKAI: desarma a revisao cacheada de sessao anterior. Se algum save
							// escapar mesmo com o load falho, expected=-1 vira INSERT -> duplicata ->
							// CONFLICT barulhento que ressincroniza e CURA — nunca um UPDATE
							// silencioso com rev velha gravando estado nao-confiavel por cima.
							revisions.remove(uuid);
							LogUtil.error(Env.SERVER, "[Load] {} — o storage NAO respondeu "
								+ "(veja o erro acima). A criacao de personagem fica bloqueada para ele: "
								+ "os dados podem existir e nao podem ser sobrescritos. Conserte o storage "
								+ "e peca para ele reentrar.", name);
						}
					}
				}))
				.exceptionally(ex -> {
					// Rede de seguranca: load() ja converte falha em FAILED, entao so se chega aqui com
					// algo inesperado. Mesma regra — nao libera nada.
					ServerLifecycleHooks.getCurrentServer().execute(() ->
							pendingLoads.compute(uuid, (k, v) -> v == null || v <= 1 ? null : v - 1));
					LogUtil.error(Env.SERVER, "[Load] " + name + " — erro inesperado carregando dados; "
							+ "criacao de personagem bloqueada para ele.", ex);
					return null;
				});
	}

	private static void applyLoadedData(ServerPlayer player, CompoundTag loadedData) {
		// Antes do load: quem quiser MEXER no NBT que sera aplicado tem esta janela.
		MinecraftForge.EVENT_BUS.post(new DMZEvent.PlayerDataLoadEvent(player, loadedData));

		// ZENKAI: capability ausente aqui significa entidade invalidada (morto/clone) — o
		// dado do banco seria DESCARTADO. Isso ja causou wipe total em silencio; se voltar
		// a acontecer, tem que gritar no log.
		if (!StatsProvider.get(StatsCapability.INSTANCE, player).isPresent()) {
			LogUtil.error(Env.SERVER, "[Load] " + player.getScoreboardName()
					+ " — capability INDISPONIVEL na hora de aplicar os dados do storage; dado "
					+ "do banco descartado. Entidade invalidada? Investigar.", new IllegalStateException());
			return;
		}

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
		// ZENKAI (auditoria do TP zerado): load em voo = a RAM ainda NAO e autoridade sobre
		// este jogador; salvar agora gravaria o pre-load (default ou .dat local) por cima do
		// banco. Vale pro save da troca de proxy, pro logout e pro sweeper. A cerca do
		// destino espera o pendingSave, que devolve completo quando nada foi enfileirado.
		if (isLoadPending(player.getUUID())) {
			LogUtil.info(Env.SERVER, "[Save] {} — recusado: load ainda em voo, nada confiavel "
					+ "a gravar.", player.getScoreboardName());
			return CompletableFuture.completedFuture(null);
		}
		if (!stats.isDataLoaded() && !stats.getStatus().isHasCreatedCharacter()) {
			return CompletableFuture.completedFuture(null);
		}

		CompoundTag dataToSave = stats.save();
		MinecraftForge.EVENT_BUS.post(new DMZEvent.PlayerDataSaveEvent(player, dataToSave));

		String name = player.getScoreboardName();
		UUID uuid = player.getUUID();

		// Borda do shutdown: executor ja anulado (corrida rara — a flag stopping cobre o caso
		// comum). Roda INLINE e FORA do saveChains: um executor sincrono dentro do compute
		// completava a cadeia ali mesmo, o whenComplete tentava remove() da MESMA chave, e
		// compute reentrante em ConcurrentHashMap trava o bin — deadlock na thread do server,
		// no meio do desligamento. O executor foi drenado antes de anular, entao nao ha cadeia
		// pendente e a escrita inline sai na ordem certa.
		java.util.concurrent.Executor saveExecutor = dbExecutor;
		if (saveExecutor == null) {
			try {
				long expected = revisions.getOrDefault(uuid, -1L);
				IDataStorage.SaveOutcome outcome = activeStorage.saveData(uuid, name, dataToSave, expected);
				if (outcome.isOk()) revisions.put(uuid, outcome.newRev());
			} catch (Exception e) {
				LogUtil.error(Env.SERVER, "Failed to save data inline for " + name, e);
			}
			return CompletableFuture.completedFuture(null);
		}
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
						// escrita esta velha e nao entra.
						//
						// CORRECAO: antes daqui so se fazia `revisions.put(uuid, fetchRev(uuid))`,
						// com a justificativa de que "o RAM deste servidor e cumulativo, entao o
						// proximo autosave leva tudo". Isso e FALSO exatamente no caso que produz o
						// conflito: houve conflito PORQUE este servidor carregou ANTES da escrita do
						// outro, ou seja o RAM daqui e a copia VELHA. Ressincronizar a revisao sem
						// mais nada ARMAVA o proximo autosave pra gravar essa copia velha por cima
						// da nova, agora sem conflito nenhum pra barrar. O CAS virava um adiamento
						// da perda, nao a prevencao dela.
						//
						// O sentido certo e o inverso: RECARREGA o registro fresco do banco e aplica
						// no jogador vivo. Dai a revisao e o estado passam a ser os do banco, e o
						// proximo autosave escreve por cima de si mesmo, sem perder nada.
						LoadResult fresco = activeStorage.load(uuid);
						if (fresco.status() == LoadResult.Status.LOADED) {
							revisions.put(uuid, fresco.rev());
							net.minecraft.server.MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
							if (srv != null) srv.execute(() -> {
								ServerPlayer vivo = srv.getPlayerList().getPlayer(uuid);
								if (vivo != null) applyLoadedData(vivo, fresco.data());
							});
							LogUtil.error(Env.SERVER, "[Storage] save de " + name + " recusado por conflito "
									+ "(eu tinha " + expected + ", banco em " + fresco.rev() + "). O estado "
									+ "deste servidor era o VELHO: recarreguei o do banco e reapliquei no "
									+ "jogador. Se isto REPETIR pro mesmo jogador, ha duas sessoes ativas "
									+ "dele na rede.");
						} else {
							// Nao consegui reler: NAO ressincroniza. Deixar a revisao velha faz os
							// saves seguintes continuarem sendo recusados, que e barulhento mas
							// seguro; ressincronizar aqui seria autorizar a escrita velha as cegas.
							LogUtil.error(Env.SERVER, "[Storage] save de " + name + " recusado por conflito "
									+ "e NAO consegui reler o registro (status " + fresco.status() + "). "
									+ "A revisao fica travada de proposito: os proximos saves vao falhar "
									+ "ate o jogador relogar, e isso e melhor que gravar dado velho.");
						}
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
		// Varredor dos marcados (markDirty): kill seco perde no maximo ~10s de TP/quest, em vez
		// dos ate 5 min do autosave. Mesma blindagem de try/catch — excecao que escapa cancela
		// o agendamento pra sempre.
		autoSaveScheduler.scheduleAtFixedRate(() -> {
			try {
				sweepDirty();
			} catch (Throwable t) {
				LogUtil.error(Env.SERVER, "[Storage] varredura de dirty falhou nesta rodada.", t);
			}
		}, 10, 10, TimeUnit.SECONDS);
	}

	/** Salva quem esta marcado e online; snapshot na MAIN thread, escrita no dbExecutor. */
	private static void sweepDirty() {
		if (stopping || activeStorage == null || dirty.isEmpty()) return;
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) return;
		server.execute(() -> {
			if (stopping || activeStorage == null) return;
			java.util.ArrayDeque<UUID> fila = new java.util.ArrayDeque<>();
			java.util.Iterator<UUID> it = dirty.iterator();
			while (it.hasNext()) {
				fila.add(it.next());
				it.remove(); // offline: o save do logout ja cobriu — sair da lista basta
			}
			saveSpread(server, fila);
		});
	}

	/** Jogadores serializados por tick nos saves periodicos. 5 x ~0,5ms = ~2,5ms por tick, invisivel. */
	private static final int SAVES_POR_TICK = 5;

	/**
	 * Salva a fila em FATIAS de {@link #SAVES_POR_TICK} por tick. O snapshot ({@code stats.save()})
	 * tem que rodar na main thread (fora dela e o CME que ja matou autosave), mas rodar TODOS num
	 * tick so e um pico: 60 online x ~0,5ms = ~30ms de um orcamento de 50ms — lag visivel a cada
	 * varredura. Fatiado, o mesmo trabalho vira ~2,5ms por tick durante alguns ticks. A ESCRITA
	 * continua no dbExecutor; so a serializacao e fatiada.
	 */
	private static void saveSpread(MinecraftServer server, java.util.ArrayDeque<UUID> fila) {
		if (stopping || activeStorage == null) return;
		for (int i = 0; i < SAVES_POR_TICK && !fila.isEmpty(); i++) {
			ServerPlayer p = server.getPlayerList().getPlayer(fila.poll());
			if (p != null) savePlayerAsync(p);
		}
		if (!fila.isEmpty()) {
			server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 1,
					() -> saveSpread(server, fila)));
		}
	}

	private static void performAutoSave() {
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null || activeStorage == null) return;

		LogUtil.info(Env.SERVER, "Auto-Saving data...");
		// O SNAPSHOT roda na main thread de proposito. Na thread do scheduler, o stats.save()
		// serializava a capability VIVA enquanto a main thread a mutava, e o getPlayers() era
		// iterado fora da main — o mesmo CME que ja matou o autosave dos goals uma vez. So a
		// ESCRITA continua async (savePlayerAsync manda pro dbExecutor). E FATIADO em
		// SAVES_POR_TICK, senao a correcao de thread viraria um pico de tick a cada autosave.
		server.execute(() -> {
			if (activeStorage == null) return; // shutdown entre o agendamento e a execucao
			java.util.ArrayDeque<UUID> fila = new java.util.ArrayDeque<>();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				fila.add(player.getUUID());
			}
			saveSpread(server, fila);
		});
	}
}