package com.dragonminez.server.events.players.combat;

import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Zenkai: so as duas perguntas do HeldVictim da v2.2 (d824a29c) que as esquivas usam (alvo do Candy Beam e da amarra de
 * chiclete). O resto da classe da v2.2 (segurar, arremessar, poses de reacao) e do agarrao dos golpes de strike novos,
 * que nao vieram; o teste de world boss tambem sai, porque o sistema de world boss da v2.2 nao existe no fork.
 */
public final class HeldVictim {

	private HeldVictim() {
	}

	public static boolean canTarget(LivingEntity holder, LivingEntity target) {
		if (target == null || target == holder || !target.isAlive() || target.isRemoved()) return false;
		return !(target instanceof Player player && (player.isCreative() || player.isSpectator()));
	}

	public static boolean canHold(LivingEntity holder, LivingEntity target) {
		if (!canTarget(holder, target)) return false;
		return !(target instanceof DBSagasEntity saga && saga.hasHitboxParts());
	}
}
