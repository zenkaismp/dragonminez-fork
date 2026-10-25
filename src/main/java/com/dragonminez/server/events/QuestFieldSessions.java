package com.dragonminez.server.events;

import com.dragonminez.Reference;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.quest.Difficulty;
import com.dragonminez.common.quest.PartyManager;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.quest.Quest;
import com.dragonminez.common.quest.QuestMobGuard;
import com.dragonminez.common.quest.QuestOvertimeRegen;
import com.dragonminez.common.quest.QuestParty;
import com.dragonminez.common.quest.QuestService;
import com.dragonminez.common.quest.objectives.SurviveWavesObjective;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side runtime state for "field" objectives that own live entities: SURVIVE_WAVES. Sessions are keyed by
 * session owner + quest key + objective index, are re-created from persisted objective progress after relog (waves
 * resume at the current wave), and are torn down — despawning their entities — on completion, failure, restart, or
 * logout.
 *
 * <p>Copiado da v2.2 (d824a29c) so com a parte das ondas (o ESCORT nao veio). O que muda no fork:</p>
 * <ul>
 *   <li>a IA de reserva (objetivo sem {@code AITier}) e a do {@link Difficulty} do fork, que nao tem o
 *       {@code AiTierResolver};</li>
 *   <li>o guarda da onda leva as marcas que o {@code QuestService} do fork poe no mob de MATAR: vida e dano com a
 *       escala de grupo ({@code Quest.getScaledWave*}, pelo elenco), o carimbo do {@link QuestMobGuard}, a saga e o
 *       relogio do anti-covarde ({@link QuestOvertimeRegen}), pra o guarda ser o mesmo de quando vinha de um MATAR;</li>
 *   <li>o fork nao tem o {@code failQuestFor} da v2.2: a sessao cai no {@link DMZEvent.QuestFailEvent} (morte da party,
 *       {@code /dmzquest reset} e {@code fail}) e no fim da quest pelo {@code QuestEvents}.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class QuestFieldSessions {

	private static final String SESSION_TAG = "dmz_quest_session";
	private static final double WAVE_SPAWN_RADIUS = 6.0;
	private static final double WAVE_LEASH_DISTANCE_SQR = 64.0 * 64.0;

	private static final Map<UUID, Map<String, WaveSession>> WAVE_SESSIONS = new ConcurrentHashMap<>();

	private QuestFieldSessions() {
	}

	// ====================================================================================
	// Waves
	// ====================================================================================

	static void tickWaves(ServerPlayer player, PlayerQuestData pqd, String questKey, Quest quest,
						  int objectiveIndex, SurviveWavesObjective objective) {
		if (!isSessionOwner(player, questKey, quest, objectiveIndex)) return;

		String sessionKey = sessionKey(questKey, objectiveIndex);
		int progress = pqd.getObjectiveProgress(questKey, objectiveIndex);
		int required = quest.getObjectiveRequired(pqd, questKey, objectiveIndex);
		if (progress >= required) {
			clearWaveSession(player, sessionKey);
			return;
		}

		Map<String, WaveSession> sessions = WAVE_SESSIONS.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>());
		long gameTime = player.serverLevel().getGameTime();
		WaveSession session = sessions.get(sessionKey);
		if (session == null) {
			session = new WaveSession(gameTime + objective.getWaveDelaySeconds() * 20L);
			sessions.put(sessionKey, session);
			broadcast(player, questKey, Component.translatable("message.dragonminez.quest.wave_incoming",
					progress + 1, required));
			return;
		}

		if (session.waveActive || session.lost) {
			if (session.lost || session.abandonedBy(player)) {
				despawnSessionMobs(player.getServer().getLevel(session.dimension), session.aliveMobs);
				session.reset(gameTime + objective.getWaveDelaySeconds() * 20L);
				broadcast(player, questKey, Component.translatable("message.dragonminez.quest.wave_reset",
						progress + 1, required));
				return;
			}

			pruneDeadMobs(player.serverLevel(), session);
			if (session.aliveMobs.isEmpty()) {
				creditSessionProgress(player, pqd, questKey, quest, objectiveIndex);
				if (progress + 1 < required) {
					session.reset(gameTime + objective.getWaveDelaySeconds() * 20L);
					broadcast(player, questKey, Component.translatable("message.dragonminez.quest.wave_cleared",
							progress + 1, required));
				} else {
					clearWaveSession(player, sessionKey);
				}
			}
			return;
		}

		if (gameTime >= session.nextWaveGameTime) {
			if (spawnWave(player, pqd, questKey, quest, objectiveIndex, objective, session)) {
				broadcast(player, questKey, Component.translatable("message.dragonminez.quest.wave_started",
						progress + 1, required));
			} else {
				session.nextWaveGameTime = gameTime + objective.getWaveDelaySeconds() * 20L;
			}
		}
	}

	private static boolean spawnWave(ServerPlayer player, PlayerQuestData pqd, String questKey, Quest quest,
									 int objectiveIndex, SurviveWavesObjective objective, WaveSession session) {
		EntityType<?> entityType = objective.resolveEntityType();
		if (entityType == null) return false;
		// Fork: a dificuldade da quest (a do re-summon do QuestService) e a escala de grupo do MATAR, pelo elenco.
		Difficulty waveDifficulty = pqd.getQuestDifficulty(questKey);
		if (waveDifficulty == null) waveDifficulty = Difficulty.NORMAL;
		int partySize = PartyManager.getPartyRosterSize(player);
		double health = quest.getScaledWaveHealth(objective, partySize);
		double melee = quest.getScaledWaveMeleeDamage(objective, partySize);
		double ki = quest.getScaledWaveKiDamage(objective, partySize);
		String partyStamp = PartyManager.partyRosterCsv(player);
		QuestService.ResolvedQuest resolved = QuestService.resolveQuest(questKey);

		session.dimension = player.level().dimension();
		session.origin = player.blockPosition();
		for (int i = 0; i < objective.getMobsPerWave(); i++) {
			Entity entity = entityType.create(player.level());
			if (entity == null) continue;

			double offsetX = (Math.random() - 0.5) * 2 * WAVE_SPAWN_RADIUS;
			double offsetZ = (Math.random() - 0.5) * 2 * WAVE_SPAWN_RADIUS;
			entity.setPos(player.getX() + offsetX, player.getY(), player.getZ() + offsetZ);

			CompoundTag data = entity.getPersistentData();
			data.putString(QuestService.QUEST_KEY_TAG, questKey);
			data.putInt(QuestService.QUEST_OBJECTIVE_INDEX_TAG, objectiveIndex);
			data.putString(QuestService.QUEST_OWNER_TAG, player.getStringUUID());
			data.putString(SESSION_TAG, sessionKey(questKey, objectiveIndex));
			if (health > 0) data.putDouble("dmz_quest_hp", health);
			if (melee > 0) data.putDouble("dmz_quest_melee", melee);
			if (ki > 0) data.putDouble("dmz_quest_ki", ki);
			if (objective.getTextureVariant() >= 0) data.putInt("dmz_quest_texture_variant", objective.getTextureVariant());
			int waveTier = objective.getAiTier() > 0 ? objective.getAiTier() : waveDifficulty.aiTierId();
			data.putInt("dmz_quest_ai_tier", waveTier);
			if (!objective.isCanTransform()) data.putBoolean("dmz_quest_no_transform", true);
			// Fork: as marcas do mob de MATAR (QuestService.spawnKillObjectives).
			data.putString(QuestMobGuard.PARTY_STAMP_TAG, partyStamp);
			if (resolved != null && resolved.saga() != null) {
				data.putString(QuestService.SAGA_ID_TAG, resolved.saga().getId());
			}
			if (quest.getFightDurationSeconds() > 0) {
				int scaledT = (int) Math.max(1, Math.round(quest.getFightDurationSeconds() * waveDifficulty.hpMultiplier()));
				data.putInt(QuestOvertimeRegen.FIGHT_T_TAG, scaledT);
				data.putLong(QuestOvertimeRegen.FIGHT_START_TAG, player.serverLevel().getGameTime());
			}

			if (entity instanceof Mob mob) {
				mob.setTarget(player);
				mob.setPersistenceRequired();
			}

			session.aliveMobs.add(entity.getUUID());
			if (!player.serverLevel().addFreshEntity(entity)) session.aliveMobs.remove(entity.getUUID());
		}
		session.waveActive = !session.aliveMobs.isEmpty();
		return session.waveActive;
	}

	private static void pruneDeadMobs(ServerLevel level, WaveSession session) {
		Iterator<UUID> iterator = session.aliveMobs.iterator();
		while (iterator.hasNext()) {
			Entity entity = level.getEntity(iterator.next());
			if (entity != null && !entity.isAlive()) iterator.remove();
		}
	}

	// ====================================================================================
	// Lifecycle hooks
	// ====================================================================================

	/** Called from LivingDeathEvent so a wave mob leaves its session the moment it dies. */
	static void onEntityDeath(LivingEntity entity) {
		CompoundTag tags = entity.getPersistentData();
		if (!tags.contains(SESSION_TAG)) return;
		String sessionKey = tags.getString(SESSION_TAG);
		UUID owner = parseOwner(tags);
		if (owner == null) return;
		Map<String, WaveSession> waves = WAVE_SESSIONS.get(owner);
		WaveSession session = waves != null ? waves.get(sessionKey) : null;
		if (session != null) session.aliveMobs.remove(entity.getUUID());
	}

	@SubscribeEvent
	public static void onEntityLeave(EntityLeaveLevelEvent event) {
		Entity entity = event.getEntity();
		if (entity.level().isClientSide() || !entity.getPersistentData().contains(SESSION_TAG)) return;
		Entity.RemovalReason reason = entity.getRemovalReason();
		if (reason == null || reason == Entity.RemovalReason.KILLED || !reason.shouldDestroy() && reason != Entity.RemovalReason.CHANGED_DIMENSION) return;
		UUID owner = parseOwner(entity.getPersistentData());
		if (owner == null) return;
		Map<String, WaveSession> waves = WAVE_SESSIONS.get(owner);
		WaveSession session = waves != null ? waves.get(entity.getPersistentData().getString(SESSION_TAG)) : null;
		if (session != null && session.aliveMobs.remove(entity.getUUID())) session.lost = true;
	}

	@SubscribeEvent
	public static void onEntityJoin(EntityJoinLevelEvent event) {
		Entity entity = event.getEntity();
		if (event.getLevel().isClientSide() || !event.loadedFromDisk()) return;
		CompoundTag tags = entity.getPersistentData();
		if (!tags.contains(SESSION_TAG)) return;
		UUID owner = parseOwner(tags);
		String sessionKey = tags.getString(SESSION_TAG);
		boolean tracked = false;
		if (owner != null) {
			Map<String, WaveSession> sessions = WAVE_SESSIONS.get(owner);
			WaveSession session = sessions != null ? sessions.get(sessionKey) : null;
			tracked = session != null && session.aliveMobs.contains(entity.getUUID());
		}
		if (!tracked) event.setCanceled(true);
	}

	/**
	 * Fork: a quest falhou (morte da party, {@code /dmzquest reset} ou {@code fail}, que e o caminho do
	 * {@code /cancelquest} do ZenkaiExtra). A v2.2 chama o {@link #clearQuest} do {@code failQuestFor}; o fork posta
	 * este evento antes de marcar a falha, entao a limpeza roda aqui, no fim da fila e so se ninguem cancelou.
	 */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onQuestFail(DMZEvent.QuestFailEvent event) {
		Set<ServerPlayer> members = new HashSet<>(event.getPartyMembers());
		if (event.getPlayer() != null) members.add(event.getPlayer());
		for (ServerPlayer member : members) {
			clearQuest(member, event.getQuestKey());
		}
	}

	/** Tears down every session (and its live entities) for one quest of a player. */
	public static void clearQuest(ServerPlayer player, String questKey) {
		Map<String, WaveSession> waves = WAVE_SESSIONS.get(player.getUUID());
		if (waves != null) {
			waves.keySet().removeIf(key -> {
				if (!key.startsWith(questKey + "#")) return false;
				WaveSession session = waves.get(key);
				despawnSessionMobs(player.getServer().getLevel(session.dimension), session.aliveMobs);
				return true;
			});
		}
	}

	/** Full teardown on logout; sessions rebuild from persisted progress on the next tick after relog. */
	public static void clearAll(ServerPlayer player) {
		Map<String, WaveSession> waves = WAVE_SESSIONS.remove(player.getUUID());
		if (waves != null) {
			waves.values().forEach(session ->
					despawnSessionMobs(player.getServer().getLevel(session.dimension), session.aliveMobs));
		}
	}

	private static void despawnSessionMobs(ServerLevel level, Set<UUID> mobIds) {
		if (level != null) {
			for (UUID mobId : new ArrayList<>(mobIds)) {
				Entity entity = level.getEntity(mobId);
				if (entity != null) entity.discard();
			}
		}
		mobIds.clear();
	}

	private static void creditSessionProgress(ServerPlayer owner, PlayerQuestData ownerData, String questKey, Quest quest,
											  int objectiveIndex) {
		for (ServerPlayer participant : QuestParty.participants(owner, questKey)) {
			PlayerQuestData data = participant == owner ? ownerData : QuestParty.questData(participant);
			if (data == null) continue;
			int progress = data.getObjectiveProgress(questKey, objectiveIndex);
			if (progress >= quest.getObjectiveRequired(data, questKey, objectiveIndex)) continue;
			if (!quest.isParallelObjectives() && !QuestEvents.isFirstUncompleted(data, questKey, quest, objectiveIndex)) continue;
			QuestEvents.updateProgress(participant, data, questKey, quest, objectiveIndex, progress + 1);
			QuestEvents.checkAndComplete(participant, data, questKey, quest);
		}
	}

	private static void clearWaveSession(ServerPlayer player, String sessionKey) {
		Map<String, WaveSession> sessions = WAVE_SESSIONS.get(player.getUUID());
		if (sessions == null) return;
		WaveSession session = sessions.remove(sessionKey);
		if (session != null) despawnSessionMobs(player.getServer().getLevel(session.dimension), session.aliveMobs);
	}

	private static boolean isSessionOwner(ServerPlayer player, String questKey, Quest quest, int objectiveIndex) {
		String sessionKey = sessionKey(questKey, objectiveIndex);
		ServerPlayer firstHolder = null;
		ServerPlayer firstNeeding = null;
		for (ServerPlayer member : PartyManager.getAllPartyMembers(player)) {
			PlayerQuestData data = QuestParty.questData(member);
			if (data == null || !data.isQuestAccepted(questKey)) continue;
			if (data.getObjectiveProgress(questKey, objectiveIndex) >= quest.getObjectiveRequired(data, questKey, objectiveIndex)) continue;
			if (member != player && !QuestParty.inShareRange(player, member)) continue;
			if (firstNeeding == null) firstNeeding = member;
			if (firstHolder == null && holdsSession(member.getUUID(), sessionKey)) firstHolder = member;
		}
		ServerPlayer owner = firstHolder != null ? firstHolder : firstNeeding;
		if (owner == null || owner == player) return true;
		clearWaveSession(player, sessionKey);
		return false;
	}

	private static boolean holdsSession(UUID playerId, String sessionKey) {
		Map<String, WaveSession> sessions = WAVE_SESSIONS.get(playerId);
		return sessions != null && sessions.containsKey(sessionKey);
	}

	private static void broadcast(ServerPlayer owner, String questKey, Component message) {
		for (ServerPlayer member : QuestParty.participants(owner, questKey)) {
			member.displayClientMessage(message, true);
		}
	}

	private static UUID parseOwner(CompoundTag tags) {
		try {
			return UUID.fromString(tags.getString(QuestService.QUEST_OWNER_TAG));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static String sessionKey(String questKey, int objectiveIndex) {
		return questKey + "#" + objectiveIndex;
	}

	private static class WaveSession {
		final Set<UUID> aliveMobs = new HashSet<>();
		long nextWaveGameTime;
		ResourceKey<Level> dimension = Level.OVERWORLD;
		BlockPos origin = BlockPos.ZERO;
		boolean lost = false;
		boolean waveActive = false;

		WaveSession(long nextWaveGameTime) {
			this.nextWaveGameTime = nextWaveGameTime;
		}

		void reset(long nextWaveGameTime) {
			this.aliveMobs.clear();
			this.lost = false;
			this.waveActive = false;
			this.nextWaveGameTime = nextWaveGameTime;
		}

		boolean abandonedBy(ServerPlayer owner) {
			return owner.level().dimension() != dimension
					|| owner.blockPosition().distSqr(origin) > WAVE_LEASH_DISTANCE_SQR;
		}
	}
}
