package com.dragonminez.common.network.C2S;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.function.Supplier;

public class DeleteMasterC2S {
	private final String masterId;

	public DeleteMasterC2S(String masterId) {
		this.masterId = masterId;
	}

	public DeleteMasterC2S(FriendlyByteBuf buf) {
		this.masterId = buf.readUtf(256);
	}

	public void toBytes(FriendlyByteBuf buf) {
		buf.writeUtf(this.masterId);
	}

	public void handle(CustomPayloadEvent.Context ctx) {
		ctx.enqueueWork(() -> {
			ServerPlayer player = ctx.getSender();
			if (player == null) return;

			StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
				data.getCharacter().removeInteractedMaster(masterId);
				NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
			});
		});
		ctx.setPacketHandled(true);
	}
}
