package com.dragonminez.common.network.C2S;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.function.Supplier;

public class DeleteTechniqueC2S {
	private final String techniqueId;

	public DeleteTechniqueC2S(String techniqueId) {
		this.techniqueId = techniqueId;
	}

	public DeleteTechniqueC2S(FriendlyByteBuf buf) {
		this.techniqueId = buf.readUtf();
	}

	public void toBytes(FriendlyByteBuf buf) {
		buf.writeUtf(this.techniqueId);
	}

	public boolean handle(CustomPayloadEvent.Context supplier) {
		CustomPayloadEvent.Context context = supplier;
		context.enqueueWork(() -> {
			ServerPlayer player = context.getSender();
			if (player != null) {
				StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
					data.getTechniques().removeTechnique(techniqueId);
					data.getSkills().removeSkill(techniqueId);
					NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(player), player);
				});
			}
		});
		context.setPacketHandled(true);
		return true;
	}
}