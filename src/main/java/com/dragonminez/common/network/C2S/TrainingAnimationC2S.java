package com.dragonminez.common.network.C2S;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.TrainingSessionTracker;
import com.dragonminez.common.network.S2C.TriggerAnimationS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.function.Supplier;

public class TrainingAnimationC2S {
	private static final String[] TRAINING_ANIMATIONS = {"base.flex", "base.meditation"};

	private final boolean active;

	public TrainingAnimationC2S(boolean active) {
		this.active = active;
	}

	public TrainingAnimationC2S(FriendlyByteBuf buf) {
		this.active = buf.readBoolean();
	}

	public void toBytes(FriendlyByteBuf buf) {
		buf.writeBoolean(active);
	}

	public void handle(CustomPayloadEvent.Context ctx) {
		ctx.enqueueWork(() -> {
			ServerPlayer player = ctx.getSender();
			if (player == null) return;

			if (active) {
				boolean canTrain = StatsProvider.get(StatsCapability.INSTANCE, player)
						.map(data -> data.getStatus().isHasCreatedCharacter())
						.orElse(false);
				if (!canTrain) return;

				TrainingSessionTracker.begin(player.getUUID(), player.level().getGameTime());

				String animation = TRAINING_ANIMATIONS[player.getRandom().nextInt(TRAINING_ANIMATIONS.length)];
				NetworkHandler.sendToTrackingEntityAndSelf(
						new TriggerAnimationS2C(player.getUUID(), TriggerAnimationS2C.AnimationType.KI_ANIMATION, 1, -1, animation), player);
			} else {
				TrainingSessionTracker.end(player.getUUID());
				NetworkHandler.sendToTrackingEntityAndSelf(
						new TriggerAnimationS2C(player.getUUID(), TriggerAnimationS2C.AnimationType.KI_ANIMATION_STOP, 0, -1, ""), player);
			}
		});
		ctx.setPacketHandled(true);
	}
}
