package com.dragonminez.mixin.common;

import com.dragonminez.common.combat.logic.player.PlayerAttackHelper;
import com.dragonminez.common.combat.logic.player.PlayerAttackProperties;
import com.dragonminez.common.combat.player.AttackHand;
import com.dragonminez.common.combat.util.Player_DMZ;
// import net.bettercombat.logic.PlayerAttackHelper;
import com.dragonminez.common.util.lists.SaiyanForms;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;

import java.util.Objects;

@Mixin(Player.class)
public abstract class PlayerMixin implements Player_DMZ, PlayerAttackProperties {
	@Unique
	private int comboCount = 0;
	private AttackHand lastAttack = null;
	@Unique
	private int dragonminez$critTick = -1;
	@Unique
	private boolean dragonminez$critActive = false;

	@Override
	public int getComboCount() {
		return this.comboCount;
	}

	@Override
	public void setComboCount(int comboCount) {
		this.comboCount = comboCount;
	}

	@ModifyVariable(method = "attack", at = @At("STORE"), ordinal = 0)
	private float dragonminez$floorAttackDamageForDmz(float attackDamage) {
		if (attackDamage > 0.0F) return attackDamage;
		Player self = (Player) (Object) this;
		boolean created = StatsProvider.get(StatsCapability.INSTANCE, self)
				.map(data -> data.getStatus().isHasCreatedCharacter())
				.orElse(false);
		return created ? 0.1F : attackDamage;
	}

	@Redirect(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"))
	private int dragonminez$suppressDamageIndicatorParticles(ServerLevel level, ParticleOptions particle, double x, double y, double z, int count, double xDist, double yDist, double zDist, double speed) {
		if (particle == ParticleTypes.DAMAGE_INDICATOR) return 0;
		return level.sendParticles(particle, x, y, z, count, xDist, yDist, zDist, speed);
	}

	@Redirect(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
	public ItemStack dragonminez$getMainHandItem_Redirect(Player instance) {
		if (this.comboCount < 0) return instance.getMainHandItem();
		AttackHand hand = PlayerAttackHelper.getCurrentAttack(instance, this.comboCount);
		if (hand == null) return instance.getMainHandItem();

		this.lastAttack = hand;
		return hand.itemStack();
	}

	@Redirect(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setItemInHand(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;)V"))
	public void dragonminez$setItemInHand_Redirect(Player instance, InteractionHand handArg, ItemStack itemStack) {
		if (this.comboCount < 0) instance.setItemInHand(handArg, itemStack);

		AttackHand hand = this.lastAttack;
		if (hand == null) hand = PlayerAttackHelper.getCurrentAttack(instance, this.comboCount);

		if (hand == null) {
			instance.setItemInHand(handArg, itemStack);
			return;
		}

		InteractionHand redirectedHand = hand.isOffHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		instance.setItemInHand(redirectedHand, itemStack);
	}

	@Nullable
	@Override
	public AttackHand getCurrentAttack() {
		if (this.comboCount < 0) return null;
		return PlayerAttackHelper.getCurrentAttack((Player)(Object)this, this.comboCount);
	}

	@Override
	public boolean rollAndGetCriticalStatus(double chance) {
		Player player = (Player) (Object) this;
		int currentTick = player.tickCount;

		if (this.dragonminez$critTick != currentTick) {
			this.dragonminez$critActive = player.getRandom().nextFloat() < chance;
			this.dragonminez$critTick = currentTick;
		}
		return this.dragonminez$critActive;
	}

	@Inject(method = "crit", at = @At("HEAD"), cancellable = true)
	private void dragonminez$suppressVanillaCrit(Entity entityHit, CallbackInfo ci) {
		ci.cancel();
	}

	@Inject(method = "magicCrit", at = @At("HEAD"), cancellable = true)
	private void dragonminez$suppressVanillaMagicCrit(Entity entityHit, CallbackInfo ci) {
		ci.cancel();
	}

	// ponytail: EntityEvent.Size was removed in 1.20.2. Player hitbox + eye-height scaling
	// (forms / Oozaru) moves onto Player's own getDimensions / getStandingEyeHeight overrides,
	// the idiomatic replacement. Logic ported verbatim from the old StatsEvents.onEntitySize.
	@Unique
	private float[] dragonminez$formScaling() {
		Player self = (Player) (Object) this;
		return StatsProvider.get(StatsCapability.INSTANCE, self).map(data -> {
			var character = data.getCharacter();
			String currentForm = character.getActiveForm();
			String race = character.getRaceName().toLowerCase();
			String logicKey = character.getRenderLogicKey();
			Float[] resolved = character.getResolvedModelScaling();
			float configScaleX = resolved[0];
			float configScaleY = resolved[1];
			boolean isOozaru = logicKey.startsWith("oozaru") ||
					(race.equals("saiyan") && (Objects.equals(currentForm, SaiyanForms.OOZARU) || Objects.equals(currentForm, SaiyanForms.GOLDEN_OOZARU)));
			if (isOozaru) {
				float baseOozaruSize = 3.8f;
				float visualScaleX = Math.max(0.1f, configScaleX - 2.8f);
				float visualScaleY = Math.max(0.1f, configScaleY - 2.8f);
				return new float[]{visualScaleX * baseOozaruSize, visualScaleY * baseOozaruSize};
			}
			return new float[]{configScaleX, configScaleY};
		}).orElse(null);
	}

	@Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
	private void dragonminez$scaleDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		float[] scaling = dragonminez$formScaling();
		if (scaling == null) return;
		if (pose == Pose.DYING || pose == Pose.SLEEPING) {
			cir.setReturnValue(EntityDimensions.fixed(0.2F, 0.2F));
			return;
		}
		float finalWidth = Math.round(0.6F * scaling[0] * 10.0F) / 10.0F;
		float finalHeight = Math.round(1.9F * scaling[1] * 10.0F) / 10.0F;
		float poseHeightMultiplier = 1.0F;
		if (pose == Pose.CROUCHING) {
			poseHeightMultiplier = 1.5F / 1.8F;
		} else if (pose == Pose.SWIMMING || pose == Pose.FALL_FLYING || pose == Pose.SPIN_ATTACK) {
			poseHeightMultiplier = 0.6F / 1.8F;
		}
		float alturaSegura = Math.round(finalHeight * poseHeightMultiplier * 10.0F) / 10.0F;
		cir.setReturnValue(EntityDimensions.fixed(finalWidth, alturaSegura));
	}

	@Inject(method = "getStandingEyeHeight", at = @At("RETURN"), cancellable = true)
	private void dragonminez$scaleEyeHeight(Pose pose, EntityDimensions dimensions, CallbackInfoReturnable<Float> cir) {
		float[] scaling = dragonminez$formScaling();
		if (scaling == null) return;
		if (pose == Pose.DYING || pose == Pose.SLEEPING) {
			cir.setReturnValue(0.2F);
			return;
		}
		float eyeHeightMultiplier = 1.0F;
		if (pose == Pose.CROUCHING) {
			eyeHeightMultiplier = 1.27F / 1.62F;
		} else if (pose == Pose.SWIMMING || pose == Pose.FALL_FLYING || pose == Pose.SPIN_ATTACK) {
			eyeHeightMultiplier = 0.4F / 1.62F;
		}
		float finalEyeHeight = Math.round(1.7F * scaling[1] * eyeHeightMultiplier * 10.0F) / 10.0F;
		cir.setReturnValue(finalEyeHeight);
	}
}