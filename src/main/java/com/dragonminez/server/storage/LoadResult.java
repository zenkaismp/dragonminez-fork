package com.dragonminez.server.storage;

import net.minecraft.nbt.CompoundTag;

/**
 * Outcome of reading one player from storage.
 *
 * <p>Exists because {@code null} used to mean four different things — no connection, no row, SQL
 * error, corrupt NBT — and only ONE of them ({@link Status#ABSENT}) means "this player is new".
 * Treating the others as new opens character creation on top of data that exists but could not be
 * read, and the first save then overwrites it. The three states below are what makes that call
 * safe.</p>
 */
public record LoadResult(Status status, CompoundTag data) {

	public enum Status {
		/** Row found and decoded — {@link #data()} is non-null. */
		LOADED,
		/** Storage answered, and this player genuinely has no record yet. */
		ABSENT,
		/** Storage could not answer (offline, SQL error, corrupt payload). Says NOTHING about the player. */
		FAILED
	}

	private static final LoadResult ABSENT_RESULT = new LoadResult(Status.ABSENT, null);
	private static final LoadResult FAILED_RESULT = new LoadResult(Status.FAILED, null);

	public static LoadResult loaded(CompoundTag data) {
		return new LoadResult(Status.LOADED, data);
	}

	public static LoadResult absent() {
		return ABSENT_RESULT;
	}

	public static LoadResult failed() {
		return FAILED_RESULT;
	}

	public boolean isLoaded() {
		return status == Status.LOADED && data != null;
	}

	public boolean isFailed() {
		return status == Status.FAILED;
	}
}
