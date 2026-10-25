package com.dragonminez.server.events.players.combat;

import com.dragonminez.Reference;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.server.storage.StorageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * Zenkai: o aviso em ingles, no chat, da XP devolvida quando um golpe de ki salvo vira esquiva (o Solar Flare da v2.2;
 * ver {@code Techniques.migrateKiToEvasion}). Espera o load do storage do jogador terminar: o load do .dat chega antes e
 * e trocado pelo do banco, que e o que vale.
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TechniqueMigrationRefund {

	public static final String MESSAGE = "message.dragonminez.technique.evasion_refund";
	private static final int CHECK_INTERVAL_TICKS = 20;

	private TechniqueMigrationRefund() {}

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
		if (player.tickCount % CHECK_INTERVAL_TICKS != 0 || StorageManager.isLoadPending(player.getUUID())) return;
		StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
			Map<String, int[]> refunds = data.getTechniques().takeMigrationRefunds();
			for (Map.Entry<String, int[]> entry : refunds.entrySet()) {
				player.sendSystemMessage(Component.translatable(MESSAGE,
						Component.translatable("technique.dragonminez." + entry.getKey()),
						entry.getValue()[0], entry.getValue()[1]).withStyle(ChatFormatting.GOLD));
			}
		});
	}
}
