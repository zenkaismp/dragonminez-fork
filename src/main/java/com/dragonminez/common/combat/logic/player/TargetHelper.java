package com.dragonminez.common.combat.logic.player;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.alignment.NpcDispositionService;
import com.dragonminez.common.init.entities.AllMastersEntity;
import com.dragonminez.common.init.entities.MastersEntity;
import com.dragonminez.common.quest.PartyManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.registries.ForgeRegistries;

public class TargetHelper {

    public static Entity resolveHittable(Entity entity) {
        return entity instanceof PartEntity<?> part ? part.getParent() : entity;
    }

    /**
     * Cenario: nao e alvo de combate, e ninguem pode empurrar, agarrar nem teleportar.
     *
     * <p>Sao as entidades que existem so pra DESENHAR ou pra receber clique: holograma
     * ({@code Display}, que cobre text/item/block display), hitbox invisivel de interface
     * ({@code Interaction}), marcador sem hitbox ({@code Marker}) e armor stand em modo marker.
     * O vanilla nao ajuda aqui: {@code Display} nao sobrescreve {@code isAttackable()}, entao
     * responde "sim, pode atacar" como qualquer mob.</p>
     *
     * <p><b>O bug que isto conserta (relato de 22/08):</b> mirando num holograma e usando Dragon
     * Fist, o holograma era ARRASTADO junto com o jogador. O {@code shouldDamage} classificava
     * o {@code TextDisplay} como NEUTRAL, e a regra de neutro so exige que o jogador esteja
     * olhando pra ele ({@code dot > 0.95}), que e exatamente o gesto de mirar. Pior: no
     * {@code SPDragonFistEntity.devastateEnemies} o {@code holdTargetAtCaster} roda FORA do
     * {@code if (hit)}, entao o {@code hurt()} devolver false (holograma nao toma dano) nao
     * impedia nada, so o agarrao continuava. Barrando aqui, o alvo nem entra na lista.</p>
     *
     * <p>Vale pros 11 ataques de ki de uma vez, porque todos filtram pelo mesmo
     * {@code shouldDamage}: blast, wave, disk, laser, area, explosion, dragon fist, ozaru fist,
     * blue hurricane e majin candy.</p>
     */
    public static boolean isCenario(Entity entity) {
        if (entity == null) return false;
        Entity target = resolveHittable(entity);
        if (target instanceof net.minecraft.world.entity.Display) return true;
        if (target instanceof net.minecraft.world.entity.Interaction) return true;
        if (target instanceof net.minecraft.world.entity.Marker) return true;
        return target instanceof net.minecraft.world.entity.decoration.ArmorStand armorStand
                && armorStand.isMarker();
    }

    public static Entity getEntityOrPart(Level level, int id) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getEntityOrPart(id);
        }
        return level.getEntity(id);
    }

    public enum Relation {
        FRIENDLY, NEUTRAL, HOSTILE;

        public static Relation coalesce(Relation value, Relation fallback) {
            if (value != null) return value;
            return fallback;
        }
    }

    public static Relation getRelation(Player attacker, Entity target) {
        target = resolveHittable(target);
        if (attacker == target) return Relation.FRIENDLY;

        if (target instanceof AllMastersEntity.MasterEnmaEntity || target instanceof AllMastersEntity.MasterUranaiEntity || target instanceof AllMastersEntity.MasterToribotEntity) {
            return Relation.FRIENDLY;
        }

        if (target instanceof Player targetPlayer) {
            if (PartyManager.areInSameParty(attacker, targetPlayer)) {
                return PartyManager.isPartyPvpEnabled(attacker) ? Relation.HOSTILE : Relation.FRIENDLY;
            }
        }

        if (target instanceof TamableAnimal tameable) {
            var owner = tameable.getOwner();
            if (owner != null) return getRelation(attacker, owner);
        }

        if (target instanceof HangingEntity) return Relation.NEUTRAL;

        var interactiveRelation = NpcDispositionService.getInteractiveRelation(attacker, target);
        if (interactiveRelation.isPresent()) return interactiveRelation.get();

        var config = ConfigManager.getCombatConfig();
        var casterTeam = attacker.getTeam();
        var targetTeam = target.getTeam();

        if (casterTeam == null || targetTeam == null) {
            var id = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
            var mappedRelation = config.getPlayerRelations().get(id != null ? id.toString() : "");
            if (mappedRelation != null) return mappedRelation;
            if (target instanceof Animal) return Relation.coalesce(config.getPlayerRelationToPassives(), Relation.HOSTILE);
            if (target instanceof Monster) return Relation.coalesce(config.getPlayerRelationToHostiles(), Relation.HOSTILE);
            return Relation.coalesce(config.getPlayerRelationToOther(), Relation.HOSTILE);
        } else return attacker.isAlliedTo(target) ? Relation.FRIENDLY : Relation.HOSTILE;
    }

    public static boolean isAttackableMount(Entity entity) {
        if (entity instanceof Monster || isEntityHostileVehicle(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()) != null ? ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString() : "")) {
            return true;
        }
        return ConfigManager.getCombatConfig().getAllowAttackingMount();
    }

    public static boolean canAttack(Player attacker, Entity target, double maxRange) {
        target = resolveHittable(target);
        Relation relation = getRelation(attacker, target);
        if (target instanceof MastersEntity) return false;
        return switch (relation) {
            case FRIENDLY -> false;
            case HOSTILE -> true;
            case NEUTRAL -> isLookingAt(attacker, target, maxRange);
        };
    }

    public static void onSuccessfulAttack(Player attacker, Entity target, Relation relation) {
        if (relation == Relation.NEUTRAL && attacker instanceof ServerPlayer serverPlayer && NpcDispositionService.isInteractiveNpc(target)) {
            NpcDispositionService.markHostile(serverPlayer, target);
        }
    }

    private static boolean isLookingAt(Player attacker, Entity target, double maxRange) {
        if (attacker == null || target == null) return false;

        Vec3 eye = attacker.getEyePosition();
        Vec3 look = attacker.getLookAngle().normalize();
        AABB box = target.getBoundingBox().inflate(0.15);
        var hit = box.clip(eye, eye.add(look.scale(maxRange + 1.0)));
        return hit.isPresent();
    }

    public static boolean isEntityHostileVehicle(String entityName) {
        return false;
    }
}