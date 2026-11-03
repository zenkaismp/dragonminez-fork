package com.dragonminez.common.network.S2C;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.event.network.CustomPayloadEvent;


// Zenkai: a v2.2 marca este pacote como StatsGroupsPacket (o sync por grupos dela); o fork nao tem essa interface.
public class RacialDataSyncS2C {
	private final int playerId;
	private final CompoundTag nbt;

	public RacialDataSyncS2C(ServerPlayer player) {
		this.playerId = player.getId();
		this.nbt = new CompoundTag();
		StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> this.nbt.put("RacialData", data.getRacialData().save()));
	}

	public RacialDataSyncS2C(int playerId, CompoundTag nbt) {
		this.playerId = playerId;
		this.nbt = nbt;
	}

	public int playerId() {
		return this.playerId;
	}

	public CompoundTag groups() {
		return this.nbt;
	}

	public static void encode(RacialDataSyncS2C msg, FriendlyByteBuf buf) {
		buf.writeInt(msg.playerId);
		buf.writeNbt(msg.nbt);
	}

	public static RacialDataSyncS2C decode(FriendlyByteBuf buf) {
		return new RacialDataSyncS2C(buf.readInt(), buf.readNbt());
	}

	public static void handle(RacialDataSyncS2C msg, CustomPayloadEvent.Context ctx) {
		ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> apply(msg)));
		ctx.setPacketHandled(true);
	}

	private static void apply(RacialDataSyncS2C msg) {
		var clientLevel = Minecraft.getInstance().level;
		if (clientLevel == null) return;

		var entity = clientLevel.getEntity(msg.playerId);
		if (entity instanceof Player player) {
			StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
				if (msg.nbt.contains("RacialData")) data.getRacialData().load(msg.nbt.getCompound("RacialData"));
			});
		}
	}
}
