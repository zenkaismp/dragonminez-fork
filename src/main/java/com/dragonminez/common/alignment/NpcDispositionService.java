package com.dragonminez.common.alignment;

import com.dragonminez.common.combat.logic.player.TargetHelper;
import com.dragonminez.common.init.entities.MastersEntity;
import com.dragonminez.common.init.entities.questnpc.QuestNPCEntity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.server.world.npc.NPCPlacementManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

public final class NpcDispositionService {
	public static final String NPC_ALIGNMENT_TAG = "DmzNpcAlignment";
	public static final String NPC_RELATION_OVERRIDE_TAG = "DmzNpcRelationOverride";

	private static final Set<String> GOOD_ALIGNED_MASTERS = Set.of("goku", "gohan", "kingkai", "oldkai", "roshi", "krillin");
	private static final Set<String> EVIL_ALIGNED_MASTERS = Set.of("cell", "frieza");
	private static final int GOOD_ALIGNMENT_MIN = 61;
	private static final int EVIL_ALIGNMENT_MAX = 40;

	/**
	 * Zenkai: servicos de master DESLIGADOS no balcao do NPC.
	 *
	 * <p>Os quatro entregavam coisa permanente e forte de graca, e o servidor vai colocar todos
	 * esses NPC numa warp — o que era "achar o Old Kai no Mundo Sagrado" viraria uma fila no
	 * spawn. A marca Majin passa a sair de item ({@code zenkaismp:majin_mark}), o resto morre.</p>
	 *
	 * <p><b>Isto NAO fecha o dialogo.</b> A trava mora em {@link #getServiceBlocker}, que so e
	 * consultada pelo pacote de acao (os botoes). O caminho de conversa e o
	 * {@link #getDialogueBlocker}, intocado — senao o Guru levaria junto as 4 quests de FALAR e
	 * as 8 que ele entrega, e o Old Kai levaria a sidequest old_kai_ritual.</p>
	 *
	 * <p><b>Antes de tirar o oldkai daqui, leia:</b> a quest 24 da saga Buu foi reescrita
	 * (objetivos viraram DIMENSAO + FALAR) justamente porque os objetivos originais eram TER a
	 * z_sword e TER a skill {@code ultimate}, e o Old Kai era a unica fonte das duas no jogo
	 * inteiro. Reativar o servico sem reverter o yml devolve a espada gratis pra qualquer um —
	 * o give dela estava FORA do {@code if} de alinhamento no {@code handleOldKai}.</p>
	 */
	private static final Set<String> DISABLED_SERVICES = Set.of("babidi", "gero", "oldkai", "guru");

	/** True se o balcao deste master esta desligado. O client usa pra nem desenhar o botao. */
	public static boolean isServiceDisabled(String masterName) {
		return DISABLED_SERVICES.contains(normalizeNpcKey(masterName));
	}

	private NpcDispositionService() {}

	public static boolean isInteractiveNpc(Entity entity) {
		if (entity instanceof QuestNPCEntity) {
			return true;
		}
		return entity instanceof MastersEntity master && master.getMasterName() != null && !master.getMasterName().isBlank();
	}

	public static Optional<TargetHelper.Relation> getInteractiveRelation(Player player, Entity target) {
		if (!isInteractiveNpc(target)) {
			return Optional.empty();
		}
		return Optional.of(getRelation(player, target));
	}

	public static TargetHelper.Relation getRelation(Player player, Entity target) {
		if (player == null || target == null) {
			return TargetHelper.Relation.HOSTILE;
		}

		String npcKey = resolveNpcKey(target);
		String hostilityKey = resolveHostilityKey(target);
		StatsData stats = player instanceof ServerPlayer serverPlayer
				? StatsProvider.get(StatsCapability.INSTANCE, serverPlayer).orElse(null)
				: null;

		if (stats != null && stats.getPlayerQuestData().isNpcHostile(hostilityKey)) {
			return TargetHelper.Relation.HOSTILE;
		}

		TargetHelper.Relation override = readRelationOverride(target.getPersistentData());
		if (override != null) {
			return override;
		}

		int playerAlignment = stats != null ? stats.getResources().getAlignment() : 100;
		NpcAlignmentRule rule = NpcAlignmentRules.get(npcKey);
		if (rule != null) {
			if (rule.isHostileFor(playerAlignment)) {
				return TargetHelper.Relation.HOSTILE;
			}
		}

		Integer npcAlignment = readNpcAlignment(target.getPersistentData());
		if (npcAlignment != null) {
			return relationFromNpcAlignment(npcAlignment);
		}

		if (rule != null) {
			return rule.defaultRelation();
		}

		return TargetHelper.Relation.NEUTRAL;
	}

	@Nullable
	public static Component getDialogueBlocker(ServerPlayer player, Entity npc) {
		if (player == null || npc == null) {
			return Component.translatable("message.dragonminez.npc.unavailable");
		}

		StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
		if (data == null) {
			return Component.translatable("message.dragonminez.npc.unavailable");
		}

		if (npc instanceof MastersEntity master && master.getMasterName() != null && !master.getMasterName().isBlank()) {
			return masterAlignmentBlocker(data, master.getMasterName());
		}

		TargetHelper.Relation relation = getRelation(player, npc);
		if (relation == TargetHelper.Relation.HOSTILE) {
			return Component.translatable("message.dragonminez.npc.hostile");
		}
		return getAlignmentBlocker(data, resolveNpcKey(npc));
	}

	@Nullable
	public static Component getServiceBlocker(ServerPlayer player, String npcId) {
		if (player == null || npcId == null || npcId.isBlank()) {
			return Component.translatable("message.dragonminez.npc.unavailable");
		}

		StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
		if (data == null) {
			return Component.translatable("message.dragonminez.npc.unavailable");
		}

		// Antes do alinhamento: o servico desligado nao depende de quem o jogador e. Fica aqui, e
		// nao no handle* de cada master, porque este e o unico ponto por onde o pacote passa —
		// client hackeado mandando o NPCActionC2S na mao bate aqui do mesmo jeito.
		String disabled = disabledServiceBlocker(npcId);
		if (disabled != null) {
			return Component.literal(disabled);
		}

		return masterAlignmentBlocker(data, npcId);
	}

	@Nullable
	private static String disabledServiceBlocker(String masterName) {
		String key = normalizeNpcKey(masterName);
		if (!DISABLED_SERVICES.contains(key)) {
			return null;
		}
		// Texto cru e nao translatable: sao chaves que so existem no Zenkai, e um en_us do fork
		// some no proximo merge do upstream. Ingles porque e texto de jogador.
		return switch (key) {
			case "guru" -> "§eYour potential is unlocked through the saga, not here.";
			case "oldkai" -> "§eThe Elder Kai has nothing to hand out.";
			case "gero" -> "§eDr. Gero is not performing conversions.";
			case "babidi" -> "§eBabidi will not mark you. The mark is found elsewhere.";
			default -> "§eThis service is not available.";
		};
	}

	public static void markHostile(ServerPlayer player, Entity npc) {
		if (player == null || npc == null || !isInteractiveNpc(npc)) {
			return;
		}
		if (npc instanceof MastersEntity) {
			return;
		}
		StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> data.getPlayerQuestData().markNpcHostile(resolveHostilityKey(npc)));
	}

	@Nullable
	private static Component masterAlignmentBlocker(StatsData data, String masterName) {
		String key = normalizeNpcKey(masterName);
		int alignment = data.getResources().getAlignment();
		if (GOOD_ALIGNED_MASTERS.contains(key) && alignment < GOOD_ALIGNMENT_MIN) {
			return Component.translatable("message.dragonminez.npc.alignment_too_low", GOOD_ALIGNMENT_MIN);
		}
		if (EVIL_ALIGNED_MASTERS.contains(key) && alignment > EVIL_ALIGNMENT_MAX) {
			return Component.translatable("message.dragonminez.npc.alignment_too_high", EVIL_ALIGNMENT_MAX);
		}
		return null;
	}

	public static String resolveNpcKey(Entity entity) {
		if (entity instanceof QuestNPCEntity questNPC) {
			return normalizeNpcKey(questNPC.getNpcId());
		}
		if (entity instanceof MastersEntity master && master.getMasterName() != null && !master.getMasterName().isBlank()) {
			return normalizeNpcKey(master.getMasterName());
		}
		CompoundTag data = entity.getPersistentData();
		if (data.contains(NPCPlacementManager.PLACEMENT_TAG, Tag.TAG_STRING)) {
			return normalizeNpcKey(data.getString(NPCPlacementManager.PLACEMENT_TAG));
		}
		return entity.getStringUUID();
	}

	public static String resolveHostilityKey(Entity entity) {
		CompoundTag data = entity.getPersistentData();
		if (data.contains(NPCPlacementManager.PLACEMENT_TAG, Tag.TAG_STRING)) {
			return normalizeNpcKey(data.getString(NPCPlacementManager.PLACEMENT_TAG));
		}
		return resolveNpcKey(entity);
	}

	private static Component getAlignmentBlocker(StatsData data, String npcKey) {
		NpcAlignmentRule rule = NpcAlignmentRules.get(npcKey);
		if (rule == null) {
			return null;
		}

		int alignment = data.getResources().getAlignment();
		if (rule.allowsInteraction(alignment)) {
			return null;
		}

		Integer min = rule.minInteractionAlignment();
		Integer max = rule.maxInteractionAlignment();
		if (min != null && alignment < min) {
			return Component.translatable("message.dragonminez.npc.alignment_too_low", min);
		}
		if (max != null && alignment > max) {
			return Component.translatable("message.dragonminez.npc.alignment_too_high", max);
		}
		return Component.translatable("message.dragonminez.npc.unavailable");
	}

	private static TargetHelper.Relation relationFromNpcAlignment(int npcAlignment) {
		return switch (AlignmentBand.fromValue(npcAlignment)) {
			case GOOD -> TargetHelper.Relation.FRIENDLY;
			case NEUTRAL -> TargetHelper.Relation.NEUTRAL;
			case EVIL -> TargetHelper.Relation.HOSTILE;
		};
	}

	@Nullable
	private static Integer readNpcAlignment(CompoundTag tag) {
		if (tag.contains(NPC_ALIGNMENT_TAG, Tag.TAG_INT)) {
			return Math.max(0, Math.min(100, tag.getInt(NPC_ALIGNMENT_TAG)));
		}
		return null;
	}

	@Nullable
	private static TargetHelper.Relation readRelationOverride(CompoundTag tag) {
		if (!tag.contains(NPC_RELATION_OVERRIDE_TAG, Tag.TAG_STRING)) {
			return null;
		}
		try {
			return TargetHelper.Relation.valueOf(tag.getString(NPC_RELATION_OVERRIDE_TAG).trim().toUpperCase());
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static String normalizeNpcKey(String npcId) {
		String normalized = npcId == null ? "" : npcId.trim().toLowerCase();
		if (normalized.contains(":")) {
			normalized = normalized.substring(normalized.indexOf(':') + 1);
		}
		return normalized;
	}
}
