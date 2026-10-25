package com.dragonminez.common.network.S2C;

import com.dragonminez.common.network.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.DistExecutor;

/**
 * Zenkai: a parada da animacao disparada do NPC de saga ({@code DBSagasEntity.stopTriggeredAnimation}), que no GeckoLib
 * 4.4+ da v2.2 vai pelo pacote do proprio GeckoLib e no 4.3.1 do fork nao existe.
 */
public class SagaAnimStopS2C {

	private final int entityId;
	private final String controller;
	private final String animation;

	public SagaAnimStopS2C(int entityId, String controller, String animation) {
		this.entityId = entityId;
		this.controller = controller;
		this.animation = animation == null ? "" : animation;
	}

	public SagaAnimStopS2C(FriendlyByteBuf buf) {
		this.entityId = buf.readVarInt();
		this.controller = buf.readUtf(64);
		this.animation = buf.readUtf(64);
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeVarInt(this.entityId);
		buf.writeUtf(this.controller, 64);
		buf.writeUtf(this.animation, 64);
	}

	public void handle(CustomPayloadEvent.Context ctx) {
		ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
				() -> () -> ClientPacketHandler.handleSagaAnimStop(this.entityId, this.controller, this.animation.isEmpty() ? null : this.animation)));
		ctx.setPacketHandled(true);
	}
}
