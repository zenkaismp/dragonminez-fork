package com.dragonminez.client.gui.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Zenkai: so a parte do HudRender da v2.2 que a tela nova de criacao de tecnica usa (blit e retangulo em coordenada
 * fracionada), com o mesmo codigo. O resto da classe (sprites, nine-slice, painel, texto) vem com a HUD nova da v2.2,
 * que nao foi portada.
 */
public final class HudRender {
	private static float alphaScale = 1.0f;
	private static boolean additive;

	private HudRender() {}

	public static void setAdditive(boolean value) {
		additive = value;
	}

	public static void setAlphaScale(float alpha) {
		alphaScale = Mth.clamp(alpha, 0.0f, 1.0f);
	}

	public static float currentAlphaScale() {
		return alphaScale;
	}

	public static void blit(GuiGraphics graphics, ResourceLocation texture, float x, float y, float u, float v, float width, float height, int textureWidth, int textureHeight) {
		blit(graphics, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight);
	}

	public static void blit(GuiGraphics graphics, ResourceLocation texture, float x, float y, float u, float v, float width, float height,
							float regionWidth, float regionHeight, int textureWidth, int textureHeight) {
		if (width <= 0.0f || height <= 0.0f) return;
		graphics.flush();
		RenderSystem.setShaderTexture(0, texture);
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.enableBlend();
		if (additive) {
			RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
		} else {
			RenderSystem.defaultBlendFunc();
		}
		Matrix4f matrix = graphics.pose().last().pose();
		float u0 = u / textureWidth, u1 = (u + regionWidth) / textureWidth;
		float v0 = v / textureHeight, v1 = (v + regionHeight) / textureHeight;
		BufferBuilder builder = Tesselator.getInstance().getBuilder();
		builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
		builder.vertex(matrix, x, y, 0.0f).uv(u0, v0).endVertex();
		builder.vertex(matrix, x, y + height, 0.0f).uv(u0, v1).endVertex();
		builder.vertex(matrix, x + width, y + height, 0.0f).uv(u1, v1).endVertex();
		builder.vertex(matrix, x + width, y, 0.0f).uv(u1, v0).endVertex();
		BufferUploader.drawWithShader(builder.end());
		if (additive) RenderSystem.defaultBlendFunc();
	}

	public static void rect(GuiGraphics graphics, float x, float y, float width, float height, int color) {
		gradient(graphics, x, y, width, height, color, color, color, color);
	}

	public static void rectHorizontal(GuiGraphics graphics, float x, float y, float width, float height, int colorLeft, int colorRight) {
		gradient(graphics, x, y, width, height, colorLeft, colorLeft, colorRight, colorRight);
	}

	public static void rectVertical(GuiGraphics graphics, float x, float y, float width, float height, int colorTop, int colorBottom) {
		gradient(graphics, x, y, width, height, colorTop, colorBottom, colorBottom, colorTop);
	}

	private static void gradient(GuiGraphics graphics, float x, float y, float width, float height, int topLeft, int bottomLeft, int bottomRight, int topRight) {
		if (width <= 0.0f || height <= 0.0f) return;
		Matrix4f matrix = graphics.pose().last().pose();
		VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());
		vertex(buffer, matrix, x, y, topLeft);
		vertex(buffer, matrix, x, y + height, bottomLeft);
		vertex(buffer, matrix, x + width, y + height, bottomRight);
		vertex(buffer, matrix, x + width, y, topRight);
		graphics.flush();
	}

	private static void vertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, int argb) {
		int alpha = Math.round(((argb >>> 24) & 0xFF) * alphaScale);
		buffer.vertex(matrix, x, y, 0.0f).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, alpha).endVertex();
	}
}
