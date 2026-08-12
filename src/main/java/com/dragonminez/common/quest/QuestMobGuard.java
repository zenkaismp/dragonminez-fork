package com.dragonminez.common.quest;

import com.dragonminez.Reference;
import com.dragonminez.server.world.data.PartySavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mob de quest e PESSOAL: so o elenco carimbado no spawn pode encostar nele.
 *
 * <h2>O problema que isto fecha</h2>
 * Qualquer um conseguia matar o NPC da saga dos outros: griefing (matar o boss do coleguinha) e
 * carry gratis (um forte mata o mob calibrado pro fraco sem pagar o preco da party). A missao e
 * individual; a unica ajuda legitima e a party, que paga a escala de vida por membro e passa
 * pelo gate de nivel.
 *
 * <h2>O carimbo ({@code dmz_quest_party})</h2>
 * A autorizacao NAO e "a party viva do dono": e o CSV de UUIDs gravado no mob NO SPAWN. A vida
 * do mob foi multiplicada pelo tamanho DAQUELE elenco, entao e aquele elenco que tem direito ao
 * mob. Sem o carimbo, spawnar solo (1x de vida) e convidar tres amigos depois zerava a escala.
 * Quem entra na party no meio da luta ve a mensagem certa: mate ou re-invoque pra reescalar.
 * Mob de save antigo (sem carimbo) cai na party viva, comportamento de antes.
 *
 * <h2>Dano que nao vem de player</h2>
 * Cancelado tambem (TNT, cristal do End, lava, golem, fogo): mob calibrado morre pra dano de
 * jogador autorizado, e ambiente e exatamente o cheese que o balanceamento quer fora. {@code
 * /kill} e void passam ({@code BYPASSES_INVULNERABILITY}), entao a staff nunca fica presa.
 *
 * <h2>Cobertura</h2>
 * {@link LivingAttackEvent} em HIGHEST cobre todo caminho que passa pelo {@code hurt()} (melee,
 * flecha, blast de ki com dono resolvido, AoE). Os dois casos que NAO passam por ali tem
 * tratamento proprio: a transformacao forcada do {@code DBSagasEntity} posta o evento a mao, e
 * os grabs (Dragon Fist / Oozaru Fist) consultam {@link #isBlocked} antes de segurar o alvo.
 * Cancelar aqui tambem impede que o hit de um estranho arme o relogio do
 * {@link QuestOvertimeRegen} (que escuta o {@code LivingHurtEvent}, que nem chega a nascer).
 *
 * <p>Custo: um {@code getString()} de NBT por evento de ataque em entidade viva; mob sem dono
 * sai na primeira comparacao.</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuestMobGuard {

	/** CSV dos UUIDs autorizados, gravado pelo {@link QuestService} no spawn. */
	public static final String PARTY_STAMP_TAG = "dmz_quest_party";

	/** Debounce do aviso por atacante (ticks). Em mapa, nunca em NBT: persistentData vaza pro .dat. */
	private static final long WARN_EVERY_TICKS = 60L;
	private static final Map<UUID, Long> WARN_AT = new HashMap<>(); // main thread only

	private QuestMobGuard() {
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onAttack(LivingAttackEvent event) {
		LivingEntity mob = event.getEntity();
		if (mob.level().isClientSide()) {
			return;
		}
		String ownerRaw = mob.getPersistentData().getString(QuestService.QUEST_OWNER_TAG);
		if (ownerRaw.isEmpty()) {
			return; // mob comum do mundo: fora do escopo
		}

		Entity src = event.getSource().getEntity();
		if (src instanceof ServerPlayer attacker) {
			if (isAuthorized(mob, attacker, ownerRaw)) {
				return;
			}
			event.setCanceled(true);
			warn(attacker, mob, ownerRaw);
			return;
		}

		// Fonte sem player: TNT, cristal, lava, fogo, golem "emprestado". Tudo barrado, sem
		// aviso (nao ha quem avisar). /kill e void passam pra staff nunca ficar presa.
		if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return;
		}
		event.setCanceled(true);
	}

	/**
	 * {@code true} = este atacante NAO pode encostar neste mob. E a mesma regra do evento,
	 * exposta pros caminhos que nao passam pelo {@code hurt()} (os grabs das tecnicas de punho,
	 * que seguravam e arrastavam o mob mesmo com o dano cancelado).
	 */
	public static boolean isBlocked(LivingEntity mob, Entity attacker) {
		if (mob.level().isClientSide()) {
			return false;
		}
		String ownerRaw = mob.getPersistentData().getString(QuestService.QUEST_OWNER_TAG);
		if (ownerRaw.isEmpty()) {
			return false;
		}
		if (!(attacker instanceof ServerPlayer p)) {
			return false;
		}
		return !isAuthorized(mob, p, ownerRaw);
	}

	/** O CSV do carimbo contem este UUID? Usado tambem pelo credito de kill (QuestEvents). */
	public static boolean stampContains(String stamp, String uuid) {
		for (String s : stamp.split(",")) {
			if (s.equals(uuid)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isAuthorized(LivingEntity mob, ServerPlayer attacker, String ownerRaw) {
		String attackerId = attacker.getStringUUID();
		if (attackerId.equals(ownerRaw)) {
			return true;
		}
		String stamp = mob.getPersistentData().getString(PARTY_STAMP_TAG);
		if (!stamp.isEmpty()) {
			return stampContains(stamp, attackerId); // fora do elenco do spawn = nao, mesmo na party de hoje
		}
		// Mob legado, sem carimbo: cai na party VIVA do dono (via SavedData, dono pode estar
		// offline). Comportamento de transicao; todo mob novo nasce carimbado.
		try {
			UUID ownerId = UUID.fromString(ownerRaw);
			PartySavedData parties = PartySavedData.get(attacker.getServer());
			PartySavedData.PartyInstance ownerParty = parties.getPartyOf(ownerId);
			PartySavedData.PartyInstance attackerParty = parties.getPartyOf(attacker.getUUID());
			return ownerParty != null && attackerParty != null
					&& ownerParty.getPartyId().equals(attackerParty.getPartyId());
		} catch (IllegalArgumentException e) {
			return true; // tag corrompido: comporta como antes do guard existir
		}
	}

	private static void warn(ServerPlayer attacker, LivingEntity mob, String ownerRaw) {
		long now = attacker.level().getGameTime();
		Long last = WARN_AT.get(attacker.getUUID());
		if (last != null && now - last < WARN_EVERY_TICKS) {
			return;
		}
		WARN_AT.put(attacker.getUUID(), now);

		// Quem esta na party do dono HOJE mas fora do carimbo entrou depois do spawn: a
		// mensagem generica ("party up!") seria um insulto, ele JA esta na party.
		boolean entrouDepois = false;
		try {
			PartySavedData parties = PartySavedData.get(attacker.getServer());
			PartySavedData.PartyInstance ownerParty = parties.getPartyOf(UUID.fromString(ownerRaw));
			PartySavedData.PartyInstance attackerParty = parties.getPartyOf(attacker.getUUID());
			entrouDepois = ownerParty != null && attackerParty != null
					&& ownerParty.getPartyId().equals(attackerParty.getPartyId());
		} catch (IllegalArgumentException ignored) {
		}
		if (entrouDepois) {
			attacker.sendSystemMessage(Component.literal(
							"This fight started before you joined the party. Re-summon it to fight together!")
					.withStyle(ChatFormatting.RED));
		} else {
			attacker.sendSystemMessage(Component.literal(
							"This enemy belongs to another player's quest. Party up to fight together!")
					.withStyle(ChatFormatting.RED));
		}
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		WARN_AT.remove(event.getEntity().getUUID());
	}
}
