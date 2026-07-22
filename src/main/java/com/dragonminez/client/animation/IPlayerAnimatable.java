package com.dragonminez.client.animation;

import software.bernie.geckolib.core.animatable.model.CoreGeoBone;

public interface IPlayerAnimatable {

	void dragonminez$setFlying(boolean flying);
	boolean dragonminez$isFlying();

	void dragonminez$triggerDash(int direction);
	void dragonminez$triggerEvasion();

	void dragonminez$setShootingKi(boolean shootingKi);
	boolean dragonminez$isShootingKi();

	void dragonminez$playMeleeAnimation(String animationName, boolean isOffhand, float speedMultiplier);
	boolean dragonminez$isPlayingCombatAnimation();

	/** Velocity slew-limiter on the action bones: eases GeckoLib's one-frame cross-controller pose snaps
	 *  ("flick") without lagging real animation. Called every frame from the model's setCustomAnimations. */
	void dragonminez$smoothActionBones(CoreGeoBone root, CoreGeoBone waist, CoreGeoBone rightArm,
	                                   CoreGeoBone leftArm, CoreGeoBone rightLeg, CoreGeoBone leftLeg);

	boolean dragonminez$isAttackingWithOffhand();
	float dragonminez$getCombatPlacementWeight();

	void dragonminez$playKiAnimation(String animationName, boolean hold);
	void dragonminez$stopKiAnimation();

	String dragonminez$getCurrentPlayingAnimation();
}