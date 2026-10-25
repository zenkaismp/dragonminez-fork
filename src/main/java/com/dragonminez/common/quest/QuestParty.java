package com.dragonminez.common.quest;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.server.storage.StorageManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Quem da party divide uma quest e a que distancia (v2.2, d824a29c). Veio so o que as ondas da v2.2
 * ({@code QuestFieldSessions}) usam; o resto do fork continua com a party do {@link PartyManager} sem raio.
 * Na 1.20.2 o {@code StorageManager.isLoadPending} do fork recebe o UUID.
 */
public final class QuestParty {

	public static final double SHARE_RANGE = 128.0;
	private static final double SHARE_RANGE_SQR = SHARE_RANGE * SHARE_RANGE;

	private QuestParty() {
	}

	public static boolean inShareRange(ServerPlayer a, ServerPlayer b) {
		if (a == null || b == null) return false;
		if (a == b) return true;
		return a.level() == b.level() && a.distanceToSqr(b) <= SHARE_RANGE_SQR;
	}

	public static PlayerQuestData questData(ServerPlayer player) {
		if (player == null || StorageManager.isLoadPending(player.getUUID())) return null;
		return StatsProvider.get(StatsCapability.INSTANCE, player).resolve()
				.map(StatsData::getPlayerQuestData).orElse(null);
	}

	public static boolean isActive(ServerPlayer player, String questKey) {
		PlayerQuestData pqd = questData(player);
		return pqd != null && pqd.isQuestAccepted(questKey);
	}

	public static List<ServerPlayer> nearbyMembers(ServerPlayer actor) {
		List<ServerPlayer> nearby = new ArrayList<>();
		for (ServerPlayer member : PartyManager.getAllPartyMembers(actor)) {
			if (member == actor || inShareRange(actor, member)) nearby.add(member);
		}
		if (!nearby.contains(actor)) nearby.add(0, actor);
		return nearby;
	}

	public static List<ServerPlayer> participants(ServerPlayer actor, String questKey) {
		List<ServerPlayer> participants = new ArrayList<>();
		for (ServerPlayer member : nearbyMembers(actor)) {
			if (isActive(member, questKey)) participants.add(member);
		}
		return participants;
	}

	public static List<ServerPlayer> allParticipants(ServerPlayer player, String questKey) {
		List<ServerPlayer> participants = new ArrayList<>();
		for (ServerPlayer member : PartyManager.getAllPartyMembers(player)) {
			if (isActive(member, questKey)) participants.add(member);
		}
		if (!participants.contains(player) && isActive(player, questKey)) participants.add(0, player);
		return participants;
	}
}
