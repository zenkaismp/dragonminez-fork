package com.dragonminez.client.gui.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Zenkai: so a parte do HudRender da v2.2 que a tela nova de criacao de tecnica usa (blit e retangulo em coordenada
 * fracionada) e a que a HUD do choque de feixes usa (texto na fonte do DMZ, argb e mistura de cor), com o mesmo codigo.
 * O resto da classe (sprites, nine-slice, painel) vem com a HUD nova da v2.2, que nao foi portada.
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

	private static final ResourceLocation DMZ_FONT = new ResourceLocation(com.dragonminez.Reference.MOD_ID, "smooth");

	public static net.minecraft.network.chat.MutableComponent dmz(String text) {
		return net.minecraft.network.chat.Component.literal(text).withStyle(net.minecraft.network.chat.Style.EMPTY.withFont(DMZ_FONT));
	}

	public static net.minecraft.network.chat.MutableComponent dmz(net.minecraft.network.chat.Component text) {
		return text.copy().withStyle(net.minecraft.network.chat.Style.EMPTY.withFont(DMZ_FONT));
	}

	public static void dmzText(GuiGraphics graphics, String text, float x, float y, float scale, float align, int rgb, float alpha) {
		if (text == null || text.isEmpty()) return;
		dmzText(graphics, dmz(text), x, y, scale, align, rgb, alpha);
	}

	private static net.minecraft.util.FormattedCharSequence uncolored(net.minecraft.util.FormattedCharSequence sequence) {
		return sink -> sequence.accept((index, style, codePoint) ->
				sink.accept(index, style.withColor((net.minecraft.network.chat.TextColor) null), codePoint));
	}

	public static void dmzText(GuiGraphics graphics, net.minecraft.network.chat.Component component, float x, float y, float scale, float align, int rgb, float alpha) {
		int alphaChannel = Math.round(Mth.clamp(alpha, 0.0f, 1.0f) * alphaScale * 255.0f);
		if (alphaChannel <= 3 || component == null) return;

		Font font = Minecraft.getInstance().font;
		int color = (alphaChannel << 24) | (rgb & 0xFFFFFF);
		int border = alphaChannel << 24;
		float offset = -font.width(component) * align;

		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0.0f);
		graphics.pose().scale(scale, scale, 1.0f);
		graphics.pose().translate(offset, 0.0f, 0.0f);
		net.minecraft.util.FormattedCharSequence outline = uncolored(component.getVisualOrderText());
		graphics.drawString(font, outline, -1, 0, border, false);
		graphics.drawString(font, outline, 1, 0, border, false);
		graphics.drawString(font, outline, 0, -1, border, false);
		graphics.drawString(font, outline, 0, 1, border, false);
		graphics.drawString(font, component, 0, 0, color, false);
		graphics.pose().popPose();
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

	public static void text(GuiGraphics graphics, String text, float x, float y, float scale, float align, int rgb, float alpha) {
		dmzText(graphics, text, x, y, scale, align, rgb, alpha);
	}

	public static int argb(float alpha, int rgb) {
		return (Math.round(Mth.clamp(alpha, 0.0f, 1.0f) * 255.0f) << 24) | (rgb & 0xFFFFFF);
	}

	public static int mix(int rgbFrom, int rgbTo, float amount) {
		float t = Mth.clamp(amount, 0.0f, 1.0f);
		int r = Math.round(((rgbFrom >> 16) & 0xFF) + (((rgbTo >> 16) & 0xFF) - ((rgbFrom >> 16) & 0xFF)) * t);
		int g = Math.round(((rgbFrom >> 8) & 0xFF) + (((rgbTo >> 8) & 0xFF) - ((rgbFrom >> 8) & 0xFF)) * t);
		int b = Math.round((rgbFrom & 0xFF) + ((rgbTo & 0xFF) - (rgbFrom & 0xFF)) * t);
		return (r << 16) | (g << 8) | b;
	}
}
