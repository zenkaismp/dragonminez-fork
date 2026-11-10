package com.dragonminez.common.racial;

import com.dragonminez.Reference;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Zenkai (P10 do pacote de passivas): o segundo gancho do reset unico dos Zenkais. Com storage externo (banco ou JSON) o
 * dado de verdade so pousa depois do login, e o login pula o reset enquanto o load esta pendente; aqui ele roda em cima
 * do dado carregado, antes do sync que o StorageManager manda logo em seguida. Sem storage externo quem roda e o login
 * ({@code StatsCapability.onPlayerLogin}). A regra toda mora no {@link RacialReset#applyZenkaiResetIfDue}.
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ZenkaiResetHook {

	private ZenkaiResetHook() {
	}

	@SubscribeEvent
	public static void onPlayerDataApplied(DMZEvent.PlayerDataAppliedEvent event) {
		ServerPlayer player = event.getPlayer();
		if (player == null) return;
		StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> RacialReset.applyZenkaiResetIfDue(player, data));
	}
}
