package com.dragonminez.server.storage;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public interface IDataStorage {
	void init();
	void shutdown();

	CompoundTag loadData(UUID playerUUID);

	/**
	 * Same read as {@link #loadData}, but able to say WHY it came back empty — see {@link LoadResult}.
	 * Callers that decide whether a player is new (character creation) must use this one; the default
	 * below cannot tell an error from an absent row, so every backend should override it.
	 */
	default LoadResult load(UUID playerUUID) {
		CompoundTag data = loadData(playerUUID);
		return data != null ? LoadResult.loaded(data) : LoadResult.absent();
	}

	boolean saveData(UUID playerUUID, String playerName, CompoundTag data);

	String getName();
}
