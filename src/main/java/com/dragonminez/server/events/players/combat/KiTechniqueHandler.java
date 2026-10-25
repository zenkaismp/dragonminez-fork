package com.dragonminez.server.events.players.combat;

import com.dragonminez.Reference;
import com.dragonminez.common.init.entities.ki.KiLaserEntity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class KiTechniqueHandler {

	private static final Map<UUID, Integer> FIRE_ANIMATION_UNTIL = new ConcurrentHashMap<>();

	public static void holdFireAnimation(Player player, int ticks) {
		MinecraftServer server = player.getServer();
		if (server == null) return;
		FIRE_ANIMATION_UNTIL.merge(player.getUUID(), server.getTickCount() + ticks, Math::max);
	}

	public static void onLaserFired(Player player, String techniqueId) {
		if (player.level().isClientSide || techniqueId == null) return;
		if (EmperorDeathBeam.is(techniqueId)) EmperorDeathBeam.holdFireAnimation(player);
	}

	public static boolean isFireAnimationHeld(Player player) {
		Integer until = FIRE_ANIMATION_UNTIL.get(player.getUUID());
		if (until == null) return false;
		MinecraftServer server = player.getServer();
		if (server != null && server.getTickCount() < until) return true;
		FIRE_ANIMATION_UNTIL.remove(player.getUUID());
		return false;
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;

		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) {
			clearAll();
			return;
		}

		EmperorDeathBeam.tick(server);
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		clearAll();
	}

	private static void clearAll() {
		FIRE_ANIMATION_UNTIL.clear();
		EmperorDeathBeam.CHAINS.clear();
	}

	public static final class EmperorDeathBeam {

		public static final String TECHNIQUE_ID = "emperor_death_beam";
		// Cooldown declared for the Emperor in v2.2's PredefinedTechniques. There (and in the fork) calculateDerivedValues
		// replaces every declared ki cooldown with the formula one (11 s for this laser); the Emperor keeps the 14 s.
		public static final int COOLDOWN_SECONDS = 14;

		private static final int TOTAL_SHOTS = 10;
		private static final int SHOT_INTERVAL_TICKS = 7;
		private static final float FOLLOW_UP_DAMAGE_RATIO = 0.2F;
		private static final int FIRE_ANIMATION_HOLD_TICKS = 14;

		private static final Map<UUID, Chain> CHAINS = new ConcurrentHashMap<>();

		private EmperorDeathBeam() {}

		private static final class Chain {
			final ResourceKey<Level> dimension;
			final UUID ownerId;
			final float damage;
			final float speed;
			final int color;
			final int colorBorder;
			final int colorOutline;
			final float size;
			final int armorPenetration;
			final int kiType;
			int shotsFired = 1;
			int ticksUntilNext = SHOT_INTERVAL_TICKS;

			Chain(LivingEntity owner, KiLaserEntity leader) {
				this.dimension = owner.level().dimension();
				this.ownerId = owner.getUUID();
				this.damage = leader.getKiDamage() * FOLLOW_UP_DAMAGE_RATIO;
				this.speed = leader.getKiSpeed();
				this.color = leader.getColor();
				this.colorBorder = leader.getColorBorder();
				this.colorOutline = leader.getColorOutline();
				this.size = leader.getSize();
				this.armorPenetration = leader.getArmorPenetration();
				this.kiType = leader.getKiType().ordinal();
			}
		}

		public static boolean is(String techniqueId) {
			return TECHNIQUE_ID.equals(techniqueId);
		}

		public static void start(LivingEntity owner, KiLaserEntity leader) {
			if (owner.level().isClientSide) return;
			CHAINS.put(owner.getUUID(), new Chain(owner, leader));
		}

		public static void holdFireAnimation(Player player) {
			KiTechniqueHandler.holdFireAnimation(player, FIRE_ANIMATION_HOLD_TICKS);
		}

		private static void tick(MinecraftServer server) {
			if (CHAINS.isEmpty()) return;

			Iterator<Map.Entry<UUID, Chain>> it = CHAINS.entrySet().iterator();
			while (it.hasNext()) {
				Chain chain = it.next().getValue();
				if (--chain.ticksUntilNext > 0) continue;

				if (!fireNextShot(server, chain)) {
					it.remove();
					continue;
				}

				chain.shotsFired++;
				if (chain.shotsFired >= TOTAL_SHOTS) it.remove();
				else chain.ticksUntilNext = SHOT_INTERVAL_TICKS;
			}
		}

		private static boolean fireNextShot(MinecraftServer server, Chain chain) {
			ServerLevel level = server.getLevel(chain.dimension);
			if (level == null) return false;

			Entity ownerEntity = level.getEntity(chain.ownerId);
			if (!(ownerEntity instanceof LivingEntity owner) || !owner.isAlive()) return false;

			KiLaserEntity shot = new KiLaserEntity(level, owner);
			shot.setupEmperorChainShot(owner, chain.damage, chain.speed, chain.color, chain.colorBorder,
					chain.colorOutline, chain.size, chain.armorPenetration, chain.kiType);
			level.addFreshEntity(shot);
			return true;
		}
	}
}
