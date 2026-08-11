package com.dragonminez.common.quest;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.Reference;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Regeneracao por tempo dos mobs de quest (o "anti covarde" do autobalanceador).
 *
 * <h2>O problema que isto fecha</h2>
 * Sem gate de stat minimo (decisao de 2026-07-25), um build de quase so VIT nao morre nunca e
 * mata qualquer boss na base do desgaste: bate, corre, volta. O NPC nao regenera, entao a luta
 * calibrada pra 60 segundos vira meia hora de bater e fugir. Isto NAO adiciona regeneracao
 * passiva: o NPC so comeca a curar quando a luta ja passou MUITO do tempo que o autobalanceador
 * calculou pra ela ({@code duracao-da-luta} do YAML, emitido no JSON como {@code fight_duration}).
 *
 * <h2>Os dois degraus (proporcao fixa pra toda quest, derivada de T)</h2>
 * <ul>
 *   <li><b>Degrau 1</b> aos 2xT: regen de 66% do DPS de referencia da quest (o DPS que mata o
 *       mob em exatamente T). Piso matematico: quem faz menos de 66% do dano esperado nunca
 *       mais mata o mob depois que o degrau 1 arma.</li>
 *   <li><b>Degrau 2</b> aos 4xT: a regen TOTAL sobe pra 100% do DPS de referencia. Empatar com
 *       o jogador de referencia vira derrota: so mata quem faz MAIS dano que ele.</li>
 * </ul>
 *
 * <p>A cura pulsa a cada varredura (1 segundo), com o valor por segundo da taxa: mesma
 * quantidade total dos pulsos grandes de antes (5,5%/T4 e 11%/T6), so continua e suave. Isso
 * tambem eliminou a contabilidade de intervalo (lastHeal e o agendamento por soma), que era
 * fonte de deriva de quantizacao apontada na revisao.</p>
 *
 * <p>Historico de tuning: doc 64 fechou 3,5xT/5xT com 22%/88% de piso; o dono apertou os
 * gatilhos pra 2xT/4xT em 2026-08-10 e subiu os pisos pra 66%/100% em 2026-08-11, com a cura
 * espalhada em pulsos de 1s.</p>
 *
 * <p>O relogio NAO corre a partir do spawn: ele so arma no PRIMEIRO dano vindo de player
 * ({@code dmz_fight_engaged}). Sem isso, um mob esquecido queimando numa fogueira armava os
 * degraus sem luta nenhuma, e o dono recebia "you took too long" sem nunca ter encostado.</p>
 *
 * <p>A cura usa {@code setHealth}, nunca {@code heal()}: o {@code heal()} dispara
 * {@code LivingHealEvent} e o proprio mod tem um debuff de HP_REGEN por tecnica de ki
 * ({@code EntityStatDebuffHandler}) que cortaria a cura em ate 50%. O piso matematico dos
 * degraus e a regra do sistema; nenhum modificador externo pode mexer nela.</p>
 *
 * <h2>Por que isto nao pesa com 130 players (o requisito de projeto)</h2>
 * <ul>
 *   <li><b>Nenhuma varredura de mundo.</b> O mob entra no mapa quando o
 *       {@link EntityJoinLevelEvent} ve os tags que o {@link QuestService} gravou ANTES do
 *       {@code addFreshEntity}: isso cobre o spawn inicial, o chunk load e a entidade nova da
 *       transformacao com o MESMO codigo. Custo fora de luta: um {@code contains()} de NBT por
 *       entidade carregada, so no load dela, e um {@code contains()} por evento de dano.</li>
 *   <li><b>O tick e 1x por segundo sobre um mapa que so tem lutas de quest vivas</b> (dezenas de
 *       entradas no pior caso, nunca milhares), e o caso comum (mapa vazio) sai no primeiro
 *       {@code isEmpty()}. Por entrada: um lookup por UUID e meia duzia de comparacoes de long.
 *       Nada aloca, nada sai da main thread, nada toca banco.</li>
 *   <li><b>Chunk descarregado nao vaza memoria:</b> a entrada e descartada depois de 5 leituras
 *       vazias; quando o chunk volta, o join event re-registra sozinho, com o relogio intacto no
 *       NBT do mob. Chunk carregado mas SEM entity ticking (borda de FULL) nao cura nem avisa:
 *       mob congelado nao esta em luta.</li>
 * </ul>
 *
 * <h2>O reset por vida cheia</h2>
 * Quando os degraus ja estao ativos e o mob volta pra vida CHEIA, o relogio desarma e volta a
 * esperar o proximo hit de player. Quem abandona a luta e deixa a regen encher o mob comeca uma
 * luta nova ao voltar, sem regen ligada de saida. A forma nova de uma transformacao tambem nasce
 * com relogio zerado (o {@code DBSagasEntity} copia o T mas zera o start e o engaged).
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuestOvertimeRegen {

	/** Duracao de referencia da luta em segundos (int), JA escalada pela dificuldade no spawn. */
	public static final String FIGHT_T_TAG = "dmz_fight_t";
	/** Game time (ticks) em que o relogio desta luta comecou a contar. */
	public static final String FIGHT_START_TAG = "dmz_fight_start";
	/** Primeiro dano de player ja aconteceu: o relogio esta correndo. */
	public static final String FIGHT_ENGAGED_TAG = "dmz_fight_engaged";
	/** Degrau armado (a regen esta ativa). Separado do aviso: armar nao depende de dono online. */
	private static final String ARMED_TIER1_TAG = "dmz_fight_armed1";
	private static final String ARMED_TIER2_TAG = "dmz_fight_armed2";
	/** Aviso ENTREGUE ao dono. Se ele estava offline, tenta de novo a cada varredura. */
	private static final String WARNED_TIER1_TAG = "dmz_fight_warned1";
	private static final String WARNED_TIER2_TAG = "dmz_fight_warned2";

	/**
	 * Taxas em fracao do DPS DE REFERENCIA da quest (que por definicao e {@code maxHP / T} por
	 * segundo). Degrau 1 sozinho = 66%; com o degrau 2 somado = 100% exato. A cura por segundo
	 * de cada degrau sai de {@code maxHP * FRACAO / T}.
	 */
	private static final double TIER1_DPS_FRACTION = 0.66;
	private static final double TIER2_EXTRA_DPS_FRACTION = 0.34;

	private static final int SCAN_EVERY_TICKS = 20;
	/** 5 varreduras (~5s) sem achar a entidade = chunk descarregou; o join event re-registra. */
	private static final int EVICT_AFTER_MISSES = 5;

	/**
	 * Estado em memoria de UMA luta rastreada. O que precisa sobreviver a reload mora no NBT.
	 * Sem contabilidade de cura: o pulso e por varredura (1s), entao "quando curei por ultimo"
	 * deixou de existir como estado.
	 */
	private static final class Fight {
		final ResourceKey<Level> dim;
		int misses;

		Fight(ResourceKey<Level> dim) {
			this.dim = dim;
		}
	}

	/** So main thread (join event, hurt event e tick de servidor). HashMap comum, sem lock. */
	private static final Map<UUID, Fight> ACTIVE = new HashMap<>();

	private QuestOvertimeRegen() {
	}

	@SubscribeEvent
	public static void onEntityJoin(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level)) {
			return;
		}
		if (!(event.getEntity() instanceof LivingEntity le)) {
			return;
		}
		CompoundTag pd = le.getPersistentData();
		// Um contains() por entidade carregada. Quem nao e mob de quest calibrado sai aqui.
		if (!pd.contains(FIGHT_START_TAG) || pd.getInt(FIGHT_T_TAG) <= 0) {
			return;
		}
		ACTIVE.put(le.getUUID(), new Fight(level.dimension()));
	}

	/**
	 * O relogio arma no primeiro dano de PLAYER, nunca no spawn. {@code getSource().getEntity()}
	 * ja resolve o dono de projetil (flecha, blast de ki), entao luta a distancia tambem arma.
	 * Custo: um {@code contains()} por evento de dano do servidor inteiro.
	 */
	@SubscribeEvent
	public static void onHurt(LivingHurtEvent event) {
		LivingEntity le = event.getEntity();
		if (le.level().isClientSide()) {
			return;
		}
		CompoundTag pd = le.getPersistentData();
		if (!pd.contains(FIGHT_T_TAG) || pd.getBoolean(FIGHT_ENGAGED_TAG)) {
			return;
		}
		if (event.getSource().getEntity() instanceof ServerPlayer) {
			pd.putBoolean(FIGHT_ENGAGED_TAG, true);
			pd.putLong(FIGHT_START_TAG, le.level().getGameTime());
		}
	}

	@SubscribeEvent
	public static void onServerStopping(ServerStoppingEvent event) {
		ACTIVE.clear();
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		// A ordem dos guards e o custo do caso comum: sem luta de quest viva, isto e UM isEmpty()
		// por tick e mais nada.
		if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) {
			return;
		}
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null || server.getTickCount() % SCAN_EVERY_TICKS != 0) {
			return;
		}

		Iterator<Map.Entry<UUID, Fight>> it = ACTIVE.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Fight> entry = it.next();
			Fight f = entry.getValue();
			ServerLevel level = server.getLevel(f.dim);
			if (level == null) {
				it.remove();
				continue;
			}
			Entity raw = level.getEntity(entry.getKey());
			if (raw == null) {
				// Chunk descarregado (ou mob removido com o chunk). Descarta rapido: se o chunk
				// voltar, o EntityJoinLevelEvent recria a entrada com o relogio do NBT.
				if (++f.misses >= EVICT_AFTER_MISSES) {
					it.remove();
				}
				continue;
			}
			f.misses = 0;
			if (!(raw instanceof LivingEntity le) || !le.isAlive()) {
				it.remove();
				continue;
			}
			// Chunk carregado mas sem entity ticking (borda): o mob esta CONGELADO. Curar ou
			// avisar aqui seria mexer numa luta que nao esta acontecendo. A entrada fica; o
			// fluxo volta sozinho quando o chunk tickar de novo.
			if (!level.isPositionEntityTicking(raw.blockPosition())) {
				continue;
			}

			CompoundTag pd = le.getPersistentData();
			// Ninguem bateu ainda: o relogio nem esta correndo (o onHurt e quem da a partida).
			if (!pd.getBoolean(FIGHT_ENGAGED_TAG)) {
				continue;
			}
			int t = pd.getInt(FIGHT_T_TAG);
			if (t <= 0) {
				it.remove();
				continue;
			}
			long tTicks = t * 20L;
			long tier1At = tTicks * 2L; // 2 x T (era 3,5x; apertado em 2026-08-10 a pedido do dono)
			long tier2At = tTicks * 4L; // 4 x T (era 5x)
			long now = level.getGameTime();
			long elapsed = now - pd.getLong(FIGHT_START_TAG);
			if (elapsed < tier1At) {
				continue; // dentro do tempo: o sistema nao existe pro jogador
			}

			// Vida cheia com degrau ativo = luta reiniciada (ver javadoc da classe). Desarma tudo
			// e volta a esperar o proximo hit de player.
			if (le.getHealth() >= le.getMaxHealth() - 0.01f) {
				if (pd.getBoolean(ARMED_TIER1_TAG)) {
					LogUtil.info(Env.SERVER, "[OvertimeRegen] {} ({}) voltou pra vida cheia; "
									+ "relogio desarmado ate o proximo hit de player.",
							le.getName().getString(), pd.getString(QuestService.QUEST_KEY_TAG));
				}
				pd.putLong(FIGHT_START_TAG, now);
				pd.remove(FIGHT_ENGAGED_TAG);
				pd.remove(ARMED_TIER1_TAG);
				pd.remove(ARMED_TIER2_TAG);
				pd.remove(WARNED_TIER1_TAG);
				pd.remove(WARNED_TIER2_TAG);
				continue;
			}

			// ---- degrau 1 (2xT): regen continua de 66% do DPS de referencia ----
			if (!pd.getBoolean(ARMED_TIER1_TAG)) {
				pd.putBoolean(ARMED_TIER1_TAG, true);
				LogUtil.info(Env.SERVER, "[OvertimeRegen] degrau 1 armado: {} ({}), T={}s, "
								+ "luta ja dura {}s, regen de 66% do DPS de referencia.",
						le.getName().getString(), pd.getString(QuestService.QUEST_KEY_TAG),
						t, elapsed / 20L);
			}
			// Armar e avisar sao coisas separadas: com o dono offline o aviso fica pendente e e
			// reentregue na primeira varredura em que ele estiver online, sem atrasar a regen.
			if (!pd.getBoolean(WARNED_TIER1_TAG) && avisar(server, le, pd, Component.empty()
					.append(Component.literal("You took too long! "))
					.append(le.getDisplayName().copy())
					.append(Component.literal(" is now regenerating health."))
					.withStyle(ChatFormatting.YELLOW))) {
				pd.putBoolean(WARNED_TIER1_TAG, true);
			}

			// ---- degrau 2 (4xT): a taxa TOTAL sobe pra 100% do DPS de referencia ----
			boolean tier2 = elapsed >= tier2At;
			if (tier2) {
				if (!pd.getBoolean(ARMED_TIER2_TAG)) {
					pd.putBoolean(ARMED_TIER2_TAG, true);
					LogUtil.info(Env.SERVER, "[OvertimeRegen] degrau 2 armado: {} ({}), T={}s, "
									+ "luta ja dura {}s, regen total de 100% do DPS de referencia.",
							le.getName().getString(), pd.getString(QuestService.QUEST_KEY_TAG),
							t, elapsed / 20L);
				}
				if (!pd.getBoolean(WARNED_TIER2_TAG) && avisar(server, le, pd, Component.empty()
						.append(le.getDisplayName().copy())
						.append(Component.literal(" is regenerating even faster now!"))
						.withStyle(ChatFormatting.RED))) {
					pd.putBoolean(WARNED_TIER2_TAG, true);
				}
			}

			// UM pulso por varredura (1s), com o valor por segundo da taxa do momento. O DPS de
			// referencia e maxHP/T por segundo, entao a cura por segundo e maxHP * fracao / T.
			double fracao = tier2 ? TIER1_DPS_FRACTION + TIER2_EXTRA_DPS_FRACTION : TIER1_DPS_FRACTION;
			le.setHealth(le.getHealth() + (float) (le.getMaxHealth() * fracao / t));
		}
	}

	/**
	 * Avisa o dono da quest e a party dele. {@code false} = dono offline neste servidor; o
	 * chamador NAO marca o aviso como entregue e tenta de novo na proxima varredura.
	 */
	private static boolean avisar(MinecraftServer server, LivingEntity mob, CompoundTag pd, Component msg) {
		ServerPlayer owner = null;
		try {
			owner = server.getPlayerList().getPlayer(UUID.fromString(pd.getString(QuestService.QUEST_OWNER_TAG)));
		} catch (IllegalArgumentException ignored) {
			// tag ausente ou corrompida: sem dono pra avisar, o log da classe ja cobre o debug
		}
		if (owner == null) {
			return false;
		}
		Set<ServerPlayer> destinatarios = new HashSet<>(PartyManager.getAllPartyMembers(owner));
		destinatarios.add(owner);
		for (ServerPlayer p : destinatarios) {
			p.sendSystemMessage(msg);
		}
		return true;
	}
}
