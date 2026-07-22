package com.dragonminez.common.network.C2S;

import com.dragonminez.server.events.players.combat.TaiyokenHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.function.Supplier;

public class TaiyokenCastC2S {

	public TaiyokenCastC2S() {
	}

	public TaiyokenCastC2S(FriendlyByteBuf buf) {
	}

	public void toBytes(FriendlyByteBuf buf) {
	}

	public void handle(CustomPayloadEvent.Context ctx) {
		ctx.enqueueWork(() -> {
			ServerPlayer player = ctx.getSender();
			if (player != null) TaiyokenHandler.cast(player);
		});
		ctx.setPacketHandled(true);
	}
}
