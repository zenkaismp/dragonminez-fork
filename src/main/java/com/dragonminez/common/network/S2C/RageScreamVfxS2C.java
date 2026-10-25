package com.dragonminez.common.network.S2C;

import com.dragonminez.client.render.effects.RageScreamEffect;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.event.network.CustomPayloadEvent;


public class RageScreamVfxS2C {

	private final int entityId;
	private final int durationTicks;

	public RageScreamVfxS2C(int entityId, int durationTicks) {
		this.entityId = entityId;
		this.durationTicks = durationTicks;
	}

	public RageScreamVfxS2C(FriendlyByteBuf buf) {
		this.entityId = buf.readInt();
		this.durationTicks = buf.readInt();
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeInt(entityId);
		buf.writeInt(durationTicks);
	}

	public void handle(CustomPayloadEvent.Context ctx) {
		ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
				() -> () -> RageScreamEffect.start(entityId, durationTicks)));
		ctx.setPacketHandled(true);
	}
}
