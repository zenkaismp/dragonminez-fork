package com.dragonminez.common.quest;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.quest.objectives.ItemObjective;
import com.dragonminez.common.quest.objectives.KillObjective;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Quest model used by both the saga system and the side-quest system.
 *
 * @since 2.0
 */
@Getter
public class Quest {

	public enum QuestType {
		SAGA,
		SIDEQUEST,
		DAILY,
		EVENT
	}

	public enum ClaimMode {
		TREE_OR_NPC,
		NPC_ONLY
	}

	private final int id;
	private final String stringId;
	private final QuestType type;
	private final String title;
	private final String description;
	private final List<QuestObjective> objectives;
	private final List<QuestReward> rewards;

	@Setter
	private boolean completed;

	@Setter
	private int currentObjectiveIndex;

	/**
	 * Duracao de referencia da luta em SEGUNDOS (o {@code duracao-da-luta} que o autobalanceador
	 * usou pra calibrar os stats do mob). {@code 0} = quest sem calibragem, e o sistema de
	 * regeneracao por tempo ({@link QuestOvertimeRegen}) fica desligado pra ela.
	 *
	 * <p>Setter em vez de parametro no construtor de proposito: o construtor "universal" ja tem
	 * quinze argumentos e varios chamadores; um campo opcional com default 0 nao justifica quebrar
	 * todos eles. Segue o idioma dos outros campos mutaveis desta classe.</p>
	 */
	@Setter
	private int fightDurationSeconds;

	private final String category;
	private final boolean parallelObjectives;
	private final boolean partyScaling;
	private final String questGiver;
	private final String turnIn;
	private final QuestPrerequisites prerequisites;
	private final QuestPrerequisites startRequirements;
	private final boolean secret;
	private final ClaimMode claimMode;

	/**
	 * Universal constructor for quests.
	 */
	public Quest(int id, String stringId, QuestType type, String title, String description,
				 String category, boolean parallelObjectives, boolean partyScaling,
				 List<QuestObjective> objectives, List<QuestReward> rewards,
				 QuestPrerequisites prerequisites, QuestPrerequisites startRequirements,
				 String questGiver, String turnIn) {
		this(id, stringId, type, title, description, category, parallelObjectives, partyScaling,
				objectives, rewards, prerequisites, startRequirements, questGiver, turnIn,
				false, ClaimMode.TREE_OR_NPC);
	}

	public Quest(int id, String stringId, QuestType type, String title, String description,
				 String category, boolean parallelObjectives, boolean partyScaling,
				 List<QuestObjective> objectives, List<QuestReward> rewards,
				 QuestPrerequisites prerequisites, QuestPrerequisites startRequirements,
				 String questGiver, String turnIn, boolean secret, ClaimMode claimMode) {
		this.id = id;
		this.stringId = stringId;
		this.type = type != null ? type : QuestType.SAGA;
		this.title = title;
		this.description = description;
		this.objectives = objectives != null ? objectives : new ArrayList<>();
		this.rewards = rewards != null ? rewards : new ArrayList<>();
		this.completed = false;
		this.currentObjectiveIndex = 0;
		this.category = category != null ? category : "general";
		this.parallelObjectives = parallelObjectives;
		this.partyScaling = partyScaling;
		this.questGiver = questGiver;
		this.turnIn = turnIn;
		this.prerequisites = prerequisites;
		this.startRequirements = startRequirements;
		this.secret = secret;
		this.claimMode = claimMode != null ? claimMode : ClaimMode.TREE_OR_NPC;
	}

	public boolean hasPrerequisites() {
		return prerequisites != null && !prerequisites.conditions().isEmpty();
	}

	public boolean hasStartRequirements() {
		return startRequirements != null && !startRequirements.conditions().isEmpty();
	}

	public boolean isSideQuest() {
		return type == QuestType.SIDEQUEST;
	}

	public boolean isSagaQuest() {
		return type == QuestType.SAGA;
	}

	public String getEffectiveId() {
		if (stringId != null) return stringId;
		return String.valueOf(id);
	}

	public int getObjectiveRequired(PlayerQuestData pqd, String questId, int objectiveIndex) {
		if (objectiveIndex < 0 || objectiveIndex >= objectives.size()) {
			return 0;
		}

		QuestObjective objective = objectives.get(objectiveIndex);
		if (pqd == null) {
			return objective.getRequired();
		}

		return pqd.getObjectiveRequired(questId, objectiveIndex, objective.getRequired());
	}

	public void initializeObjectiveRequirements(PlayerQuestData pqd, String questId, int partySize) {
		if (pqd == null || questId == null || questId.isBlank()) {
			return;
		}

		int safePartySize = Math.max(1, partySize);
		for (int i = 0; i < objectives.size(); i++) {
			pqd.setObjectiveRequired(questId, i, getScaledObjectiveRequired(objectives.get(i), safePartySize));
		}
	}

	/**
	 * VIDA multiplica pelo TAMANHO da party (2 players = 2x, 3 = 3x): sao N jogadores fazendo
	 * ~N vezes o DPS de referencia, entao a luta so mantem a duracao calibrada (o T do
	 * autobalanceador) com a vida acompanhando linearmente. O knob enemyHealthPerPartyPlayer
	 * deixou de valer pra vida (dava so +25% por membro extra e a party diluia a luta).
	 *
	 * <p>O DANO continua no knob (1.1 = +10% por membro extra, decisao do dono 2026-08-11): o
	 * NPC foca UM alvo por vez, entao dano x N nao distribui, concentra — mataria o focado.</p>
	 */
	public double getScaledKillHealth(KillObjective objective, int partySize) {
		if (!partyScaling) {
			return objective.getHealth();
		}
		return objective.getHealth() * Math.max(1, partySize);
	}

	public double getScaledKillMeleeDamage(KillObjective objective, int partySize) {
		return objective.getMeleeDamage() * enemyPartyMultiplier(partySize,
				ConfigManager.getServerConfig().getGameplay().getEnemyDamagePerPartyPlayer());
	}

	public double getScaledKillKiDamage(KillObjective objective, int partySize) {
		return objective.getKiDamage() * enemyPartyMultiplier(partySize,
				ConfigManager.getServerConfig().getGameplay().getEnemyDamagePerPartyPlayer());
	}

	public Double getScaledTransformHealth(KillObjective objective, int partySize) {
		Double base = objective.getTransformHealth();
		if (base == null) {
			return null;
		}
		// Mesma regra da vida base: x tamanho da party (a forma transformada e a mesma luta).
		return partyScaling ? base * Math.max(1, partySize) : base;
	}

	public Double getScaledTransformMeleeDamage(KillObjective objective, int partySize) {
		Double base = objective.getTransformMeleeDamage();
		return base == null ? null : base * enemyPartyMultiplier(partySize,
				ConfigManager.getServerConfig().getGameplay().getEnemyDamagePerPartyPlayer());
	}

	public Double getScaledTransformKiDamage(KillObjective objective, int partySize) {
		Double base = objective.getTransformKiDamage();
		return base == null ? null : base * enemyPartyMultiplier(partySize,
				ConfigManager.getServerConfig().getGameplay().getEnemyDamagePerPartyPlayer());
	}

	private int getScaledObjectiveRequired(QuestObjective objective, int partySize) {
		int baseRequired = objective.getRequired();
		if (!partyScaling || partySize <= 1 || baseRequired <= 0) {
			return baseRequired;
		}

		int extraMembers = partySize - 1;
		double configuredMultiplier = ConfigManager.getServerConfig().getGameplay().getDefaultQuestPartyMultiplier();

		if (objective instanceof ItemObjective) {
			double scaled = baseRequired * Math.pow(configuredMultiplier, extraMembers);
			return roundUpCount(Math.max(baseRequired, scaled), resolveItemCountStep(baseRequired));
		}

		// KILL nao escala mais a CONTAGEM: a vida do mob ja multiplica pelo tamanho da party
		// (getScaledKillHealth), e escalar as duas coisas cobrava a party duas vezes (contagem
		// por 1.45^0.75 por extra EM CIMA de vida por N chegava a 3x de esforco por pessoa).

		return baseRequired;
	}

	private double enemyPartyMultiplier(int partySize, double perPlayerMultiplier) {
		if (!partyScaling) {
			return 1.0;
		}
		int extraMembers = Math.max(1, partySize) - 1;
		return 1.0 + extraMembers * (perPlayerMultiplier - 1.0);
	}

	private static int resolveItemCountStep(int baseRequired) {
		if (baseRequired >= 96) {
			return 10;
		}
		if (baseRequired >= 24) {
			return 5;
		}
		if (baseRequired >= 8) {
			return 2;
		}
		return 1;
	}

	private static int roundUpCount(double value, int step) {
		if (step <= 1) {
			return (int) Math.ceil(value);
		}
		return (int) (Math.ceil(value / step) * step);
	}
}
