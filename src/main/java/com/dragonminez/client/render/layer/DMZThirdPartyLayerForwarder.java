package com.dragonminez.client.render.layer;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.client.render.VanillaModelSync;
import com.dragonminez.client.render.firstperson.dto.FirstPersonManager;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.mixin.client.LivingEntityRendererAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.List;
import java.util.Set;

public class DMZThirdPartyLayerForwarder<T extends AbstractClientPlayer & GeoAnimatable> extends GeoRenderLayer<T> {

	// @zenkai o CustomHeadLayer SAIU desta lista de pulados: e ele que desenha bloco/cranio no slot
	// de capacete, e sem ele o cosmetico de cabeca (/heads do ZenkaiExtra) fica INVISIVEL, porque o
	// PlayerRendererMixin cancela o renderer vanilla pra todo jogador com capability de stats (ou
	// seja, todos) e o DMZPlayerRenderer nao tem camada de cabeca propria.
	//
	// @zenkai CONTENCAO: este forwarder ficou MORTO desde o porte pro 1.20.2 (ver o comentario da
	// busca no getSkinMap la embaixo), entao ha meses NENHUMA layer de mod terceiro e desenhada.
	// Consertar a busca acorda todas de uma vez num servidor no ar, e render duplicado de Curios /
	// cosmetico apareceria sem ninguem saber de onde veio. Enquanto isso nao for testado com calma,
	// so o que o /heads precisa passa. Pra liberar geral: FORWARD_EVERYTHING = true.
	// @zenkai INTERRUPTOR GERAL. false devolve o forwarder ao estado em que ele passou meses: sai
	// antes de tocar em qualquer coisa. Existe pra dar bisect de UMA constante quando aparecer um
	// bug de render depois de mexer aqui, sem precisar reverter commit nem caçar jar antigo.
	private static final boolean ZENKAI_ENABLED = true;
	private static final boolean FORWARD_EVERYTHING = false;
	private static final Set<Class<?>> ZENKAI_FORWARDED = Set.of(CustomHeadLayer.class);

	/** @zenkai layer que ja explodiu uma vez; existe so pra nao repetir log 60x por segundo. */
	private static final Set<Class<?>> LOGGED_FAILURES = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private static final Set<Class<?>> VANILLA_LAYER_CLASSES = Set.of(
			HumanoidArmorLayer.class,
			ItemInHandLayer.class,
			PlayerItemInHandLayer.class,
			ArrowLayer.class,
			Deadmau5EarsLayer.class,
			CapeLayer.class,
			ElytraLayer.class,
			ParrotOnShoulderLayer.class,
			SpinAttackEffectLayer.class,
			BeeStingerLayer.class
	);

	public DMZThirdPartyLayerForwarder(GeoRenderer<T> renderer) {
		super(renderer);
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
		if (!ZENKAI_ENABLED) return; // @zenkai
		if (!"root".equals(bone.getName())) return;
		var stats = StatsProvider.get(StatsCapability.INSTANCE, animatable).orElse(new StatsData(animatable));
		if (stats.getCharacter().isOozaruCached()) return;
		// @zenkai em primeira pessoa o DMZPOVPlayerRenderer esconde o BONE "head"
		// (DMZPOVPlayerRenderer:66), mas este metodo roda no bone "root" e nao seria alcancado por
		// aquele esconde: a layer de cabeca desenharia dentro da camera do jogador.
		if (FirstPersonManager.shouldRenderFirstPerson(animatable)) return;

		var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		// @zenkai BUG DO PORTE 1.20.1 -> 1.20.2, e a razao de TODO este forwarder ser codigo morto.
		// No 1.20.1 o skin map era Map<String, ...> e a busca era por getModelName(). No 1.20.2 ele
		// virou Map<PlayerSkin.Model, ...>, chaveado por ENUM. A chamada antiga
		// (.get(animatable.getSkin().model().id())) passa uma String; como Map.get aceita Object,
		// isso COMPILA sem erro e sem aviso, e devolve null pra sempre em runtime, porque String
		// nunca e igual a constante de enum. Resultado: return no if de baixo, todo frame, pra todo
		// jogador, desde o porte. O getOrDefault imita o proprio EntityRenderDispatcher.getRenderer,
		// que cai em WIDE quando nao acha.
		var skinMap = dispatcher.getSkinMap();
		var vanillaRenderer = skinMap.getOrDefault(animatable.getSkin().model(), skinMap.get(PlayerSkin.Model.WIDE));
		if (!(vanillaRenderer instanceof PlayerRenderer playerRenderer)) return;

		List<RenderLayer<?, ?>> layers;
		try {
			layers = ((LivingEntityRendererAccessor) playerRenderer).dragonminez$getLayers();
		} catch (Exception e) {
			return;
		}

		if (layers == null || layers.isEmpty()) return;

		BakedGeoModel geoModel = this.getRenderer().getGeoModel().getBakedModel(this.getRenderer().getGeoModel().getModelResource(animatable));
		PlayerModel<AbstractClientPlayer> vanillaModel = playerRenderer.getModel();
		if (geoModel == null) return;

		VanillaModelSync.sync(geoModel, vanillaModel, animatable);
		vanillaModel.attackTime = animatable.getAttackAnim(partialTick);
		vanillaModel.riding = animatable.isPassenger();
		vanillaModel.young = false;

		float bodyYaw = Mth.rotLerp(partialTick, animatable.yBodyRotO, animatable.yBodyRot);
		float headYaw = Mth.rotLerp(partialTick, animatable.yHeadRotO, animatable.yHeadRot);
		float netHeadYaw = headYaw - bodyYaw;
		float headPitch = Mth.lerp(partialTick, animatable.xRotO, animatable.getXRot());
		float limbSwing = animatable.walkAnimation.position(partialTick);
		float limbSwingAmount = animatable.walkAnimation.speed(partialTick);
		float ageInTicks = animatable.tickCount + partialTick;

		poseStack.pushPose();

		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(0.0F, -1.501F, 0.0F);

		for (RenderLayer layer : layers) {
			Class<?> layerClass = layer.getClass();
			if (!FORWARD_EVERYTHING && !ZENKAI_FORWARDED.contains(layerClass)) continue; // @zenkai
			if (VANILLA_LAYER_CLASSES.contains(layerClass)) continue;

			String className = layerClass.getName().toLowerCase();
			if (className.contains("cosmeticarmor") || className.contains("cosarmor")) continue;

			poseStack.pushPose();
			try {
				RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> typedLayer =
						(RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) layer;

				typedLayer.render(poseStack, bufferSource, packedLight, animatable,
						limbSwing, limbSwingAmount, partialTick, ageInTicks,
						netHeadYaw, headPitch);
			} catch (Exception e) {
				// @zenkai era catch vazio. Uma layer de terceiro que explode sumia sem rastro e o
				// proximo bug de render viraria outra cacada as cegas. Loga a primeira vez de cada
				// classe: sem o Set isto seria uma linha de log por frame.
				if (LOGGED_FAILURES.add(layerClass)) {
					LogUtil.error(Env.CLIENT, "Layer de terceiro falhou ao renderizar: {}",
							layerClass.getName(), e);
				}
			}
			poseStack.popPose();
		}

		bufferSource.getBuffer(renderType);
		poseStack.popPose();
	}
}

