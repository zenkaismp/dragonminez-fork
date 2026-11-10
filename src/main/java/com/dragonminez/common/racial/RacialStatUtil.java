package com.dragonminez.common.racial;

import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.BonusStats;
import net.minecraft.server.level.ServerPlayer;

public final class RacialStatUtil {

	private RacialStatUtil() {
	}

	public static int getStat(StatsData data, String statName) {
		return switch (statName) {
			case "STR" -> data.getStats().getStrength();
			case "SKP" -> data.getStats().getStrikePower();
			case "RES", "DEF", "STM" -> data.getStats().getResistance();
			case "VIT" -> data.getStats().getVitality();
			case "PWR" -> data.getStats().getKiPower();
			case "ENE" -> data.getStats().getEnergy();
			default -> 0;
		};
	}

	/**
	 * Zenkai (P4 do pacote de passivas): bonus em porcentagem da base ATUAL do stat, recalculado a cada conta, no lugar do
	 * {@code (int) max(1, stat x pct)} gravado na hora do ganho (que perdia valor conforme o stat subia).
	 *
	 * <p>Grava na operacao "*" com multiplicadores, que o BonusStats sempre conheceu: o formato salvo e o mesmo de antes,
	 * e um jar velho calcula esse bonus igual ao novo. Pra soma das vagas da mesma passiva sair exata (2,2% + 2,2% = 4,4% da
	 * base, e nao 4,45%), o fator gravado nesta vaga e {@code (T + pct) / T}, onde T e o produto dos fatores "*" que a
	 * passiva ja tem nesse stat (os nomes com {@code slotPrefix}). Assim o produto das vagas e sempre 1 + a soma das
	 * porcentagens. A conta vale enquanto as vagas saem na ordem inversa da entrada, que e o que o jogo faz (a morte do Namek
	 * tira a ultima assimilacao, o Zenkai temporario e o ultimo a entrar, e os resets tiram todas). Os bonus "+" antigos da
	 * mesma passiva ficam como estao, somados por fora.</p>
	 *
	 * @return o bonus em pontos que esta vaga vale agora (so informativo, pro registro da vaga)
	 */
	public static int addPercentBonus(StatsData data, String stat, String bonusName, String slotPrefix, double pct) {
		if (!(pct > 0.0) || !Double.isFinite(pct)) return 0;
		BonusStats bonusStats = data.getBonusStats();
		String[] keys = "RES".equalsIgnoreCase(stat) ? new String[]{"DEF", "STM"} : new String[]{stat.toUpperCase()};
		for (String key : keys) {
			double product = 1.0;
			for (BonusStats.StatBonus bonus : bonusStats.getBonuses(key)) {
				if (!"*".equals(bonus.operation) || !bonus.applyMultipliers) continue;
				if (!bonus.name.startsWith(slotPrefix) || bonus.name.equals(bonusName)) continue;
				product *= bonus.value;
			}
			if (!(product > 0.0) || !Double.isFinite(product)) product = 1.0;
			bonusStats.addBonus(key, bonusName, "*", (product + pct) / product, true);
		}
		return (int) Math.min(Integer.MAX_VALUE, Math.round(getStat(data, stat) * pct));
	}

	public static int applyTechniqueXpBonus(StatsData data, int amount) {
		if (amount <= 0 || !(data.getPlayer() instanceof ServerPlayer serverPlayer)) return amount;
		return RacialRegistry.forPlayer(data)
				.map(ability -> (int) Math.round(ability.modifyTechniqueXpGain(new RacialContext(serverPlayer, data), amount)))
				.orElse(amount);
	}

	public static float applyTechniqueXpBonus(StatsData data, float amount) {
		if (amount <= 0 || !(data.getPlayer() instanceof ServerPlayer serverPlayer)) return amount;
		return RacialRegistry.forPlayer(data)
				.map(ability -> (float) ability.modifyTechniqueXpGain(new RacialContext(serverPlayer, data), amount))
				.orElse(amount);
	}
}
