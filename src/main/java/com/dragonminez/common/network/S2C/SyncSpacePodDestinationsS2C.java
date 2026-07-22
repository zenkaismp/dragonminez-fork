package com.dragonminez.common.network.S2C;

import com.dragonminez.common.network.CompressionUtil;
import com.dragonminez.common.spacepod.SpacePodDestinationDefinition;
import com.dragonminez.common.spacepod.SpacePodDestinationRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.List;
import java.util.function.Supplier;

public class SyncSpacePodDestinationsS2C {

	private final byte[] payload;

	public SyncSpacePodDestinationsS2C(List<SpacePodDestinationDefinition> destinations) {
		this.payload = CompressionUtil.compress(SpacePodDestinationRegistry.toJson(destinations));
	}

	public SyncSpacePodDestinationsS2C(FriendlyByteBuf buf) {
		this.payload = buf.readByteArray();
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeByteArray(payload);
	}

	public void handle(CustomPayloadEvent.Context ctx) {
		ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
			String json = CompressionUtil.decompress(payload);
			SpacePodDestinationRegistry.setClientDestinations(SpacePodDestinationRegistry.fromJson(json));
		}));
		ctx.setPacketHandled(true);
	}
}
