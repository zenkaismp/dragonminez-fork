package com.dragonminez.client.render.effects;

import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import net.minecraft.world.entity.player.Player;

// Ported from v2.2. Ki Surge, the Frost Demon energy reserve and the Namekian assimilation are not in this fork,
// so their branches always answer "not active" and the multipliers stay at 1.0; the form lightning is untouched.
public final class AuraFxState {

	private AuraFxState() {}

	public static StatsData stats(Player player) {
		return StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
	}

	public static boolean isSurging(StatsData stats) {
		return false;
	}

	public static boolean isSurging(Player player) {
		return isSurging(stats(player));
	}

	public static double auraScaleMultiplier(StatsData stats) {
		return 1.0;
	}

	public static double auraSpeedMultiplier(Player player) {
		return 1.0;
	}

	public static boolean isReserveDischarging(StatsData stats) {
		return false;
	}

	public static boolean isReserveDischarging(Player player) {
		return isReserveDischarging(stats(player));
	}

	public static boolean hasLightning(StatsData stats) {
		if (stats == null) return false;
		return formLightning(stats) != null || isSurging(stats) || isReserveDischarging(stats);
	}

	public static boolean hasLightning(Player player) {
		return hasLightning(stats(player));
	}

	private static final String LIGHTNING_CYAN = "#5CD6FF";
	private static final String LIGHTNING_DEEP_BLUE = "#12277A";
	private static final String LIGHTNING_WHITE = "#FFFFFF";

	public static String lightningColor(StatsData stats) {
		if (stats == null) return LIGHTNING_CYAN;
		if (isReserveDischarging(stats)) return LIGHTNING_WHITE;

		FormConfig.FormData form = formLightning(stats);
		if (form != null) {
			String color = form.getLightningColor();
			if (color != null && !color.isEmpty()) return color;
		}
		return complementaryLightningColor(stats.getCharacter().getActiveAuraColor());
	}

	public static String complementaryLightningColor(String auraColorHex) {
		float[] hsv = ColorUtils.hexToHsv(auraColorHex != null && !auraColorHex.isEmpty() ? auraColorHex : "#FFFFFF");
		float hue = hsv[0];
		float saturation = hsv[1];

		if (saturation < 15.0f) return LIGHTNING_DEEP_BLUE;
		if (hue >= 45.0f && hue < 170.0f) return LIGHTNING_CYAN;
		if (hue >= 170.0f && hue < 260.0f) return LIGHTNING_DEEP_BLUE;
		return LIGHTNING_WHITE;
	}

	public static float lightningSpeedMultiplier(StatsData stats) {
		return 1.0f;
	}

	private static FormConfig.FormData formLightning(StatsData stats) {
		Character character = stats.getCharacter();
		if (character.hasActiveStackForm() && character.getActiveStackFormData() != null
				&& character.getActiveStackFormData().getHasLightnings()) {
			return character.getActiveStackFormData();
		}
		if (character.hasActiveForm() && character.getActiveFormData() != null
				&& character.getActiveFormData().getHasLightnings()) {
			return character.getActiveFormData();
		}
		return null;
	}
}
