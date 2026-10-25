package com.dragonminez.common.config;

import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class TechniqueConfig {
	public static final String CURRENT_VERSION = ConfigManager.CONFIG_VERSION;

	@Setter
	private String configVersion;

	@SerializedName("KiAttacks")
	private final Map<String, TechniqueTypeConfig> kiAttacks = new HashMap<>();
	@SerializedName("StrikeAttacks")
	private final Map<String, StrikeAttackConfig> strikeAttacks = new HashMap<>();
	@SerializedName("EvasionAttacks")
	private final Map<String, EvasionAttackConfig> evasionAttacks = new HashMap<>();

	public TechniqueConfig() {
		createDefaults();
	}

	private void createDefaults() {
		for (KiAttackData.KiType type : KiAttackData.KiType.values()) {
			TechniqueTypeConfig cfg = TechniqueTypeConfig.defaults();
			cfg.setCastTimeTicks(defaultCastTimeTicks(type));
			cfg.setBaseSizeCap(defaultBaseSizeCap(type));
			cfg.setTransformedSizeCap(defaultTransformedSizeCap(type));
			cfg.setMaxDestructionRadius(defaultMaxDestructionRadius(type));
			kiAttacks.put(type.name().toLowerCase(), cfg);
		}
		for (String strikeId : PredefinedTechniques.STRIKE_IDS) {
			StrikeAttackConfig cfg = StrikeAttackConfig.defaults();
			cfg.setCooldownTicks(defaultStrikeCooldownTicks(strikeId));
			strikeAttacks.put(strikeId, cfg);
		}
		for (String evasionId : PredefinedTechniques.EVASION_IDS) {
			EvasionAttackConfig cfg = EvasionAttackConfig.defaults();
			cfg.setCooldownTicks(defaultEvasionCooldownTicks(evasionId));
			evasionAttacks.put(evasionId, cfg);
		}
	}

	private static int defaultEvasionCooldownTicks(String evasionId) {
		return switch (evasionId) {
			case "taiyoken" -> 900;
			case "sleep_recovery" -> 1200;
			case "rage_scream" -> 80;
			case "afterimage" -> 200;
			case "candy_beam" -> 600;
			case "gum_bind" -> 900;
			default -> 80;
		};
	}

	private static int defaultStrikeCooldownTicks(String strikeId) {
		return switch (strikeId) {
			case "dragon_fist" -> 320;
			case "oozaru_fist" -> 280;
			case "super_god_fist", "kaioken_attack" -> 240;
			case "deadly_dance_vegetto" -> 200;
			case "blue_hurricane" -> 260;
			case "wolf_fang" -> 140;
			default -> 160;
		};
	}

	/** 0 means "do not scale this type with ki power" -- lasers keep their authored size. */
	private static double defaultBaseSizeCap(KiAttackData.KiType type) {
		return switch (type) {
			case GIANT_BALL -> 15.0;
			case EXPLOSION -> 12.0;
			case MEDIUM_BALL -> 4.5;
			case WAVE -> 2.0;
			case BEAM, DISK -> 1.2;
			case BARRAGE -> 1.5;
			default -> 0.0;
		};
	}

	private static double defaultTransformedSizeCap(KiAttackData.KiType type) {
		return switch (type) {
			case GIANT_BALL, EXPLOSION -> 25.0;
			case MEDIUM_BALL -> 7.0;
			case WAVE -> 5.0;
			case BEAM, DISK -> 2.3;
			case BARRAGE -> 2.5;
			default -> 0.0;
		};
	}

	private static double defaultMaxDestructionRadius(KiAttackData.KiType type) {
		return switch (type) {
			case GIANT_BALL, EXPLOSION -> 32.0;
			case WAVE, BEAM, AREA -> 12.0;
			default -> 6.0;
		};
	}

	private static int defaultCastTimeTicks(KiAttackData.KiType type) {
		return switch (type) {
			case SMALL_BALL, LASER -> 0;
			case MEDIUM_BALL, DISK -> 30;
			case BARRAGE, SHIELD, AREA -> 40;
			case WAVE, BEAM -> 50;
			case GIANT_BALL, EXPLOSION -> 60;
		};
	}

	// Zenkai: o que a v2.2 acrescentou neste arquivo, num techniques.json que ja existia. Na v2.2 isso chega pela
	// regeneracao da versao 2.2.0 do config; aqui a versao do config fica, entao entra so o que falta no arquivo:
	// as chaves da escala dos golpes (baseSizeCap, transformedSizeCap, sizePowerFloor, sizePowerCeiling e
	// maxDestructionRadius, com o padrao da v2.2 de cada tipo) e o golpe SP que nao tem entrada (o Blue Hurricane, com a
	// recarga padrao dele). Valor que ja estava no arquivo nao e tocado.
	public boolean addMissingV22Defaults(JsonObject raw) {
		JsonObject typesJson = raw != null && raw.has("KiAttacks") && raw.get("KiAttacks").isJsonObject() ? raw.getAsJsonObject("KiAttacks") : null;
		JsonObject strikesJson = raw != null && raw.has("StrikeAttacks") && raw.get("StrikeAttacks").isJsonObject() ? raw.getAsJsonObject("StrikeAttacks") : null;
		// Zenkai: as esquivas do combate 2.0 da v2.2 (EvasionAttacks), com a recarga padrao de cada uma, quando faltam.
		JsonObject evasionsJson = raw != null && raw.has("EvasionAttacks") && raw.get("EvasionAttacks").isJsonObject() ? raw.getAsJsonObject("EvasionAttacks") : null;
		TechniqueConfig defaults = new TechniqueConfig();
		boolean changed = false;
		for (Map.Entry<String, TechniqueTypeConfig> entry : kiAttacks.entrySet()) {
			TechniqueTypeConfig def = defaults.kiAttacks.get(entry.getKey());
			TechniqueTypeConfig cfg = entry.getValue();
			if (def == null || cfg == null) continue;
			JsonObject typeJson = typesJson != null && typesJson.has(entry.getKey()) && typesJson.get(entry.getKey()).isJsonObject()
					? typesJson.getAsJsonObject(entry.getKey()) : null;
			if (typeJson == null) continue;
			if (!typeJson.has("baseSizeCap")) { cfg.setBaseSizeCap(def.getBaseSizeCap()); changed = true; }
			if (!typeJson.has("transformedSizeCap")) { cfg.setTransformedSizeCap(def.getTransformedSizeCap()); changed = true; }
			if (!typeJson.has("sizePowerFloor")) { cfg.setSizePowerFloor(def.getSizePowerFloor()); changed = true; }
			if (!typeJson.has("sizePowerCeiling")) { cfg.setSizePowerCeiling(def.getSizePowerCeiling()); changed = true; }
			if (!typeJson.has("maxDestructionRadius")) { cfg.setMaxDestructionRadius(def.getMaxDestructionRadius()); changed = true; }
		}
		if (strikesJson != null) {
			for (Map.Entry<String, StrikeAttackConfig> entry : defaults.strikeAttacks.entrySet()) {
				if (strikesJson.has(entry.getKey())) continue;
				strikeAttacks.put(entry.getKey(), entry.getValue());
				changed = true;
			}
		}
		if (raw != null) {
			for (Map.Entry<String, EvasionAttackConfig> entry : defaults.evasionAttacks.entrySet()) {
				if (evasionsJson != null && evasionsJson.has(entry.getKey())) continue;
				evasionAttacks.put(entry.getKey(), entry.getValue());
				changed = true;
			}
		}
		return changed;
	}

	public TechniqueTypeConfig getKiTypeConfig(KiAttackData.KiType type) {
		if (type == null) return TechniqueTypeConfig.defaults();
		TechniqueTypeConfig config = kiAttacks.get(type.name().toLowerCase());
		return config != null ? config : TechniqueTypeConfig.defaults();
	}

	public StrikeAttackConfig getStrikeConfig(String strikeId) {
		if (strikeId == null || strikeId.isEmpty()) return StrikeAttackConfig.defaults();
		StrikeAttackConfig config = strikeAttacks.get(strikeId.toLowerCase());
		return config != null ? config : StrikeAttackConfig.defaults();
	}

	public EvasionAttackConfig getEvasionConfig(String evasionId) {
		if (evasionId == null || evasionId.isEmpty()) return EvasionAttackConfig.defaults();
		EvasionAttackConfig config = evasionAttacks.get(evasionId.toLowerCase());
		if (config != null) return config;
		EvasionAttackConfig fallback = EvasionAttackConfig.defaults();
		fallback.setCooldownTicks(defaultEvasionCooldownTicks(evasionId.toLowerCase()));
		return fallback;
	}

	@Getter
	@Setter
	public static class TechniqueTypeConfig {
		private int minXPCost = 100;
		private int maxXPCost = -1;
		private double xpCostMultiplier = 1.0;
		private double xpGainMultiplier = 1.0;
		private int xpGainPerHit = 1;
		private int xpGainPerKill = 3;
		private double kiCostMultiplier = 1.0;
		private double damageMultiplier = 1.0;
		private double destructionMultiplier = 1.0;
		private int castTimeTicks = 30;
		/** Size ceiling in base form, before the technique's own XP upgrades are added on top. */
		private double baseSizeCap = 0.0;
		/** Size ceiling while transformed, or stacking a form such as Kaioken. */
		private double transformedSizeCap = 0.0;
		/** Ki power at which an attack sits at its authored size, and at which it hits the cap. */
		private double sizePowerFloor = 100.0;
		private double sizePowerCeiling = 1000000.0;
		private double maxDestructionRadius = 12.0;

		public static TechniqueTypeConfig defaults() {
			return new TechniqueTypeConfig();
		}
	}

	@Getter
	@Setter
	public static class StrikeAttackConfig {
		private int minXPCost = 100;
		private int maxXPCost = -1;
		private double xpCostMultiplier = 1.0;
		private double xpGainMultiplier = 1.0;
		private int xpGainPerHit = 1;
		private int xpGainPerKill = 3;
		private double kiCostMultiplier = 1.0;
		private double damageMultiplier = 1.0;
		private int castTimeTicks = 0;
		private int cooldownTicks = 80;

		public static StrikeAttackConfig defaults() {
			return new StrikeAttackConfig();
		}
	}

	@Getter
	@Setter
	public static class EvasionAttackConfig {
		private int minXPCost = 100;
		private int maxXPCost = -1;
		private double xpCostMultiplier = 1.0;
		private double xpGainMultiplier = 1.0;
		private int xpGainPerHit = 2;
		private double kiCostMultiplier = 1.0;
		private double damageMultiplier = 1.0;
		private int cooldownTicks = 80;

		public static EvasionAttackConfig defaults() {
			return new EvasionAttackConfig();
		}
	}
}
