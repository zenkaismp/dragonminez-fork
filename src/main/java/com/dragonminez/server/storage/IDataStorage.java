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

	/**
	 * Grava SO se o registro ainda estiver na revisao {@code expectedRev} — o compare-and-swap
	 * que impede um backend de sobrescrever dado mais novo de outro.
	 *
	 * <p>Devolve {@link SaveOutcome#CONFLICT} quando a revisao no banco ja avancou. Isso NAO e
	 * erro de infraestrutura: e exatamente a protecao funcionando, e o certo e nao insistir —
	 * o dado em memoria deste servidor esta velho.</p>
	 *
	 * <p>O default ignora a revisao e cai no {@link #saveData} de sempre, pra backend sem
	 * suporte a revisao (JSON/NBT local) continuar funcionando igual.</p>
	 */
	default SaveOutcome saveData(UUID playerUUID, String playerName, CompoundTag data, long expectedRev) {
		return saveData(playerUUID, playerName, data)
				? SaveOutcome.ok(Math.max(0L, expectedRev) + 1)
				: SaveOutcome.failed();
	}

	/**
	 * Revisao ATUAL do registro no storage: -1 = nao existe, -2 = nao suportado/falhou.
	 * Usada pra ressincronizar depois de um CONFLICT — sem isto o conflito era terminal e a
	 * sessao inteira parava de salvar.
	 */
	default long fetchRev(UUID playerUUID) {
		return -2L;
	}

	/** Resultado do save com revisao: OK (com a revisao nova), CONFLICT (dado velho) ou FAILED. */
	record SaveOutcome(Kind kind, long newRev) {
		public enum Kind { OK, CONFLICT, FAILED }

		public static SaveOutcome ok(long newRev) { return new SaveOutcome(Kind.OK, newRev); }
		public static SaveOutcome conflict() { return new SaveOutcome(Kind.CONFLICT, 0L); }
		public static SaveOutcome failed() { return new SaveOutcome(Kind.FAILED, 0L); }

		public boolean isOk() { return kind == Kind.OK; }
		public boolean isConflict() { return kind == Kind.CONFLICT; }
	}

	String getName();
}
