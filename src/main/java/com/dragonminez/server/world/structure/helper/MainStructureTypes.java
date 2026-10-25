package com.dragonminez.server.world.structure.helper;

import com.dragonminez.Reference;
import com.dragonminez.server.world.structure.BossStructures.GomahCradleStructure;
import com.dragonminez.server.world.structure.DemonVillageStructure;
import com.dragonminez.server.world.structure.TallJigsawStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class MainStructureTypes {
	public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
			DeferredRegister.create(Registries.STRUCTURE_TYPE, Reference.MOD_ID);
	public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
			DeferredRegister.create(Registries.STRUCTURE_PIECE, Reference.MOD_ID);

	public static final RegistryObject<StructureType<TallJigsawStructure>> TALL_JIGSAW =
			STRUCTURE_TYPES.register("tall_jigsaw", () -> () -> TallJigsawStructure.CODEC);
	public static final RegistryObject<StructureType<DemonVillageStructure>> DEMON_VILLAGE =
			STRUCTURE_TYPES.register("demon_village", () -> () -> DemonVillageStructure.CODEC);
	public static final RegistryObject<StructureType<GomahCradleStructure>> GOMAH_CRADLE =
			STRUCTURE_TYPES.register("gomah_cradle", () -> () -> GomahCradleStructure.CODEC);

	public static final RegistryObject<StructurePieceType> DEMON_VILLAGE_BUILDING =
			STRUCTURE_PIECES.register("demon_village_building", () -> (StructurePieceType.StructureTemplateType) DemonVillageStructure.BuildingPiece::new);
	public static final RegistryObject<StructurePieceType> DEMON_VILLAGE_GROUND =
			STRUCTURE_PIECES.register("demon_village_ground", () -> (StructurePieceType.ContextlessType) DemonVillageStructure.GroundPiece::new);
	public static final RegistryObject<StructurePieceType> GOMAH_CRADLE_PIECE =
			STRUCTURE_PIECES.register("gomah_cradle", () -> (StructurePieceType.ContextlessType) GomahCradleStructure.Piece::new);

	public static void register(IEventBus eventBus) {
		STRUCTURE_TYPES.register(eventBus);
		STRUCTURE_PIECES.register(eventBus);
	}
}
