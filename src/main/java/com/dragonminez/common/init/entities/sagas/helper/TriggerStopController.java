package com.dragonminez.common.init.entities.sagas.helper;

import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;

/**
 * Zenkai: o controle de animacao do GeckoLib 4.3.1 com a parada da animacao disparada que o GeckoLib 4.4+ tem
 * ({@code AnimationController.stopTriggeredAnimation}), e que o controle de dor do NPC da v2.2 usa para soltar a pose
 * presa (agarrao, golpe que derruba) e para repetir a mesma dor seguida. Mesma regra do 4.4: so para se a animacao
 * disparada for a pedida (ou qualquer uma, com nome nulo).
 */
public class TriggerStopController<T extends GeoAnimatable> extends AnimationController<T> {

	public TriggerStopController(T animatable, String name, int transitionTickTime, AnimationStateHandler<T> animationHandler) {
		super(animatable, name, transitionTickTime, animationHandler);
	}

	public boolean stopTriggeredAnimation(String animName) {
		if (this.triggeredAnimation == null) return false;
		if (animName != null) {
			RawAnimation asked = this.triggerableAnimations.get(animName);
			if (asked == null || asked != this.triggeredAnimation) return false;
		}
		if (this.currentRawAnimation == this.triggeredAnimation) {
			this.currentAnimation = null;
			this.animationQueue.clear();
		}
		this.triggeredAnimation = null;
		this.needsAnimationReload = true;
		this.stop();
		return true;
	}
}
