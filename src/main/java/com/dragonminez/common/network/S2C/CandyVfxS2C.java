package com.dragonminez.common.network.S2C;

import com.dragonminez.client.render.effects.CandyEffect;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.event.network.CustomPayloadEvent;

public class CandyVfxS2C {

	private final int casterId;
	private final int targetId;
	private final int candyTicks;
	private final int boltTicks;

	public CandyVfxS2C(int casterId, int targetId, int candyTicks, int boltTicks) {
		this.casterId = casterId;
		this.targetId = targetId;
		this.candyTicks = candyTicks;
		this.boltTicks = boltTicks;
	}

	public CandyVfxS2C(FriendlyByteBuf buf) {
		this.casterId = buf.readInt();
		this.targetId = buf.readInt();
		this.candyTicks = buf.readInt();
		this.boltTicks = buf.readInt();
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeInt(casterId);
		buf.writeInt(targetId);
		buf.writeInt(candyTicks);
		buf.writeInt(boltTicks);
	}

	// Forge 48: o contexto chega direto (CustomPayloadEvent.Context), sem o Supplier<NetworkEvent.Context> do Forge 47.
	public void handle(CustomPayloadEvent.Context ctx) {
		ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
				() -> () -> CandyEffect.apply(casterId, targetId, candyTicks, boltTicks)));
		ctx.setPacketHandled(true);
	}
}
