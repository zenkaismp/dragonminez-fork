package com.dragonminez.server.events;

import com.dragonminez.Reference;
import com.dragonminez.server.storage.StorageManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class DataSyncHandler {

	/**
	 * HIGHEST: dispara o load do storage antes de qualquer outro handler deste mesmo evento — em
	 * especial o do {@code StatsCapability}, que so pode decidir se manda o sync inicial depois de
	 * saber que existe um load em andamento. Com a ordem invertida o cliente recebia os defaults e
	 * abria a criacao de personagem por cima do personagem que estava no banco.
	 */
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity().level().isClientSide) return;
		if (event.getEntity() instanceof ServerPlayer player) {
			StorageManager.loadPlayer(player);
		}
	}

	@SubscribeEvent
	public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity().level().isClientSide) return;
		if (event.getEntity() instanceof ServerPlayer player) {
			StorageManager.savePlayer(player);
		}
	}
}