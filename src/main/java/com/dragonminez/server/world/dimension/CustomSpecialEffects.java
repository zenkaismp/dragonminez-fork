package com.dragonminez.server.world.dimension;

import com.dragonminez.Reference;
import com.dragonminez.client.render.DMZCloudsRenderer;
import com.dragonminez.client.systems.DragonSkyState;
import com.dragonminez.client.util.ClientStateHelper;
import com.dragonminez.server.world.biome.DemonRealmBiomes;
import com.dragonminez.server.world.gen.DemonRealmGeneration;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

public class CustomSpecialEffects extends DimensionSpecialEffects {
	public static final ResourceLocation NAMEK_EFFECTS = new ResourceLocation(Reference.MOD_ID, "namek_effects");
	public static final ResourceLocation OTHERWORLD_EFFECTS = new ResourceLocation(Reference.MOD_ID, "otherworld_effects");
	public static final ResourceLocation HTC_EFFECT = new ResourceLocation(Reference.MOD_ID, "htc_effects");
	public static final ResourceLocation SACREDKAI_EFFECTS = new ResourceLocation(Reference.MOD_ID, "sacredkai_effects");
	public static final ResourceLocation DEMON_REALM_EFFECTS = new ResourceLocation(Reference.MOD_ID, "demon_realm_effects");
	final DMZCloudsRenderer cloudRenderer;

	public CustomSpecialEffects(float cloudLevel, boolean hasGround, SkyType skyType, boolean forceBrightLightMap, boolean constantAmbientLight) {
		super(cloudLevel, hasGround, skyType, forceBrightLightMap, constantAmbientLight);
		this.cloudRenderer = new DMZCloudsRenderer();
	}

	@Override
	public Vec3 getBrightnessDependentFogColor(Vec3 biomeFogColor, float daylight) {
		return biomeFogColor.multiply((double)(daylight * 0.94F + 0.06F), (double)(daylight * 0.94F + 0.06F), (double)(daylight * 0.91F + 0.09F));
	}

	@Override
	public boolean isFoggyAt(int x, int y) {
		return false; // False = No hay niebla | True = Hay niebla
	}

	@Override
	public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY, double camZ, Matrix4f projectionMatrix) {
		return false; // False = No se renderizan nubes | True = Se renderizan nubes
	}

	@Override
	public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
		return false; // True = No se renderiza el cielo | False = Se renderiza el cielo
	}

	@Override
	public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ) {
		return false; // False = No se renderiza la lluvia | True = Se renderiza la lluvia
	}

	// ---- Da v2.2 (CustomSpecialEffects): o que o DemonRealmEffects usa. O ceu da Terra e de Namek da v2.2 nao
	// foi portado, entao ninguem mais no fork chama isto.

	protected static final float SKY_RADIUS = 100.0F;

	@FunctionalInterface
	protected interface SkyColor {
		Vec3 at(float elevation, float angle);
	}

	protected record SkySprite(float yaw, float elevation, float size, float red, float green, float blue, float alpha,
							   float drift, float phase) {
		protected static SkySprite of(float yaw, float elevation, float size, int rgb, float alpha, float drift, float phase) {
			return new SkySprite(yaw, elevation, size, (rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F,
					(rgb & 0xFF) / 255.0F, alpha, drift, phase);
		}
	}

	protected static float[] uniformRings(int below, int above) {
		float[] rings = new float[below + above + 1];
		for (int i = 0; i < rings.length; i++) rings[i] = (i - below) * 90.0F / above;
		return rings;
	}

	protected static void drawDome(Matrix4f pose, float[] rings, int segments, SkyColor color) {
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder builder = Tesselator.getInstance().getBuilder();
		builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		for (int ring = 0; ring < rings.length - 1; ring++) {
			float low = rings[ring];
			float high = rings[ring + 1];
			for (int segment = 0; segment < segments; segment++) {
				float from = segment * 360.0F / segments;
				float to = (segment + 1) * 360.0F / segments;
				domeVertex(builder, pose, low, from, color.at(low, from));
				domeVertex(builder, pose, low, to, color.at(low, to));
				domeVertex(builder, pose, high, to, color.at(high, to));
				domeVertex(builder, pose, high, from, color.at(high, from));
			}
		}
		BufferUploader.drawWithShader(builder.end());
	}

	private static void domeVertex(BufferBuilder builder, Matrix4f pose, float elevation, float angle, Vec3 color) {
		float e = elevation * Mth.DEG_TO_RAD;
		float a = angle * Mth.DEG_TO_RAD;
		builder.vertex(pose, SKY_RADIUS * Mth.cos(e) * Mth.cos(a), SKY_RADIUS * Mth.sin(e), SKY_RADIUS * Mth.cos(e) * Mth.sin(a))
				.color((float) color.x, (float) color.y, (float) color.z, 1.0F).endVertex();
	}

	protected static Vec3 gradient(float elevation, float[] stops, Vec3[] colors) {
		if (elevation <= stops[0]) return colors[0];
		for (int i = 1; i < stops.length; i++) {
			if (elevation <= stops[i]) {
				return colors[i - 1].lerp(colors[i], (elevation - stops[i - 1]) / (stops[i] - stops[i - 1]));
			}
		}
		return colors[colors.length - 1];
	}

	protected static void drawSprites(PoseStack poseStack, ResourceLocation texture, List<SkySprite> sprites,
									  float time, float alphaScale, boolean tinted, boolean additive) {
		if (alphaScale <= 0.01F || sprites.isEmpty()) return;
		if (additive) {
			RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
		} else {
			RenderSystem.defaultBlendFunc();
		}
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, texture);
		BufferBuilder builder = Tesselator.getInstance().getBuilder();
		builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		for (SkySprite sprite : sprites) {
			float red = tinted ? sprite.red() : 1.0F;
			float green = tinted ? sprite.green() : 1.0F;
			float blue = tinted ? sprite.blue() : 1.0F;
			float alpha = sprite.alpha() * alphaScale;
			float bob = Mth.sin(time * 0.02F + sprite.phase()) * 0.6F;
			float size = sprite.size();
			poseStack.pushPose();
			poseStack.mulPose(Axis.YP.rotationDegrees(sprite.yaw() + time * sprite.drift()));
			poseStack.mulPose(Axis.XP.rotationDegrees(90.0F - sprite.elevation() - bob));
			Matrix4f pose = poseStack.last().pose();
			builder.vertex(pose, -size, SKY_RADIUS, -size).uv(0.0F, 0.0F).color(red, green, blue, alpha).endVertex();
			builder.vertex(pose, size, SKY_RADIUS, -size).uv(1.0F, 0.0F).color(red, green, blue, alpha).endVertex();
			builder.vertex(pose, size, SKY_RADIUS, size).uv(1.0F, 1.0F).color(red, green, blue, alpha).endVertex();
			builder.vertex(pose, -size, SKY_RADIUS, size).uv(0.0F, 1.0F).color(red, green, blue, alpha).endVertex();
			poseStack.popPose();
		}
		BufferUploader.drawWithShader(builder.end());
	}

	protected static double smoothStep(double t) {
		double clamped = Mth.clamp(t, 0.0D, 1.0D);
		return clamped * clamped * (3.0D - 2.0D * clamped);
	}

	protected static final float DRAGON_NIGHT_SKY_DARKEN = 0.2F;
	protected static final float DRAGON_NIGHT_CLOUD_SHADE = 0.28F;
	private static final int DRAGON_NIGHT_STAR_ATTEMPTS = 1500;
	private static VertexBuffer dragonNightStars;

	protected Vec3 dragonNightFogColor(double cameraY) {
		return null;
	}

	protected static boolean skyBlockedByEffect(Camera camera) {
		return camera.getEntity() instanceof LivingEntity living
				&& (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
	}

	public static void applyDragonNightFog(ViewportEvent.ComputeFogColor event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !(mc.level.effects() instanceof CustomSpecialEffects effects)) return;
		if (event.getCamera().getFluidInCamera() != FogType.NONE || skyBlockedByEffect(event.getCamera())) return;
		float night = DragonSkyState.darkness((float) event.getPartialTick());
		if (night <= 0.0F) return;
		Vec3 fog = effects.dragonNightFogColor(event.getCamera().getPosition().y);
		if (fog == null) return;
		event.setRed(Mth.lerp(night, event.getRed(), (float) fog.x));
		event.setGreen(Mth.lerp(night, event.getGreen(), (float) fog.y));
		event.setBlue(Mth.lerp(night, event.getBlue(), (float) fog.z));
	}

	protected static void applyDragonNightLightmap(ClientLevel level, float partialTicks, float blockLightRedFlicker, int blockIndex, int skyIndex, Vector3f colors) {
		float night = DragonSkyState.darkness(partialTicks);
		float exposure = level.dimensionType().ambientLight() > 0.0F ? skyIndex / 15.0F : 1.0F;
		float weight = night * exposure;
		if (weight <= 0.0F) return;

		float skyFactor = level.getSkyFlashTime() > 0 ? 1.0F : DRAGON_NIGHT_SKY_DARKEN * 0.95F + 0.05F;
		float sky = lightCurve(skyIndex) * skyFactor;
		float block = lightCurve(blockIndex) * blockLightRedFlicker;
		Vector3f target = new Vector3f(block, block * ((block * 0.6F + 0.4F) * 0.6F + 0.4F), block * (block * block * 0.6F + 0.4F));
		target.add(new Vector3f(DRAGON_NIGHT_SKY_DARKEN, DRAGON_NIGHT_SKY_DARKEN, 1.0F).lerp(new Vector3f(1.0F, 1.0F, 1.0F), 0.35F).mul(sky));
		target.lerp(new Vector3f(0.75F, 0.75F, 0.75F), 0.04F);
		float worldDarken = Minecraft.getInstance().gameRenderer.getDarkenWorldAmount(partialTicks);
		if (worldDarken > 0.0F) target.lerp(new Vector3f(target).mul(0.7F, 0.6F, 0.6F), worldDarken);
		colors.lerp(target, weight);
	}

	private static float lightCurve(int level) {
		float f = level / 15.0F;
		return f / (4.0F - 3.0F * f);
	}

	protected void renderCloudLayer(PoseStack poseStack, Matrix4f projectionMatrix, float partialTick, double camX, double camY, double camZ, Vec3 color, float cloudHeight, float cloudTicks) {
		float shade = Mth.lerp(DragonSkyState.darkness(partialTick), 1.0F, DRAGON_NIGHT_CLOUD_SHADE);
		RenderSystem.setShaderColor(shade, shade, shade, 1.0F);
		this.cloudRenderer.render(poseStack, projectionMatrix, partialTick, camX, camY, camZ, color, cloudHeight, cloudTicks);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
	}

	protected static void drawDragonNightStars(PoseStack poseStack, Matrix4f projectionMatrix, float brightness, Vec3 tint) {
		if (brightness <= 0.01F) return;
		if (dragonNightStars == null) {
			dragonNightStars = new VertexBuffer(VertexBuffer.Usage.STATIC);
			dragonNightStars.bind();
			dragonNightStars.upload(buildDragonNightStars());
			VertexBuffer.unbind();
		}
		RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
		RenderSystem.setShaderColor((float) tint.x, (float) tint.y, (float) tint.z, brightness);
		dragonNightStars.bind();
		dragonNightStars.drawWithShader(poseStack.last().pose(), projectionMatrix, GameRenderer.getPositionColorShader());
		VertexBuffer.unbind();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
	}

	private static BufferBuilder.RenderedBuffer buildDragonNightStars() {
		RandomSource random = RandomSource.create(0xD2A60L);
		BufferBuilder builder = Tesselator.getInstance().getBuilder();
		builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
		Vector3f side = new Vector3f(1.0F, 0.0F, 0.0F);
		for (int i = 0; i < DRAGON_NIGHT_STAR_ATTEMPTS; i++) {
			Vector3f direction = new Vector3f(random.nextFloat() * 2.0F - 1.0F, random.nextFloat() * 2.0F - 1.0F, random.nextFloat() * 2.0F - 1.0F);
			float lengthSquared = direction.lengthSquared();
			float size = 0.12F + random.nextFloat() * 0.14F;
			float shade = 0.45F + random.nextFloat() * 0.55F;
			float spin = random.nextFloat() * Mth.TWO_PI;
			if (lengthSquared >= 1.0F || lengthSquared < 0.01F) continue;
			direction.normalize();
			if (direction.y < -0.2F) continue;

			Vector3f right = new Vector3f(direction).cross(Math.abs(direction.y) > 0.99F ? side : up).normalize();
			Vector3f forward = new Vector3f(direction).cross(right).normalize();
			Vector3f a = new Vector3f(right).mul(Mth.cos(spin) * size).add(new Vector3f(forward).mul(Mth.sin(spin) * size));
			Vector3f b = new Vector3f(forward).mul(Mth.cos(spin) * size).sub(new Vector3f(right).mul(Mth.sin(spin) * size));
			Vector3f center = new Vector3f(direction).mul(SKY_RADIUS);
			starVertex(builder, new Vector3f(center).sub(a).sub(b), shade);
			starVertex(builder, new Vector3f(center).sub(a).add(b), shade);
			starVertex(builder, new Vector3f(center).add(a).add(b), shade);
			starVertex(builder, new Vector3f(center).add(a).sub(b), shade);
		}
		return builder.end();
	}

	private static void starVertex(BufferBuilder builder, Vector3f position, float shade) {
		builder.vertex(position.x, position.y, position.z).color(shade, shade, shade, 1.0F).endVertex();
	}

	public static class NamekEffects extends CustomSpecialEffects {
		public NamekEffects() {
			super(192.0F, true, SkyType.NORMAL, false, false);
		}

		@Override
		public Vec3 getBrightnessDependentFogColor(Vec3 biomeFogColor, float daylight) {
			if (ClientStateHelper.isPorungaActive) {
				return new Vec3(0.02, 0.02, 0.02);
			}
			return biomeFogColor.multiply((double)(daylight * 0.94F + 0.06F), (double)(daylight * 0.94F + 0.06F), (double)(daylight * 0.91F + 0.09F));
		}

		@Override
		public boolean isFoggyAt(int x, int y) {
			if (ClientStateHelper.isPorungaActive) {
				return true;
			}
			return false;
		}

		@Override
		public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
			if (!ClientStateHelper.isPorungaActive) {
				return false;
			}

			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.depthMask(false);

			RenderSystem.setShader(GameRenderer::getPositionColorShader);

			Tesselator tesselator = Tesselator.getInstance();
			BufferBuilder bufferbuilder = tesselator.getBuilder();

			float r = 0.05f;
			float g = 0.05f;
			float b = 0.05f;
			float a = 1.0f;

			for (int i = 0; i < 6; ++i) {
				poseStack.pushPose();
				if (i == 1) poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90.0F));
				if (i == 2) poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0F));
				if (i == 3) poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180.0F));
				if (i == 4) poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90.0F));
				if (i == 5) poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-90.0F));

				Matrix4f matrix4f = poseStack.last().pose();

				bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
				bufferbuilder.vertex(matrix4f, -100.0F, -100.0F, -100.0F).color(r, g, b, a).endVertex();
				bufferbuilder.vertex(matrix4f, -100.0F, -100.0F, 100.0F).color(r, g, b, a).endVertex();
				bufferbuilder.vertex(matrix4f, 100.0F, -100.0F, 100.0F).color(r, g, b, a).endVertex();
				bufferbuilder.vertex(matrix4f, 100.0F, -100.0F, -100.0F).color(r, g, b, a).endVertex();
				tesselator.end();

				poseStack.popPose();
			}

			RenderSystem.depthMask(true);
			RenderSystem.disableBlend();

			return true;
		}

		@Override
		public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY, double camZ, Matrix4f projectionMatrix) {
			Vec3 namekGreen = new Vec3(0.659D, 0.922D, 0.443D);
			this.cloudRenderer.render(poseStack, projectionMatrix, partialTick, camX, camY, camZ, namekGreen);
			return true;
		}

		@Override
		public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ) {
			return true;
		}
	}

	public static class HTCEffects extends CustomSpecialEffects {
		public HTCEffects() {
			super(192.0F, true, SkyType.NORMAL, false, false);
		}

		@Override
		public Vec3 getBrightnessDependentFogColor(Vec3 biomeFogColor, float daylight) {
			return biomeFogColor.multiply((double)(daylight * 0.94F + 0.06F), (double)(daylight * 0.94F + 0.06F), (double)(daylight * 0.91F + 0.09F));
		}

		@Override
		public boolean isFoggyAt(int x, int y) {
			return Math.abs(x) > 64;
		}

		@Override
		public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY, double camZ, Matrix4f projectionMatrix) {
			return true;
		}

		@Override
		public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
			return true;
		}

		@Override
		public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ) {
			return true;
		}
	}

	public static class OtherWorldEffects extends CustomSpecialEffects {
		public OtherWorldEffects() {
			super(192.0F, false, SkyType.NORMAL, false, false);
		}

		@Override
		public Vec3 getBrightnessDependentFogColor(Vec3 biomeFogColor, float daylight) {
			return biomeFogColor.multiply((double)(daylight * 0.94F + 0.06F), (double)(daylight * 0.94F + 0.06F), (double)(daylight * 0.91F + 0.09F));
		}

		@Override
		public boolean isFoggyAt(int x, int y) {
			return Math.abs(x) > 64;
		}

		@Override
		public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY, double camZ, Matrix4f projectionMatrix) {
			Vec3 namekGreen = new Vec3(0.929D, 0.929D, 0.157D);
			this.cloudRenderer.render(poseStack, projectionMatrix, partialTick, camX, camY, camZ, namekGreen);
			return true;
		}

		@Override
		public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
			return true;
		}

		@Override
		public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ) {
			return true;
		}
	}

	public static class SacredKaiEffects extends CustomSpecialEffects {
		public SacredKaiEffects() {
			super(192.0F, true, SkyType.NORMAL, false, false);
		}
	}

	public static class DemonRealmEffects extends CustomSpecialEffects {
		private static final ResourceLocation NAMEK_PLANET = skyTexture("namek_planet");
		private static final ResourceLocation PALE_MOON = skyTexture("pale_moon");
		private static final ResourceLocation GLIND_PLANET = skyTexture("glind_planet");
		private static final ResourceLocation MEGA_PLANET = skyTexture("mega_planet");
		private static final ResourceLocation RED_SUN = skyTexture("red_sun");

		private static final Vec3[] HORIZON = {Vec3.fromRGB24(DemonRealmBiomes.FIRST_SKY), Vec3.fromRGB24(DemonRealmBiomes.SECOND_SKY), Vec3.fromRGB24(DemonRealmBiomes.THIRD_SKY)};
		private static final Vec3[] ZENITH = {Vec3.fromRGB24(0x6E0D0D), Vec3.fromRGB24(0x93C93A), Vec3.fromRGB24(0xC24FC6)};
		private static final Vec3[] NIGHT_HORIZON = {Vec3.fromRGB24(0x2B0A06), Vec3.fromRGB24(0x18220E), Vec3.fromRGB24(0x2A1029)};
		private static final Vec3[] NIGHT_ZENITH = {Vec3.fromRGB24(0x0C0202), Vec3.fromRGB24(0x050A03), Vec3.fromRGB24(0x0B030D)};
		private static final Vec3 STAR_TINT = new Vec3(1.0D, 0.9D, 0.95D);
		private static final float NIGHT_STARS = 0.5F;
		private static final Vec3[] CLOUD_COLORS = {new Vec3(0.5D, 0.14D, 0.12D), new Vec3(0.97D, 1.0D, 0.88D), new Vec3(1.0D, 0.86D, 0.97D)};
		private static final float[] CLOUD_HEIGHTS = {DemonRealmGeneration.FIRST_CLOUDS, DemonRealmGeneration.SECOND_CLOUDS, DemonRealmGeneration.THIRD_CLOUDS};
		private static final float LIGHT_HAZE_START = 0.65F;

		private static final int LOWER_FADE_START = DemonRealmGeneration.CRUST_A_BOTTOM - DemonRealmGeneration.STALACTITE_DEPTH;
		private static final int LOWER_FADE_END = DemonRealmGeneration.CRUST_A_TOP;
		private static final int UPPER_FADE_START = DemonRealmGeneration.CRUST_B_BOTTOM - DemonRealmGeneration.STALACTITE_DEPTH;
		private static final int UPPER_FADE_END = DemonRealmGeneration.CRUST_B_TOP;

		private static final float[] DOME_RINGS = uniformRings(3, 12);
		private static final int DOME_SEGMENTS = 32;

		public DemonRealmEffects() {
			super(192.0F, true, SkyType.NORMAL, false, false);
		}

		public static double[] layerWeights(double y) {
			double lower = smoothStep((y - LOWER_FADE_START) / (LOWER_FADE_END - LOWER_FADE_START));
			double upper = smoothStep((y - UPPER_FADE_START) / (UPPER_FADE_END - UPPER_FADE_START));
			return new double[]{1.0D - lower, lower - upper, upper};
		}

		public static void adjustFog(ViewportEvent.RenderFog event) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null || !DemonRealmDimension.DEMON_REALM_KEY.equals(mc.level.dimension())) return;
			if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || event.getType() != FogType.NONE) return;

			double[] weights = layerWeights(event.getCamera().getPosition().y);
			float near = event.getNearPlaneDistance();
			float lightHaze = Math.min(near, event.getFarPlaneDistance() * LIGHT_HAZE_START);
			float target = (float) (weights[0] * near + (weights[1] + weights[2]) * lightHaze);
			if (target >= near - 0.5F) return;

			event.setNearPlaneDistance(target);
			event.setCanceled(true);
		}

		@Override
		public Vec3 getBrightnessDependentFogColor(Vec3 biomeFogColor, float daylight) {
			return mix(HORIZON, layerWeights(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().y));
		}

		@Override
		protected Vec3 dragonNightFogColor(double cameraY) {
			return mix(NIGHT_HORIZON, layerWeights(cameraY));
		}

		@Override
		public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker, float skyLight, int pixelX, int pixelY, Vector3f colors) {
			applyDragonNightLightmap(level, partialTicks, blockLightRedFlicker, pixelX, pixelY, colors);
		}

		@Override
		public float[] getSunriseColor(float timeOfDay, float partialTicks) {
			return null;
		}

		@Override
		public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
			double[] weights = layerWeights(camera.getPosition().y);
			float night = DragonSkyState.darkness(partialTick);

			RenderSystem.depthMask(false);
			RenderSystem.disableCull();
			Vec3 horizon = mix(HORIZON, weights).lerp(mix(NIGHT_HORIZON, weights), night);
			Vec3 zenith = mix(ZENITH, weights).lerp(mix(NIGHT_ZENITH, weights), night);
			drawDome(poseStack.last().pose(), DOME_RINGS, DOME_SEGMENTS, (elevation, angle) -> domeColor(elevation, horizon, zenith));

			RenderSystem.enableBlend();
			drawDragonNightStars(poseStack, projectionMatrix, night * NIGHT_STARS, STAR_TINT);
			if (weights[0] > 0.01D && night < 0.99F) {
				drawBody(poseStack, RED_SUN, 250.0F, 14.0F, 42.0F, (float) weights[0] * (1.0F - night), true);
			}
			if (weights[1] > 0.01D) {
				float alpha = (float) weights[1];
				drawBody(poseStack, PALE_MOON, 110.0F, 14.0F, 32.0F, alpha * 0.55F, false);
				drawBody(poseStack, PALE_MOON, 150.0F, 30.0F, 9.0F, alpha * 0.45F, false);
				drawBody(poseStack, NAMEK_PLANET, 40.0F, 28.0F, 22.0F, alpha * 0.8F, false);
				drawBody(poseStack, GLIND_PLANET, 205.0F, 36.0F, 12.0F, alpha * 0.85F, false);
				drawBody(poseStack, MEGA_PLANET, 300.0F, 20.0F, 7.0F, alpha * 0.8F, false);
			}

			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
			RenderSystem.defaultBlendFunc();
			RenderSystem.disableBlend();
			RenderSystem.enableCull();
			RenderSystem.depthMask(true);
			return true;
		}

		@Override
		public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY, double camZ, Matrix4f projectionMatrix) {
			double[] weights = layerWeights(camY);
			int layer = weights[0] >= weights[1] ? 0 : (weights[1] >= weights[2] ? 1 : 2);
			renderCloudLayer(poseStack, projectionMatrix, partialTick, camX, camY, camZ, CLOUD_COLORS[layer], CLOUD_HEIGHTS[layer], level.getGameTime() + partialTick);
			return true;
		}

		@Override
		public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ) {
			return true;
		}

		private static Vec3 domeColor(float elevation, Vec3 horizon, Vec3 zenith) {
			if (elevation <= 0.0F) return horizon;
			return horizon.lerp(zenith, Math.pow(Mth.sin(elevation * Mth.DEG_TO_RAD), 0.6D));
		}

		private static void drawBody(PoseStack poseStack, ResourceLocation texture, float yaw, float elevation, float size, float alpha, boolean additive) {
			if (additive) {
				RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
			} else {
				RenderSystem.defaultBlendFunc();
			}
			RenderSystem.setShader(GameRenderer::getPositionTexShader);
			RenderSystem.setShaderTexture(0, texture);
			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);

			poseStack.pushPose();
			poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
			poseStack.mulPose(Axis.XP.rotationDegrees(90.0F - elevation));
			Matrix4f pose = poseStack.last().pose();
			BufferBuilder builder = Tesselator.getInstance().getBuilder();
			builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
			builder.vertex(pose, -size, SKY_RADIUS, -size).uv(0.0F, 0.0F).endVertex();
			builder.vertex(pose, size, SKY_RADIUS, -size).uv(1.0F, 0.0F).endVertex();
			builder.vertex(pose, size, SKY_RADIUS, size).uv(1.0F, 1.0F).endVertex();
			builder.vertex(pose, -size, SKY_RADIUS, size).uv(0.0F, 1.0F).endVertex();
			BufferUploader.drawWithShader(builder.end());
			poseStack.popPose();
		}

		private static Vec3 mix(Vec3[] colors, double[] weights) {
			return colors[0].scale(weights[0]).add(colors[1].scale(weights[1])).add(colors[2].scale(weights[2]));
		}

		private static ResourceLocation skyTexture(String name) {
			return new ResourceLocation(Reference.MOD_ID, "textures/environment/demon_realm/" + name + ".png");
		}
	}

	public static void registerSpecialEffects(RegisterDimensionSpecialEffectsEvent event) {
		event.register(NAMEK_EFFECTS, new NamekEffects());
		event.register(OTHERWORLD_EFFECTS, new OtherWorldEffects());
		event.register(HTC_EFFECT, new HTCEffects());
		event.register(SACREDKAI_EFFECTS, new SacredKaiEffects());
		event.register(DEMON_REALM_EFFECTS, new DemonRealmEffects());
	}

}

