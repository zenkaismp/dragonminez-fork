package com.dragonminez.common.racial.impl;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.RaceCharacterConfig;

// Only the piece of the v2.2 GlindDivinity that the 3D auras use: the god aura belongs to races whose character config
// has racialSkill "glind". The rest of the v2.2 racial system (passives, divine ki, RacialAbility) is not in this fork.
public class GlindDivinity {
	public static final String ID = "glind";

	public static boolean grantsGodAura(String raceName) {
		RaceCharacterConfig race = ConfigManager.getRaceCharacter(raceName);
		return race != null && ID.equals(race.getRacialSkill());
	}
}
