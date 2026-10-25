package com.dragonminez.client.render.util;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

// v2.2 keeps this as SaiyanInvaderRenderer.TintedBufferSource (the Saiyan Invader raid renderer). This fork does not have
// the v2.2 raids, so the class comes on its own, unchanged: the Demon Eye tints the player with it.
public final class TintedBufferSource implements MultiBufferSource {
	private final MultiBufferSource delegate;
	private final float red;
	private final float green;
	private final float blue;

	public TintedBufferSource(MultiBufferSource delegate, float[] tint) {
		this.delegate = delegate;
		float strength = tint[3];
		this.red = 1.0F - strength + strength * tint[0];
		this.green = 1.0F - strength + strength * tint[1];
		this.blue = 1.0F - strength + strength * tint[2];
	}

	@Override
	public VertexConsumer getBuffer(RenderType renderType) {
		return new TintedConsumer(this.delegate.getBuffer(renderType), this.red, this.green, this.blue);
	}

	private record TintedConsumer(VertexConsumer delegate, float red, float green, float blue) implements VertexConsumer {
		@Override
		public VertexConsumer vertex(double x, double y, double z) {
			this.delegate.vertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer color(int r, int g, int b, int a) {
			this.delegate.color(Math.round(r * this.red), Math.round(g * this.green), Math.round(b * this.blue), a);
			return this;
		}

		@Override
		public VertexConsumer uv(float u, float v) {
			this.delegate.uv(u, v);
			return this;
		}

		@Override
		public VertexConsumer overlayCoords(int u, int v) {
			this.delegate.overlayCoords(u, v);
			return this;
		}

		@Override
		public VertexConsumer uv2(int u, int v) {
			this.delegate.uv2(u, v);
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			this.delegate.normal(x, y, z);
			return this;
		}

		@Override
		public void endVertex() {
			this.delegate.endVertex();
		}

		@Override
		public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v,
						   int overlay, int light, float normalX, float normalY, float normalZ) {
			this.delegate.vertex(x, y, z, r * this.red, g * this.green, b * this.blue, a, u, v, overlay, light, normalX, normalY, normalZ);
		}

		@Override
		public void defaultColor(int r, int g, int b, int a) {
			this.delegate.defaultColor(Math.round(r * this.red), Math.round(g * this.green), Math.round(b * this.blue), a);
		}

		@Override
		public void unsetDefaultColor() {
			this.delegate.unsetDefaultColor();
		}
	}
}
