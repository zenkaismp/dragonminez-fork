package com.dragonminez.common.wish.wishes;

import com.dragonminez.common.racial.RacialReset;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.wish.Wish;
import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerPlayer;

public class PassiveResetWish extends Wish {

	public PassiveResetWish(String name, String description) {
		super(name, description, "passivereset");
	}

	@Override
	public void grant(ServerPlayer player) {
		StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> RacialReset.reset(player, data));
	}

	@Override
	public String toJson() {
		return new GsonBuilder().setPrettyPrinting().create().toJson(this, PassiveResetWish.class);
	}
}
