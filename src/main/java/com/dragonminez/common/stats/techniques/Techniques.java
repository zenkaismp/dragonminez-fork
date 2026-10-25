package com.dragonminez.common.stats.techniques;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.HashMap;
import java.util.Map;

public class Techniques {
	public static final int SLOT_COUNT = 8;
	public static final int MAX_UNLOCKED_TECHNIQUES = 256;

	@Getter
	private final Map<String, TechniqueData> unlockedTechniques = new HashMap<>();
	private final String[] equippedSlots = new String[SLOT_COUNT];
	private int selectedSlot = 0;
	private String chargingTechniqueId = "";
	private float techniqueChargePercent = 0.0f;
	@Setter
	private boolean techniqueCharging = false;
	@Setter
	private boolean chargeHolding = false;

	@Getter
	@Setter
	private transient int homingTargetId = -1;

	private final transient Map<String, Float> fractionalXpRemainder = new HashMap<>();

	// Zenkai: a devolucao da XP dos niveis do golpe de ki que virou esquiva (id -> {niveis, XP devolvida}), para o aviso
	// no chat (TechniqueMigrationRefund). Fica so na memoria: o NBT salvo depois da migracao ja e da esquiva, entao a
	// devolucao nao se repete. Cada load comeca do zero (o dado de antes foi trocado).
	private final transient Map<String, int[]> migrationRefunds = new HashMap<>();

	public Map<String, int[]> takeMigrationRefunds() {
		if (migrationRefunds.isEmpty()) return Map.of();
		Map<String, int[]> taken = new HashMap<>(migrationRefunds);
		migrationRefunds.clear();
		return taken;
	}

	/**
	 * v2.2: o golpe de ki salvo que agora e esquiva (o Solar Flare) carrega como a esquiva do registro, so com a XP; os
	 * niveis de melhoria de golpe de ki somem. Zenkai: a XP que esses niveis custaram volta para a tecnica, pela formula de
	 * custo do fork ({@link KiAttackData#getSpentUpgradeXp}, com o config do tipo do golpe salvo). Puro (o config vem de
	 * fora) para o teste. {@code refundOut}, quando vem, recebe {niveis, XP devolvida}.
	 */
	public static EvasionAttackData migrateKiToEvasion(CompoundTag oldKiTag, EvasionAttackData template,
			com.dragonminez.common.config.TechniqueConfig.TechniqueTypeConfig costConfig, int[] refundOut) {
		EvasionAttackData migrated = new EvasionAttackData();
		migrated.load(template.save());
		int experience = oldKiTag.contains("Experience") ? oldKiTag.getInt("Experience") : 0;
		KiAttackData old = KiAttackData.upgradeSnapshot(oldKiTag);
		int levels = old.getUpgradeLevelCount();
		int refund = levels > 0 && costConfig != null ? old.getSpentUpgradeXp(costConfig) : 0;
		migrated.setExperience((int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, (long) experience + refund)));
		if (refundOut != null && refundOut.length >= 2) {
			refundOut[0] = levels;
			refundOut[1] = refund;
		}
		return migrated;
	}

	private static com.dragonminez.common.config.TechniqueConfig.TechniqueTypeConfig refundCostConfig(CompoundTag oldKiTag) {
		com.dragonminez.common.config.TechniqueConfig config = com.dragonminez.common.config.ConfigManager.getTechniqueConfig();
		return config != null ? config.getKiTypeConfig(KiAttackData.upgradeSnapshot(oldKiTag).getKiType()) : null;
	}

	public Techniques() {
		for (int i = 0; i < SLOT_COUNT; i++) equippedSlots[i] = "";
	}

	public static TechniqueData copyOf(TechniqueData source) {
		TechniqueData clone = source instanceof KiAttackData ? new KiAttackData()
				: source instanceof EvasionAttackData ? new EvasionAttackData() : new StrikeAttackData();
		clone.load(source.save());
		return clone;
	}

	public void unlockTechnique(TechniqueData data) {
		if (!unlockedTechniques.containsKey(data.getId()) && unlockedTechniques.size() >= MAX_UNLOCKED_TECHNIQUES) return;
		unlockedTechniques.put(data.getId(), data);
	}

	public void equipTechnique(int slotIndex, String techniqueId) {
		if (slotIndex >= 0 && slotIndex < SLOT_COUNT && unlockedTechniques.containsKey(techniqueId)) {
			equippedSlots[slotIndex] = techniqueId;
		}
	}

	public void removeTechnique(String techniqueId) {
		unlockedTechniques.remove(techniqueId);
		for (int i = 0; i < SLOT_COUNT; i++) {
			if (equippedSlots[i].equals(techniqueId)) {
				equippedSlots[i] = "";
				if (selectedSlot == i) selectedSlot = 0;
			}
		}
		if (chargingTechniqueId.equals(techniqueId)) clearTechniqueCharge();
	}

	public void selectSlot(int slotIndex) {
		if (slotIndex >= 0 && slotIndex < SLOT_COUNT) {
			this.selectedSlot = slotIndex;
		}
	}

	public TechniqueData getSelectedTechnique() {
		String id = equippedSlots[selectedSlot];
		return id.isEmpty() ? null : unlockedTechniques.get(id);
	}

	public String[] getEquippedSlots() {
		return this.equippedSlots;
	}

	public String getChargingTechniqueId() {
		return chargingTechniqueId;
	}

	public float getTechniqueChargePercent() {
		return techniqueChargePercent;
	}

	public boolean isTechniqueCharging() {
		return techniqueCharging;
	}

	public boolean isTechniqueChargeActive() {
		return !chargingTechniqueId.isEmpty() && techniqueChargePercent > 0.0f;
	}

	public void startTechniqueCharge(String techniqueId) {
		if (techniqueId == null || techniqueId.isEmpty()) return;
		if (!techniqueId.equals(this.chargingTechniqueId)) {
			this.chargingTechniqueId = techniqueId;
			this.techniqueChargePercent = 0.0f;
		}
		this.techniqueCharging = true;
		this.chargeHolding = true;
	}

	public void setTechniqueChargePercent(float percent) {
		this.techniqueChargePercent = Math.max(0.0f, Math.min(200.0f, percent));
	}

	public boolean isChargeHolding() {
		return chargeHolding;
	}

	public void clearTechniqueCharge() {
		this.chargingTechniqueId = "";
		this.techniqueChargePercent = 0.0f;
		this.techniqueCharging = false;
		this.chargeHolding = false;
		this.homingTargetId = -1;
	}

	public void clearAllTechniques() {
		this.unlockedTechniques.clear();
		for (int i = 0; i < this.equippedSlots.length; i++) {
			this.equippedSlots[i] = "";
		}
		this.selectedSlot = 0;
		clearTechniqueCharge();
	}

	public int getSelectedSlot() {
		return this.selectedSlot;
	}

	public void equipOrSwapTechnique(int slotIndex, String techniqueId) {
		if (slotIndex < 0 || slotIndex >= SLOT_COUNT) return;

		boolean isEmpty = techniqueId == null || techniqueId.isEmpty();
		if (!isEmpty && !unlockedTechniques.containsKey(techniqueId)) return;

		int existingSlot = -1;
		for (int i = 0; i < SLOT_COUNT; i++) {
			if (equippedSlots[i].equals(techniqueId)) {
				existingSlot = i;
				break;
			}
		}

		if (existingSlot != -1) {
			String temp = equippedSlots[slotIndex];
			equippedSlots[slotIndex] = techniqueId;
			equippedSlots[existingSlot] = temp;
		} else equippedSlots[slotIndex] = techniqueId;
	}

	public void addExperienceToSelected(int amount) {
		TechniqueData active = getSelectedTechnique();
		if (active != null) active.addExperience(amount);
	}

	public void addExperienceToTechnique(String id, int amount) {
		if (unlockedTechniques.containsKey(id)) {
			unlockedTechniques.get(id).addExperience(amount);
		}
	}

	public void addFractionalExperienceToTechnique(String id, float amount) {
		if (amount <= 0.0f || !unlockedTechniques.containsKey(id)) return;
		float total = fractionalXpRemainder.merge(id, amount, Float::sum);
		int whole = (int) total;
		if (whole > 0) {
			unlockedTechniques.get(id).addExperience(whole);
			fractionalXpRemainder.put(id, total - whole);
		}
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("SelectedSlot", selectedSlot);
		tag.putString("ChargingTechniqueId", chargingTechniqueId);
		tag.putFloat("TechniqueChargePercent", techniqueChargePercent);
		tag.putBoolean("TechniqueCharging", techniqueCharging);
		tag.putBoolean("ChargeHolding", chargeHolding);

		CompoundTag slotsTag = new CompoundTag();
		for (int i = 0; i < SLOT_COUNT; i++) {
			slotsTag.putString("Slot" + i, equippedSlots[i]);
		}
		tag.put("EquippedSlots", slotsTag);

		ListTag unlockedTag = new ListTag();
		for (TechniqueData tech : unlockedTechniques.values()) {
			CompoundTag techTag = tech.save();
			techTag.putString("TechClassType", tech instanceof KiAttackData ? "KI"
					: tech instanceof EvasionAttackData ? "EVASION" : "STRIKE");
			unlockedTag.add(techTag);
		}
		tag.put("UnlockedTechniques", unlockedTag);

		return tag;
	}

	public void load(CompoundTag tag) {
		this.selectedSlot = tag.getInt("SelectedSlot");
		this.chargingTechniqueId = tag.getString("ChargingTechniqueId");
		this.techniqueChargePercent = Math.max(0.0f, Math.min(200.0f, tag.getFloat("TechniqueChargePercent")));
		this.techniqueCharging = tag.getBoolean("TechniqueCharging");
		this.chargeHolding = tag.getBoolean("ChargeHolding");

		CompoundTag slotsTag = tag.getCompound("EquippedSlots");
		for (int i = 0; i < SLOT_COUNT; i++) {
			this.equippedSlots[i] = slotsTag.getString("Slot" + i);
		}

		this.unlockedTechniques.clear();
		this.migrationRefunds.clear();
		ListTag unlockedTag = tag.getList("UnlockedTechniques", 10);
		for (int i = 0; i < unlockedTag.size(); i++) {
			CompoundTag techTag = unlockedTag.getCompound(i);
			String type = techTag.getString("TechClassType");
			String savedId = techTag.getString("Id");

			if ("KI".equals(type) && PredefinedTechniques.EVASION_REGISTRY.containsKey(savedId)) {
				EvasionAttackData template = PredefinedTechniques.EVASION_REGISTRY.get(savedId);
				int[] refund = new int[2];
				EvasionAttackData migrated = migrateKiToEvasion(techTag, template, refundCostConfig(techTag), refund);
				if (refund[1] > 0) this.migrationRefunds.put(migrated.getId(), refund);
				this.unlockedTechniques.put(migrated.getId(), migrated);
				continue;
			}

			TechniqueData tech = type.equals("KI") ? new KiAttackData()
					: type.equals("EVASION") ? new EvasionAttackData() : new StrikeAttackData();
			tech.load(techTag);
			this.unlockedTechniques.put(tech.getId(), tech);
		}
	}

	public void copyFrom(Techniques other) {
		this.selectedSlot = other.selectedSlot;
		this.chargingTechniqueId = other.chargingTechniqueId;
		this.techniqueChargePercent = other.techniqueChargePercent;
		this.techniqueCharging = other.techniqueCharging;
		this.chargeHolding = other.chargeHolding;
		System.arraycopy(other.equippedSlots, 0, this.equippedSlots, 0, SLOT_COUNT);
		this.unlockedTechniques.clear();
		for (Map.Entry<String, TechniqueData> entry : other.unlockedTechniques.entrySet()) {
			this.unlockedTechniques.put(entry.getKey(), copyOf(entry.getValue()));
		}
	}
}
