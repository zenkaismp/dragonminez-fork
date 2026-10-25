package com.dragonminez.mixin.client;

import com.dragonminez.client.render.DMZRendererCache;
import com.dragonminez.client.render.effects.CandyEffect;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

	@Inject(method = "onResourceManagerReload(Lnet/minecraft/server/packs/resources/ResourceManager;)V", at = @At("TAIL"), locals = LocalCapture.CAPTURE_FAILHARD)
	public void dmz$onResourceManagerReload(ResourceManager resourceManager, CallbackInfo ci, EntityRendererProvider.Context context) {
		DMZRendererCache.onResourceReload(context);
	}

	// v2.2: mob que virou doce (Majin Candy) e desenhado como o doce no lugar do modelo dele (CandyEffect). Na v2.2 este
	// gancho tambem embrulha a assimilacao namekuseijin e o teletransporte, que nao existem no fork.
	@Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
	private void dmz$candyRender(Entity entity, double x, double y, double z, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		if (CandyEffect.renderInstead(entity, x, y, z, partialTick, poseStack, buffer, packedLight)) {
			ci.cancel();
		}
	}
}