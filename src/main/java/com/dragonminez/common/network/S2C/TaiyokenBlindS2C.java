package com.dragonminez.common.network.S2C;

import com.dragonminez.client.systems.taiyoken.TaiyokenBlindState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.function.Supplier;

public class TaiyokenBlindS2C {

	private final int durationTicks;

	public TaiyokenBlindS2C(int durationTicks) {
		this.durationTicks = durationTicks;
	}

	public TaiyokenBlindS2C(FriendlyByteBuf buf) {
		this.durationTicks = buf.readInt();
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeInt(durationTicks);
	}

	public void handle(CustomPayloadEvent.Context ctx) {
		ctx.enqueueWork(() -> TaiyokenBlindState.startBlind(durationTicks));
		ctx.setPacketHandled(true);
	}
}
