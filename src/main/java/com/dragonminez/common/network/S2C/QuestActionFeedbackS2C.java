package com.dragonminez.common.network.S2C;

import com.dragonminez.common.network.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.function.Supplier;

public class QuestActionFeedbackS2C {
    private final Component message;

    public QuestActionFeedbackS2C(Component message) {
        this.message = message == null ? Component.empty() : message;
    }

    public QuestActionFeedbackS2C(FriendlyByteBuf buf) {
        this.message = buf.readComponent();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeComponent(message);
    }

    public void handle(CustomPayloadEvent.Context contextSupplier) {
        CustomPayloadEvent.Context context = contextSupplier;
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.handleQuestActionFeedback(message)));
        context.setPacketHandled(true);
    }
}
