package com.dragonminez.mixin.common;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

// 1.20.2: Entity.canEnterPose was removed. The pose-fit check moved to
// Player.canPlayerFitWithinBlocksAndEntitiesWhen(Pose), which now derives its box from
// getDimensions(pose). Scaled-player pose fitting is therefore already handled by
// PlayerMixin's getDimensions override, so this mixin no longer needs an injector.
@Mixin(Entity.class)
public abstract class EntityMixin {
}
