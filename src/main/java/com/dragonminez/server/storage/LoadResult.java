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
 *
 * <p><b>{@link #rev()} é a revisão do registro lido</b> e o que torna a escrita segura numa REDE.
 * O save volta como {@code UPDATE ... WHERE rev = <a que eu li>}: se outro backend gravou nesse
 * meio tempo a condição falha, e o dado velho simplesmente não entra. Sem isso o save era
 * {@code ON DUPLICATE KEY UPDATE} incondicional, ou seja, quem escrevesse por último ganhava —
 * e numa troca de servidor quem escreve por último costuma ser quem tem o dado MAIS VELHO.</p>
 */
public record LoadResult(Status status, CompoundTag data, long rev) {

	public enum Status {
		/** Row found and decoded — {@link #data()} is non-null. */
		LOADED,
		/** Storage answered, and this player genuinely has no record yet. */
		ABSENT,
		/** Storage could not answer (offline, SQL error, corrupt payload). Says NOTHING about the player. */
		FAILED
	}

	private static final LoadResult ABSENT_RESULT = new LoadResult(Status.ABSENT, null, 0L);
	private static final LoadResult FAILED_RESULT = new LoadResult(Status.FAILED, null, 0L);

	/** Backend sem revisão (JSON/NBT): rev 0 desliga o CAS e mantém o comportamento antigo. */
	public static LoadResult loaded(CompoundTag data) {
		return new LoadResult(Status.LOADED, data, 0L);
	}

	public static LoadResult loaded(CompoundTag data, long rev) {
		return new LoadResult(Status.LOADED, data, rev);
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
