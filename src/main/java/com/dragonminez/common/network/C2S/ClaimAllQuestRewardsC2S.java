package com.dragonminez.common.network.C2S;

import com.dragonminez.common.quest.QuestService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.function.Supplier;

public class ClaimAllQuestRewardsC2S {

	public ClaimAllQuestRewardsC2S() {
	}

	public ClaimAllQuestRewardsC2S(FriendlyByteBuf buffer) {
	}

	public void encode(FriendlyByteBuf buffer) {
	}

	public void handle(CustomPayloadEvent.Context contextSupplier) {
		CustomPayloadEvent.Context context = contextSupplier;
		context.enqueueWork(() -> {
			ServerPlayer player = context.getSender();
			if (player != null) {
				QuestService.claimAllRewards(player);
			}
		});
		context.setPacketHandled(true);
	}
}
