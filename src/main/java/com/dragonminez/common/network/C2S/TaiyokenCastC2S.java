package com.dragonminez.common.network.C2S;

import com.dragonminez.server.events.players.combat.EvasionAttackHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Zenkai: na v2.2 este pacote virou o {@link EvasionCastC2S} (e25e31743) e o TaiyokenHandler saiu. Aqui o registro
 * fica no mesmo lugar do NetworkHandler (o id dos pacotes e a ordem de registro) e so repassa para a esquiva do
 * Taiyoken; o client novo manda o EvasionCastC2S, registrado no fim da lista.
 */
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
			if (player != null) EvasionAttackHandler.cast(player, "taiyoken");
		});
		ctx.setPacketHandled(true);
	}
}
