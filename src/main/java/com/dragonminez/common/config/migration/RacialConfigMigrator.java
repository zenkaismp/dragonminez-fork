package com.dragonminez.common.config.migration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class RacialConfigMigrator {

	private RacialConfigMigrator() {
	}

	/**
	 * As chaves raciais planas da 2.1 viram as secoes por raca da v2.2, com os mesmos valores.
	 *
	 * <p>Zenkai (pacote de passivas, secao 6, item 2): antes o migrador saia inteiro se ja existisse a secao saiyan, e um
	 * general-server.json da 2.1 editado a mao com a secao nova (os valores do Saiyajin do pacote) perdia o Namek, o Majin e o
	 * Bio da 2.1, que voltavam ao padrao. Agora cada chave plana vai pra sua secao quando a secao ainda nao tem aquela chave
	 * (a secao escrita a mao vence), e o migrador so age se houver alguma chave plana. Arquivo ja migrado: nada muda.</p>
	 */
	public static void migrate(JsonObject root) {
		if (root == null || !root.has("racialSkills") || !root.get("racialSkills").isJsonObject()) return;
		JsonObject racial = root.getAsJsonObject("racialSkills");
		if (!hasLegacyKeys(racial)) return;

		mergeSection(racial, "saiyan",
				new String[]{"saiyanRacialSkill", "enabled"},
				new String[]{"saiyanZenkaiMinLevel", "minLevel"},
				new String[]{"saiyanZenkaiAmount", "permanentMaxBuffs"},
				new String[]{"saiyanZenkaiBoosts", "buffStats"},
				new String[]{"saiyanZenkaiCooldownSeconds", "cooldownSeconds"});

		mergeSection(racial, "namekian",
				new String[]{"namekianRacialSkill", "enabled"},
				new String[]{"namekianAssimilationAmount", "assimilationAmount"},
				new String[]{"namekianAssimilationHealthRegen", "assimilationHealthRegen"},
				new String[]{"namekianAssimilationStatBoost", "assimilationStatBoost"},
				new String[]{"namekianAssimilationBoosts", "assimilationBoosts"},
				new String[]{"namekianAssimilationOnNamekNpcs", "assimilationOnNamekNpcs"});

		mergeSection(racial, "majin",
				new String[]{"majinAbsoprtionSkill", "enabled"},
				new String[]{"majinAbsorptionAmount", "absorptionAmount"},
				new String[]{"majinAbsorptionHealthRegen", "absorptionHealthRegen"},
				new String[]{"majinAbsorptionStatsCopy", "absorptionStatCopy"},
				new String[]{"majinAbsorptionBoosts", "absorptionBoosts"},
				new String[]{"majinAbsorptionOnMobs", "absorptionOnMobs"});

		mergeSection(racial, "human",
				new String[]{"humanRacialSkill", "enabled"});

		mergeSection(racial, "frostdemon",
				new String[]{"frostDemonRacialSkill", "enabled"},
				new String[]{"frostDemonTPBoost", "tpBoost"});

		mergeSection(racial, "bioandroid",
				new String[]{"bioAndroidRacialSkill", "enabled"},
				new String[]{"bioAndroidCooldownSeconds", "cooldownSeconds"},
				new String[]{"bioAndroidDrainRatio", "drainRatio"});

		for (String[] mapping : ALL_FLAT_KEYS) racial.remove(mapping[0]);
	}

	/** Ainda tem alguma chave racial plana da 2.1 (o que pede migracao). */
	public static boolean hasLegacyKeys(JsonObject racial) {
		if (racial == null) return false;
		for (String[] mapping : ALL_FLAT_KEYS) {
			if (racial.has(mapping[0])) return true;
		}
		return false;
	}

	private static void mergeSection(JsonObject flatSource, String sectionName, String[]... keyMappings) {
		JsonElement existing = flatSource.get(sectionName);
		JsonObject section = existing != null && existing.isJsonObject() ? existing.getAsJsonObject() : new JsonObject();
		for (String[] mapping : keyMappings) {
			String oldKey = mapping[0];
			String newKey = mapping[1];
			if (section.has(newKey)) continue;
			if (flatSource.has(oldKey) && !flatSource.get(oldKey).isJsonNull()) {
				section.add(newKey, flatSource.get(oldKey));
			}
		}
		flatSource.add(sectionName, section);
	}

	private static final String[][] ALL_FLAT_KEYS = {
			{"saiyanRacialSkill"}, {"saiyanZenkaiMinLevel"}, {"saiyanZenkaiAmount"},
			{"saiyanZenkaiHealthRegen"}, {"saiyanZenkaiStatBoost"}, {"saiyanZenkaiBoosts"}, {"saiyanZenkaiCooldownSeconds"},
			{"namekianRacialSkill"}, {"namekianAssimilationAmount"}, {"namekianAssimilationHealthRegen"},
			{"namekianAssimilationStatBoost"}, {"namekianAssimilationBoosts"}, {"namekianAssimilationOnNamekNpcs"},
			{"majinAbsoprtionSkill"}, {"majinReviveSkill"}, {"majinAbsorptionAmount"}, {"majinAbsorptionHealthRegen"},
			{"majinAbsorptionStatsCopy"}, {"majinAbsorptionBoosts"}, {"majinAbsorptionOnMobs"},
			{"majinReviveCooldownSeconds"}, {"majinReviveHealthRatioPerBlop"},
			{"humanRacialSkill"}, {"humanKiRegenBoost"},
			{"frostDemonRacialSkill"}, {"frostDemonTPBoost"},
			{"bioAndroidRacialSkill"}, {"bioAndroidCooldownSeconds"}, {"bioAndroidDrainRatio"},
	};
}
