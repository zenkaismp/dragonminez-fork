package com.dragonminez.common.network.C2S;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.hair.HairSanitizer;
import com.dragonminez.common.hair.HairStyleSlot;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.PacketRateLimiter;
import com.dragonminez.common.network.S2C.AppearanceSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.wish.wishes.ReCustomizeWish;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

public class UpdateCustomHairC2S {
	private static final long MIN_INTERVAL_TICKS = 10L;

	private final int hairIndex;
	private final CustomHair customHair;

	public UpdateCustomHairC2S(HairStyleSlot slot, CustomHair customHair) {
		this(slot.index(), customHair);
	}

	private UpdateCustomHairC2S(int hairIndex, CustomHair customHair) {
		this.hairIndex = hairIndex;
		this.customHair = customHair;
	}

	public static void encode(UpdateCustomHairC2S msg, FriendlyByteBuf buf) {
		buf.writeInt(msg.hairIndex);
		boolean hasHair = msg.customHair != null;
		buf.writeBoolean(hasHair);
		if (hasHair) msg.customHair.writeToBuffer(buf);
	}

	public static UpdateCustomHairC2S decode(FriendlyByteBuf buf) {
		int hairIndex = buf.readInt();
		boolean hasHair = buf.readBoolean();
		CustomHair hair = hasHair ? CustomHair.readFromBuffer(buf) : null;
		return new UpdateCustomHairC2S(hairIndex, hair);
	}

	// Same rule as v2.2: character not created yet, a pending recustomize wish, or Popo nearby.
	public static boolean canChangeHair(ServerPlayer player, StatsData data) {
		return !data.getStatus().isHasCreatedCharacter() || ReCustomizeWish.isPending(player)
				|| NPCActionC2S.isNpcInRange(player, "popo");
	}

	public static void handle(UpdateCustomHairC2S msg, CustomPayloadEvent.Context ctx) {
		ctx.enqueueWork(() -> {
			ServerPlayer player = ctx.getSender();
			if (player == null || msg.customHair == null) return;

			String playerName = player.getGameProfile().getName();
			HairStyleSlot slot = HairStyleSlot.byIndex(msg.hairIndex);
			if (slot == null) {
				LogUtil.warn(Env.SERVER, "Rejected hair update from {}: invalid style index {}", playerName, msg.hairIndex);
				return;
			}
			if (!PacketRateLimiter.allow(player.getUUID(), "hair_style_" + slot.name(), player.level().getGameTime(), MIN_INTERVAL_TICKS)) {
				LogUtil.debug(Env.SERVER, "Rate limited hair update from {} for style {}", playerName, slot);
				return;
			}

			StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
				if (!canChangeHair(player, data)) {
					LogUtil.warn(Env.SERVER, "Rejected hair update from {}: no character creation, recustomize wish or Popo nearby", playerName);
					NetworkHandler.sendToPlayer(new AppearanceSyncS2C(player), player);
					return;
				}
				HairSanitizer.sanitizeAndLog(msg.customHair, slot, playerName);
				data.getCharacter().setHairStyle(slot, msg.customHair);
				data.getCharacter().setHairId(0);
				NetworkHandler.sendToTrackingEntityAndSelf(new AppearanceSyncS2C(player), player);
			});
		});
		ctx.setPacketHandled(true);
	}
}
