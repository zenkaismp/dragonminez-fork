package com.dragonminez.common.network.S2C;

import com.dragonminez.common.network.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.function.Supplier;

public class PartyInviteToastS2C {
    private final String inviterName;

    public PartyInviteToastS2C(String inviterName) {
        this.inviterName = inviterName == null ? "" : inviterName;
    }

    public PartyInviteToastS2C(FriendlyByteBuf buf) {
        this.inviterName = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(inviterName);
    }

    public void handle(CustomPayloadEvent.Context contextSupplier) {
        CustomPayloadEvent.Context context = contextSupplier;
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.handlePartyInviteToastPacket(inviterName)));
        context.setPacketHandled(true);
    }
}
