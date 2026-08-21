package com.dragonminez.server.world.structure.placement;

import com.dragonminez.common.config.ConfigManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

@Getter
public class BiomeAwareUniquePlacement extends StructurePlacement {
	public static final Codec<BiomeAwareUniquePlacement> CODEC = RecordCodecBuilder.create(instance ->
			placementCodec(instance).and(instance.group(
					RegistryCodecs.homogeneousList(Registries.BIOME)
							.fieldOf("valid_biomes")
							.forGetter(BiomeAwareUniquePlacement::getValidBiomes),
					Rotation.CODEC.optionalFieldOf("rotation", Rotation.NONE)
							.forGetter(BiomeAwareUniquePlacement::getRotation)
			)).apply(instance, BiomeAwareUniquePlacement::new));

	private final HolderSet<Biome> validBiomes;
	private final Rotation rotation;

	public BiomeAwareUniquePlacement(Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod, float frequency, int salt, Optional<ExclusionZone> exclusionZone, HolderSet<Biome> validBiomes, Rotation rotation) {
		super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone);
		this.validBiomes = validBiomes;
		this.rotation = rotation;
		StructureSpawnPlanner.register(this);
	}

	public int placementSalt() {
		return this.salt();
	}

	public BiomeAwareUniquePlacement(Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod, float frequency, int salt, Optional<ExclusionZone> exclusionZone, HolderSet<Biome> validBiomes) {
		this(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, validBiomes, Rotation.NONE);
	}

	/**
	 * ANTES existia aqui uma copia do cache reflexivo (um "static Field biomeSourceField"
	 * sem volatile, preenchido no primeiro uso). Com geracao de chunk paralela o
	 * isPlacementChunk passa a rodar em varias threads ao mesmo tempo, e esse cache virava
	 * corrida: uma thread podia enxergar a referencia do Field ANTES do efeito do
	 * setAccessible, a leitura estourava e o metodo devolvia null. Null aqui significa
	 * "sem posicao planejada", ou seja, a estrutura do DMZ simplesmente NAO gera e nada
	 * aparece no log.
	 *
	 * <p>Agora existe um unico cache, publicado com seguranca pelo
	 * {@link StructureSpawnPlanner#getBiomeSourceReflection(ChunkGeneratorStructureState)}.
	 * Dois caches pro mesmo campo tambem eram desperdicio: e o mesmo campo do mesmo
	 * ChunkGeneratorStructureState.</p>
	 */
	@Override
	protected boolean isPlacementChunk(@NonNull ChunkGeneratorStructureState structureState, int x, int z) {
		if (!ConfigManager.getServerConfig().getWorldGen().getGenerateCustomStructures()) return false;
		BiomeSource biomeSource = StructureSpawnPlanner.getBiomeSourceReflection(structureState);
		if (biomeSource == null) return false;
		ChunkPos pos = getStructureChunk(structureState.getLevelSeed(), biomeSource, structureState.randomState(), structureState);
		return pos != null && pos.x == x && pos.z == z;
	}

	public ChunkPos getStructureChunk(long worldSeed, BiomeSource biomeSource, RandomState randomState,
									  ChunkGeneratorStructureState state) {
		return StructureSpawnPlanner.getPositionFor(this, worldSeed, biomeSource, randomState, state);
	}

	@Override
	public @NonNull StructurePlacementType<?> type() {
		return MainStructurePlacements.BIOME_AWARE_PLACEMENT.get();
	}
}