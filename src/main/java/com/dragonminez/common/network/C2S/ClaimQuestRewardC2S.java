package com.dragonminez.common.network.C2S;

import com.dragonminez.common.quest.QuestService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.function.Supplier;

public class ClaimQuestRewardC2S {
	private final String questId;

	public ClaimQuestRewardC2S(String questId) {
		this.questId = questId;
	}

	public ClaimQuestRewardC2S(FriendlyByteBuf buffer) {
		this.questId = buffer.readUtf();
	}

	public void encode(FriendlyByteBuf buffer) {
		buffer.writeUtf(questId);
	}

	public void handle(CustomPayloadEvent.Context contextSupplier) {
		CustomPayloadEvent.Context context = contextSupplier;
		context.enqueueWork(() -> {
			ServerPlayer player = context.getSender();
			if (player != null) {
				QuestService.claimRewards(player, questId);
			}
		});
		context.setPacketHandled(true);
	}
}
