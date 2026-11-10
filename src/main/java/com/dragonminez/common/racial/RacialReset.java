package com.dragonminez.common.racial;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.RaceCharacterConfig;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.racial.impl.BioAndroidEvolution;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.BonusStats;
import com.dragonminez.common.stats.character.Cooldowns;
import com.dragonminez.server.storage.StorageManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;

public final class RacialReset {
	private static final String ABSORPTION_SLOT_COOLDOWN = "AbsorptionSlot_";
	private static final String CELL_JR_SLOT_COOLDOWN = "CellJrSlot_";
	// Todo bonus do Zenkai tem este prefixo: os permanentes (Zenkai_1..3), o temporario (Zenkai_Temp_<tempo>) e os da 2.1.
	public static final String ZENKAI_BONUS_PREFIX = "Zenkai_";
	private static final String[] BONUS_KEYS = {"STR", "SKP", "DEF", "STM", "VIT", "PWR", "ENE"};

	private RacialReset() {
	}

	public static void reset(ServerPlayer player, StatsData data) {
		RacialData racial = data.getRacialData();
		BioAndroidEvolution.despawnAllCellJrs(new RacialContext(player, data));

		var ownedBonusNames = racial.getOwnedBonusNames();
		if (!ownedBonusNames.isEmpty()) {
			for (String bonusName : new ArrayList<>(ownedBonusNames)) data.getBonusStats().removeAllBonuses(bonusName);
			ownedBonusNames.clear();
		} else {
			clearLegacyBonuses(data);
		}

		racial.getAssimilations().clear();
		racial.getAbsorptions().clear();
		racial.getCellJrs().clear();
		racial.setAbsorptionSlotCounter(0);
		racial.setZenkaiPermanentUses(0);
		racial.setZenkaiReleaseBonus(0.0);
		racial.setTempZenkaiBuffName("");

		Cooldowns cooldowns = data.getCooldowns();
		cooldowns.removeCooldown(Cooldowns.ZENKAI);
		cooldowns.removeCooldown(Cooldowns.ZENKAI_TEMP_BUFF);
		for (String key : cooldowns.getAllCooldowns().keySet()) {
			if (key.startsWith(ABSORPTION_SLOT_COOLDOWN) || key.startsWith(CELL_JR_SLOT_COOLDOWN)) cooldowns.removeCooldown(key);
		}
		data.getResources().setRacialSkillCount(0);
	}

	/**
	 * Zenkai (P10 do pacote de passivas): o reset unico dos Zenkais antigos, chamado no login e quando o storage aplica os
	 * dados do jogador (PlayerDataAppliedEvent). Com saiyan.zenkaiResetVersion em 0 (o padrao) nao faz nada e nao grava
	 * nada. Com a chave acima da marca do jogador, e se a raca dele usa a passiva saiyan ou a ficha ainda tem bonus
	 * Zenkai_, roda o {@link #resetZenkai}, grava a marca e loga. Devolve true quando resetou.
	 */
	public static boolean applyZenkaiResetIfDue(ServerPlayer player, StatsData data) {
		int target = ConfigManager.getServerConfig().getRacialSkills().getSaiyan().getZenkaiResetVersion();
		if (target <= 0) return false;
		if (!data.getStatus().isHasCreatedCharacter()) return false;

		RacialData racial = data.getRacialData();
		if (racial.getZenkaiResetVersion() >= target) return false;
		if (!usesSaiyanPassive(data) && !hasZenkaiBonus(data.getBonusStats())) return false;

		resetZenkai(player, data);
		racial.setZenkaiResetVersion(target);
		// Grava em ate ~10 s (varredura do storage), e nao no proximo autosave: uma queda antes disso repetiria o reset.
		StorageManager.markDirty(player.getUUID());
		LogUtil.info(Env.SERVER, "[Zenkai] reset: {}", player.getGameProfile().getName());
		return true;
	}

	/**
	 * Zenkai (P10): so a parte do Zenkai. Tira os bonus Zenkai_ (permanentes, o temporario e os da 2.1), a liberacao extra
	 * e a recarga, e devolve os usos permanentes. Nao toca em stats base, nivel, formas, maestria, saga, itens, TP, money,
	 * nem nas assimilacoes, absorcoes e Cell Jr. (de quem trocou de raca), que o {@link #reset} completo apagaria.
	 */
	public static void resetZenkai(ServerPlayer player, StatsData data) {
		RacialData racial = data.getRacialData();
		BonusStats bonusStats = data.getBonusStats();
		for (String stat : BONUS_KEYS) {
			for (BonusStats.StatBonus bonus : bonusStats.getBonuses(stat)) {
				if (bonus.name.startsWith(ZENKAI_BONUS_PREFIX)) bonusStats.removeBonus(stat, bonus.name);
			}
		}
		racial.getOwnedBonusNames().removeIf(name -> name.startsWith(ZENKAI_BONUS_PREFIX));
		racial.setZenkaiPermanentUses(0);
		racial.setZenkaiReleaseBonus(0.0);
		racial.setTempZenkaiBuffName("");

		data.getCooldowns().removeCooldown(Cooldowns.ZENKAI);
		data.getCooldowns().removeCooldown(Cooldowns.ZENKAI_TEMP_BUFF);
		player.removeEffect(MainEffects.SAIYAN_PASSIVE.get());
		// O contador da 2.1 era o numero de Zenkais do Saiyajin; em outra raca ele conta a captura dela e fica.
		if (usesSaiyanPassive(data)) data.getResources().setRacialSkillCount(0);
	}

	private static boolean usesSaiyanPassive(StatsData data) {
		String race = data.getCharacter().getRace();
		if (race == null || race.isEmpty()) return false;
		RaceCharacterConfig raceConfig = ConfigManager.getRaceCharacter(race);
		return raceConfig != null && "saiyan".equals(raceConfig.getRacialSkill());
	}

	private static boolean hasZenkaiBonus(BonusStats bonusStats) {
		for (String stat : BONUS_KEYS) {
			for (BonusStats.StatBonus bonus : bonusStats.getBonuses(stat)) {
				if (bonus.name.startsWith(ZENKAI_BONUS_PREFIX)) return true;
			}
		}
		return false;
	}

	private static void clearLegacyBonuses(StatsData data) {
		String[] statBoosts = switch (data.getCharacter().getRace()) {
			case "namekian" -> ConfigManager.getServerConfig().getRacialSkills().getNamekianAssimilationBoosts();
			case "majin" -> ConfigManager.getServerConfig().getRacialSkills().getMajinAbsorptionBoosts();
			case "saiyan" -> ConfigManager.getServerConfig().getRacialSkills().getSaiyanZenkaiBoosts();
			default -> new String[0];
		};

		for (String stat : statBoosts) {
			for (int i = data.getResources().getRacialSkillCount(); i >= 0; i--) {
				data.getBonusStats().clearBonusSplit(stat, "Absorption_");
				data.getBonusStats().clearBonusSplit(stat, "Assimilation_");
				data.getBonusStats().clearBonusSplit(stat, "Zenkai_");
			}
		}
	}
}
