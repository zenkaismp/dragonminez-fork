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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public final class RacialReset {
	private static final String ABSORPTION_SLOT_COOLDOWN = "AbsorptionSlot_";
	private static final String CELL_JR_SLOT_COOLDOWN = "CellJrSlot_";
	// Todo bonus do Zenkai tem este prefixo: os permanentes (Zenkai_1..3), o temporario (Zenkai_Temp_<tempo>) e os da 2.1.
	public static final String ZENKAI_BONUS_PREFIX = "Zenkai_";
	// Zenkai (Parte 8 do docs/95): os bonus das vagas da assimilacao (Namek) e da absorcao (Majin) tem estes prefixos.
	public static final String ASSIMILATION_BONUS_PREFIX = "Assimilation_";
	public static final String ABSORPTION_BONUS_PREFIX = "Absorption_";
	private static final String NAMEK_RESET_MESSAGE = "message.dragonminez.racial.namek.reset";
	private static final String NAMEK_RESET_FALLBACK = "\u00a7eYour old assimilations were cleared. The slots are free again, with the new values.";
	private static final String MAJIN_RESET_MESSAGE = "message.dragonminez.racial.majin.reset";
	private static final String MAJIN_RESET_FALLBACK = "\u00a7eYour old absorptions were cleared. The slots are free again, with the new values.";
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

	/**
	 * Os resets unicos que o login e a carga dos dados (PlayerDataAppliedEvent) rodam: o dos Zenkais (P10) e os da Parte 8
	 * do docs/95 (assimilacoes do Namek e absorcoes do Majin). Cada um tem a propria chave e a propria marca, e com a chave
	 * em 0 nao faz nada.
	 */
	public static void applyRacialResetsIfDue(ServerPlayer player, StatsData data) {
		applyZenkaiResetIfDue(player, data);
		applyAssimilationResetIfDue(player, data);
		applyAbsorptionResetIfDue(player, data);
	}

	/**
	 * Zenkai (Parte 8 do docs/95): o reset unico das assimilacoes, no molde do {@link #applyZenkaiResetIfDue}. Com
	 * namekian.assimilationResetVersion em 0 (o padrao) nao faz nada e nao grava nada. Com a chave acima da marca do jogador
	 * e a raca dele usando a passiva namekian, tira todas as assimilacoes ({@link #resetAssimilations}), grava a marca, loga
	 * e, se havia o que tirar, avisa o jogador. Devolve true quando resetou.
	 */
	public static boolean applyAssimilationResetIfDue(ServerPlayer player, StatsData data) {
		int target = ConfigManager.getServerConfig().getRacialSkills().getNamekian().getAssimilationResetVersion();
		if (target <= 0) return false;
		if (!data.getStatus().isHasCreatedCharacter()) return false;

		RacialData racial = data.getRacialData();
		if (racial.getAssimilationResetVersion() >= target) return false;
		if (!usesPassive(data, "namekian")) return false;

		boolean cleared = resetAssimilations(data);
		racial.setAssimilationResetVersion(target);
		StorageManager.markDirty(player.getUUID());
		LogUtil.info(Env.SERVER, "[Namek] reset: {}", player.getGameProfile().getName());
		if (cleared) player.sendSystemMessage(Component.translatableWithFallback(NAMEK_RESET_MESSAGE, NAMEK_RESET_FALLBACK));
		return true;
	}

	/**
	 * Zenkai (Parte 8 do docs/95): o reset unico das absorcoes, no molde do {@link #applyZenkaiResetIfDue}. Com
	 * majin.absorptionResetVersion em 0 (o padrao) nao faz nada e nao grava nada. Com a chave acima da marca do jogador e a
	 * raca dele usando a passiva majin, tira todas as absorcoes ({@link #resetAbsorptions}), grava a marca, loga e, se havia
	 * o que tirar, avisa o jogador. Devolve true quando resetou.
	 */
	public static boolean applyAbsorptionResetIfDue(ServerPlayer player, StatsData data) {
		int target = ConfigManager.getServerConfig().getRacialSkills().getMajin().getAbsorptionResetVersion();
		if (target <= 0) return false;
		if (!data.getStatus().isHasCreatedCharacter()) return false;

		RacialData racial = data.getRacialData();
		if (racial.getAbsorptionResetVersion() >= target) return false;
		if (!usesPassive(data, "majin")) return false;

		boolean cleared = resetAbsorptions(data);
		racial.setAbsorptionResetVersion(target);
		StorageManager.markDirty(player.getUUID());
		LogUtil.info(Env.SERVER, "[Majin] reset: {}", player.getGameProfile().getName());
		if (cleared) player.sendSystemMessage(Component.translatableWithFallback(MAJIN_RESET_MESSAGE, MAJIN_RESET_FALLBACK));
		return true;
	}

	/**
	 * Zenkai (Parte 8): tira todas as assimilacoes, cada uma do jeito que o NamekAssimilation.onDeath tira a ultima (os bonus
	 * da vaga e o nome dela), e depois o que sobrou com o prefixo Assimilation_ (vaga da 2.1 que o migrador nao registrou
	 * inteira). Faz a conta aqui e nao chama o onDeath, que o ZenkaiExtra pode cancelar (AbsorcaoForaDaLutaMixin). O
	 * contador da 2.1 zera, como no {@link #resetZenkai}. Nao toca em stats base, nivel, formas, maestria, saga, itens, TP,
	 * money, nos Zenkais nem nas vagas de outra raca. Devolve true se havia vaga ou bonus pra tirar.
	 */
	public static boolean resetAssimilations(StatsData data) {
		RacialData racial = data.getRacialData();
		List<RacialData.AssimilationSlot> slots = racial.getAssimilations();
		boolean cleared = !slots.isEmpty();
		for (RacialData.AssimilationSlot slot : slots) {
			for (String stat : slot.grantedStats().keySet()) data.getBonusStats().removeBonusSplit(stat, slot.bonusName());
			racial.removeOwnedBonusName(slot.bonusName());
		}
		slots.clear();
		cleared |= removeBonusesWithPrefix(data, ASSIMILATION_BONUS_PREFIX);
		data.getResources().setRacialSkillCount(0);
		return cleared;
	}

	/**
	 * Zenkai (Parte 8): tira todas as absorcoes do jeito que o MajinAbsorption.onDeath tira (os bonus de cada vaga e o nome
	 * dela), e depois o que sobrou com o prefixo Absorption_. As travas de vaga do eject (AbsorptionSlot_) saem junto, pra
	 * as vagas ficarem livres de verdade; o contador de nomes (absorptionSlotCounter) fica, como no onDeath. O contador da
	 * 2.1 zera. Nao toca em stats base, nivel, formas, maestria, saga, itens, TP, money nem nas vagas de outra raca. Devolve
	 * true se havia vaga ou bonus pra tirar.
	 */
	public static boolean resetAbsorptions(StatsData data) {
		RacialData racial = data.getRacialData();
		List<RacialData.AbsorptionSlot> slots = racial.getAbsorptions();
		boolean cleared = !slots.isEmpty();
		for (RacialData.AbsorptionSlot slot : slots) {
			for (String stat : slot.grantedStats().keySet()) data.getBonusStats().removeBonusSplit(stat, slot.bonusName());
			racial.removeOwnedBonusName(slot.bonusName());
		}
		slots.clear();
		cleared |= removeBonusesWithPrefix(data, ABSORPTION_BONUS_PREFIX);
		Cooldowns cooldowns = data.getCooldowns();
		for (String key : cooldowns.getAllCooldowns().keySet()) {
			if (key.startsWith(ABSORPTION_SLOT_COOLDOWN)) cooldowns.removeCooldown(key);
		}
		data.getResources().setRacialSkillCount(0);
		return cleared;
	}

	// Tira de todos os stats os bonus cujo nome comeca com o prefixo, e os nomes da lista de donos. Devolve true se tirou algum.
	private static boolean removeBonusesWithPrefix(StatsData data, String prefix) {
		BonusStats bonusStats = data.getBonusStats();
		boolean removed = false;
		for (String stat : BONUS_KEYS) {
			for (BonusStats.StatBonus bonus : bonusStats.getBonuses(stat)) {
				if (!bonus.name.startsWith(prefix)) continue;
				bonusStats.removeBonus(stat, bonus.name);
				removed = true;
			}
		}
		data.getRacialData().getOwnedBonusNames().removeIf(name -> name.startsWith(prefix));
		return removed;
	}

	private static boolean usesSaiyanPassive(StatsData data) {
		return usesPassive(data, "saiyan");
	}

	// A raca do jogador usa a passiva racial dada (o racialSkill do character.json da raca).
	private static boolean usesPassive(StatsData data, String racialSkill) {
		String race = data.getCharacter().getRace();
		if (race == null || race.isEmpty()) return false;
		RaceCharacterConfig raceConfig = ConfigManager.getRaceCharacter(race);
		return raceConfig != null && racialSkill.equals(raceConfig.getRacialSkill());
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
