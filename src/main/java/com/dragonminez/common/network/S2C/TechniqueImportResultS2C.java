package com.dragonminez.common.network.S2C;

import com.dragonminez.client.gui.character.SkillsMenuScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.function.Supplier;

public class TechniqueImportResultS2C {
	public enum Status {
		INVALID,
		NOT_ENOUGH_TP,
		IMPORTED
	}

	private final Status status;
	private final int value;

	public TechniqueImportResultS2C(Status status, int value) {
		this.status = status;
		this.value = value;
	}

	public TechniqueImportResultS2C(FriendlyByteBuf buf) {
		this.status = buf.readEnum(Status.class);
		this.value = buf.readInt();
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeEnum(status);
		buf.writeInt(value);
	}

	public void handle(CustomPayloadEvent.Context ctx) {
		ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SkillsMenuScreen.handleTechniqueImportResult(status, value)));
		ctx.setPacketHandled(true);
	}
}
