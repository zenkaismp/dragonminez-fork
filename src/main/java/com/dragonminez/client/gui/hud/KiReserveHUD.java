package com.dragonminez.client.gui.hud;

import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

// Zenkai: a v2.2 desenha a reserva de ki do Freeza no HUD novo dela (HudLayout/HudSideMeters/HudBar), que nao veio para o
// fork. Mesma regra de quando aparece e mesma conta (reserva / (ki maximo x reserveMaxRatio)), num medidor vertical simples
// ao lado esquerdo da hotbar, com a cor da aura e o pulso enquanto a reserva alimenta a forma.
public class KiReserveHUD {
	private static final HudSmoother VISIBILITY = new HudSmoother(0.18f, 0.01f);
	private static final HudSmoother ACTIVE = new HudSmoother(0.15f);
	private static final HudSmoother FILL = new HudSmoother(0.12f);
	private static final int BAR_WIDTH = 5;
	private static final int BAR_HEIGHT = 22;

	public static final IGuiOverlay HUD_KI_RESERVE = (forgeGui, guiGraphics, partialTicks, width, height) -> render(guiGraphics, partialTicks, width, height);

	public static void render(GuiGraphics guiGraphics, float partialTicks, int width, int height) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getDebugOverlay().showDebugScreen() || mc.options.hideGui || mc.player == null) return;

		StatsProvider.get(StatsCapability.INSTANCE, mc.player).ifPresent(data -> {
			if (!data.getStatus().isHasCreatedCharacter()) return;
			boolean applicable = "frostdemon".equalsIgnoreCase(data.getCharacter().getRaceName());

			float reserve = applicable ? Math.max(0.0f, data.getRacialData().getEnergyReserve()) : 0.0f;
			boolean feeding = applicable && data.getRacialData().isReserveActive();
			double ratio = ConfigManager.getServerConfig().getRacialSkills().getFrostdemon().getReserveMaxRatio();
			float maxReserve = Math.max(1.0f, (float) (data.getMaxEnergy() * ratio));

			float fraction = FILL.update(Mth.clamp(reserve / maxReserve, 0.0f, 1.0f));
			float visibility = VISIBILITY.update(applicable && (reserve > 0.0f || feeding) ? 1.0f : 0.0f);
			float active = ACTIVE.update(feeding ? 1.0f : 0.0f);
			if (visibility <= 0.01f) return;

			float seconds = (mc.player.tickCount + partialTicks) / 20.0f;
			float pulse = active * (0.5f + 0.5f * Mth.sin(seconds * 8.0f));
			float[] rgb = ColorUtils.hexToRgb(data.getCharacter().getAuraColor());
			int kiColor = ((int) (rgb[0] * 255) << 16) | ((int) (rgb[1] * 255) << 8) | (int) (rgb[2] * 255);
			int color = HudRender.mix(kiColor, 0xFFFFFF, active * 0.35f + pulse * 0.2f);
			int alpha = Math.round(visibility * 255.0f) << 24;

			int x = width / 2 - 91 - 6 - BAR_WIDTH;
			int y = height - BAR_HEIGHT - 1;
			RenderSystem.enableBlend();
			guiGraphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, (Math.round(visibility * 170.0f) << 24));
			int filled = Math.round(fraction * BAR_HEIGHT);
			if (filled > 0) guiGraphics.fill(x, y + BAR_HEIGHT - filled, x + BAR_WIDTH, y + BAR_HEIGHT, alpha | (color & 0xFFFFFF));
			if (!ConfigManager.getUserConfig().getHideHudNumbers()) {
				String text = Math.round(fraction * 100.0f) + "%";
				guiGraphics.pose().pushPose();
				guiGraphics.pose().translate(x + BAR_WIDTH / 2.0f, y - 6.0f, 0.0f);
				guiGraphics.pose().scale(0.5f, 0.5f, 1.0f);
				guiGraphics.drawString(mc.font, text, -mc.font.width(text) / 2, 0, alpha | (HudRender.mix(color, 0xFFFFFF, 0.7f) & 0xFFFFFF), true);
				guiGraphics.pose().popPose();
			}
			RenderSystem.disableBlend();
		});
	}
}
