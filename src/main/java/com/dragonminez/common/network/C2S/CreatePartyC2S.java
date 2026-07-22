package com.dragonminez.common.network.C2S;

import com.dragonminez.common.quest.PartyManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.function.Supplier;

public class CreatePartyC2S {

    public CreatePartyC2S() {
    }

    public CreatePartyC2S(FriendlyByteBuf ignored) {
    }

    public void encode(FriendlyByteBuf ignored) {
    }

    public void handle(CustomPayloadEvent.Context contextSupplier) {
        CustomPayloadEvent.Context context = contextSupplier;
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (!PartyManager.canInvitePlayers(player)) return;
            PartyManager.getOrCreateParty(player);
        });
        context.setPacketHandled(true);
    }
}
