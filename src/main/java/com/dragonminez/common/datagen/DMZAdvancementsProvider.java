package com.dragonminez.common.datagen;

import com.dragonminez.Reference;
import com.dragonminez.common.init.MainBlocks;
import com.dragonminez.common.init.MainItems;
import com.dragonminez.server.world.biome.OverworldBiomes;
import com.dragonminez.server.world.dimension.HTCDimension;
import com.dragonminez.server.world.dimension.NamekDimension;
import com.dragonminez.server.world.dimension.OtherworldDimension;
import com.dragonminez.server.world.dimension.SacredKaiDimension;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.FrameType;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.critereon.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.advancements.AdvancementProvider;
import net.minecraft.data.advancements.AdvancementSubProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public class DMZAdvancementsProvider extends AdvancementProvider {
	private static ServerLevel serverLevel;

	public DMZAdvancementsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries, List.of(new DMZAdvancements()));
	}

	private static class DMZAdvancements implements AdvancementSubProvider {
		@Override
		public void generate(HolderLookup.Provider provider, Consumer<AdvancementHolder> consumer) {
			AdvancementHolder root = Advancement.Builder.advancement()
					.display(
							MainItems.DBALL4_BLOCK_ITEM.get(), // Ítem de muestra
							Component.translatable("advancements.dragonminez.root.title"), // Título
							Component.translatable("advancements.dragonminez.root.description"), // Descripción
							new ResourceLocation(Reference.MOD_ID, "textures/block/rocky_stone.png"), // Textura de fondo
							FrameType.TASK, true, true, false
					) // Tipo de marco, si se muestra en la esquina superior derecha, si se muestra en el chat y si se oculta en los Logros ("Logro Oculto/Secreto")
					.addCriterion("first_spawn_in_world", // Nombre del criterio
							PlayerTrigger.TriggerInstance.located(EntityPredicate.Builder.entity().of(EntityType.PLAYER))) // Criterio
					.rewards(AdvancementRewards.Builder.experience(0)) // Recompensa de experiencia (Se pueden poner más tipos xd)
					.save(consumer, "dragonminez:root"); // Logro "raíz" o "inicial"; el primero de todos.

			AdvancementHolder rockybiome = Advancement.Builder.advancement()
					.parent(root)
					.display(
							MainBlocks.ROCKY_STONE.get(),
							Component.translatable("advancements.dragonminez.rockybiome.title"),
							Component.translatable("advancements.dragonminez.rockybiome.description"),
							null, FrameType.GOAL, true, true, false
					).addCriterion("found_rockybiome",
							PlayerTrigger.TriggerInstance.located(
									LocationPredicate.Builder.location()
											.setBiome(OverworldBiomes.ROCKY)
							)
					).save(consumer, "dragonminez:rockybiome");

			AdvancementHolder otherworld = Advancement.Builder.advancement()
					.parent(root)
					.display(
							MainBlocks.OTHERWORLD_CLOUD.get(),
							Component.translatable("advancements.dragonminez.otherworld.title"),
							Component.translatable("advancements.dragonminez.otherworld.description"),
							null, FrameType.GOAL, true, true, false
					).addCriterion("found_otherworld",
							ChangeDimensionTrigger.TriggerInstance.changedDimensionTo(OtherworldDimension.OTHERWORLD_KEY)
					).save(consumer, "dragonminez:otherworld");


			AdvancementHolder invincible = Advancement.Builder.advancement()
					.parent(root)
					.display(
							MainItems.INVENCIBLE_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.invincible.title"),
							Component.translatable("advancements.dragonminez.invincible.description"),
							null, FrameType.GOAL, true, true, false
					).addCriterion("invincible",
							InventoryChangeTrigger.TriggerInstance.hasItems(MainItems.INVENCIBLE_ARMOR.get(ArmorItem.Type.HELMET).get(),
									MainItems.INVENCIBLE_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
									MainItems.INVENCIBLE_ARMOR.get(ArmorItem.Type.LEGGINGS).get(),
									MainItems.INVENCIBLE_ARMOR.get(ArmorItem.Type.BOOTS).get()))
					.addCriterion("invincible_blue",
							InventoryChangeTrigger.TriggerInstance.hasItems(MainItems.INVENCIBLE_BLUE_ARMOR.get(ArmorItem.Type.HELMET).get(),
									MainItems.INVENCIBLE_BLUE_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
									MainItems.INVENCIBLE_BLUE_ARMOR.get(ArmorItem.Type.LEGGINGS).get(),
									MainItems.INVENCIBLE_BLUE_ARMOR.get(ArmorItem.Type.BOOTS).get()))
					.requirements(AdvancementRequirements.Strategy.OR)
					.save(consumer, "dragonminez:invincible");

			AdvancementHolder kamilookout = Advancement.Builder.advancement()
					.parent(root)
					.display(
							Items.CLOCK,
							Component.translatable("advancements.dragonminez.kamilookout.title"),
							Component.translatable("advancements.dragonminez.kamilookout.description"),
							null, FrameType.GOAL, true, true, false
					).addCriterion("kamilookout",
							PlayerTrigger.TriggerInstance.located(
									LocationPredicate.Builder.inStructure(
											ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(Reference.MOD_ID, "kamilookout"))
									)
							)
					).save(consumer, "dragonminez:kamilookout");

			AdvancementHolder gokuhouse = Advancement.Builder.advancement()
					.parent(root)
					.display(
							MainBlocks.DBALL4_BLOCK.get(),
							Component.translatable("advancements.dragonminez.gokuhouse.title"),
							Component.translatable("advancements.dragonminez.gokuhouse.description"),
							null, FrameType.GOAL, true, true, false
					).addCriterion("gokuhouse",
							PlayerTrigger.TriggerInstance.located(
									LocationPredicate.Builder.inStructure(
											ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(Reference.MOD_ID, "goku_house"))
									)
							)
					).save(consumer, "dragonminez:gokuhouse");

			AdvancementHolder roshihouse = Advancement.Builder.advancement()
					.parent(root)
					.display(
							MainItems.WEIGHT_TURTLE_SHELL.get(),
							Component.translatable("advancements.dragonminez.roshihouse.title"),
							Component.translatable("advancements.dragonminez.roshihouse.description"),
							null, FrameType.GOAL, true, true, false
					).addCriterion("roshihouse",
							PlayerTrigger.TriggerInstance.located(
									LocationPredicate.Builder.inStructure(
											ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(Reference.MOD_ID, "roshi_house"))
									)
							)
					).save(consumer, "dragonminez:roshihouse");

			AdvancementHolder timechamber = Advancement.Builder.advancement()
					.parent(kamilookout)
					.display(
							MainItems.VEGETA_Z_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.timechamber.title"),
							Component.translatable("advancements.dragonminez.timechamber.description"),
							null, FrameType.CHALLENGE, true, true, false
					).addCriterion("timechamber",
							ChangeDimensionTrigger.TriggerInstance.changedDimensionTo(HTCDimension.HTC_KEY)
					).save(consumer, "dragonminez:timechamber");

			AdvancementHolder nimbus = Advancement.Builder.advancement()
					.parent(kamilookout)
					.display(
							MainItems.NUBE_ITEM.get(),
							Component.translatable("advancements.dragonminez.nimbus.title"),
							Component.translatable("advancements.dragonminez.nimbus.description"),
							null, FrameType.GOAL, true, true, false
					).addCriterion("nimbus",
							InventoryChangeTrigger.TriggerInstance.hasItems(MainItems.NUBE_ITEM.get())
					).save(consumer, "dragonminez:nimbus");

			AdvancementHolder radar = Advancement.Builder.advancement()
					.parent(root)
					.display(
							MainItems.DBALL_RADAR_ITEM.get(),
							Component.translatable("advancements.dragonminez.radar.title"),
							Component.translatable("advancements.dragonminez.radar.description"),
							null, FrameType.GOAL, true, true, false
					).addCriterion("radar",
							InventoryChangeTrigger.TriggerInstance.hasItems(MainItems.DBALL_RADAR_ITEM.get())
					).save(consumer, "dragonminez:radar");

			AdvancementHolder dball1 = Advancement.Builder.advancement()
					.parent(radar)
					.display(
							MainItems.DBALL1_BLOCK_ITEM.get(),
							Component.translatable("advancements.dragonminez.dball1.title"),
							Component.translatable("advancements.dragonminez.dball1.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("dball1",
							InventoryChangeTrigger.TriggerInstance.hasItems(MainItems.DBALL1_BLOCK_ITEM.get())
					).save(consumer, "dragonminez:dball1");

			AdvancementHolder dball7 = Advancement.Builder.advancement()
					.parent(dball1)
					.display(
							MainItems.DBALL7_BLOCK_ITEM.get(),
							Component.translatable("advancements.dragonminez.dball7.title"),
							Component.translatable("advancements.dragonminez.dball7.description"),
							null, FrameType.CHALLENGE, true, true, true
					).addCriterion("dball7",
							InventoryChangeTrigger.TriggerInstance.hasItems(
									MainItems.DBALL1_BLOCK_ITEM.get(),
									MainItems.DBALL2_BLOCK_ITEM.get(),
									MainItems.DBALL3_BLOCK_ITEM.get(),
									MainItems.DBALL4_BLOCK_ITEM.get(),
									MainItems.DBALL5_BLOCK_ITEM.get(),
									MainItems.DBALL6_BLOCK_ITEM.get(),
									MainItems.DBALL7_BLOCK_ITEM.get())
					).save(consumer, "dragonminez:dball7");

			AdvancementHolder namekdim = Advancement.Builder.advancement()
					.parent(root)
					.display(
							MainBlocks.NAMEK_GRASS_BLOCK.get(),
							Component.translatable("advancements.dragonminez.namekdim.title"),
							Component.translatable("advancements.dragonminez.namekdim.description"),
							null, FrameType.GOAL, true, true, false
					).addCriterion("namekdim",
							ChangeDimensionTrigger.TriggerInstance.changedDimensionTo(NamekDimension.NAMEK_KEY)
					).save(consumer, "dragonminez:namekdim");

			AdvancementHolder patriarca = Advancement.Builder.advancement()
					.parent(namekdim)
					.display(
							MainItems.PICCOLO_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.patriarca.title"),
							Component.translatable("advancements.dragonminez.patriarca.description"),
							null, FrameType.GOAL, true, true, false
					).addCriterion("patriarca", inStructure("elder_guru")
					).save(consumer, "dragonminez:patriarca");

			AdvancementHolder radarnamek = Advancement.Builder.advancement()
					.parent(namekdim)
					.display(
							MainItems.NAMEKDBALL_RADAR_ITEM.get(),
							Component.translatable("advancements.dragonminez.radarnamek.title"),
							Component.translatable("advancements.dragonminez.radarnamek.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("radarnamek",
							InventoryChangeTrigger.TriggerInstance.hasItems(MainItems.NAMEKDBALL_RADAR_ITEM.get())
					).save(consumer, "dragonminez:radarnamek");

			AdvancementHolder namekballs = Advancement.Builder.advancement()
					.parent(radarnamek)
					.display(
							MainItems.DBALL1_NAMEK_BLOCK_ITEM.get(),
							Component.translatable("advancements.dragonminez.namekballs.title"),
							Component.translatable("advancements.dragonminez.namekballs.description"),
							null, FrameType.CHALLENGE, true, true, true
					).addCriterion("namekballs",
							InventoryChangeTrigger.TriggerInstance.hasItems(
									MainItems.DBALL1_NAMEK_BLOCK_ITEM.get(),
									MainItems.DBALL2_NAMEK_BLOCK_ITEM.get(),
									MainItems.DBALL3_NAMEK_BLOCK_ITEM.get(),
									MainItems.DBALL4_NAMEK_BLOCK_ITEM.get(),
									MainItems.DBALL5_NAMEK_BLOCK_ITEM.get(),
									MainItems.DBALL6_NAMEK_BLOCK_ITEM.get(),
									MainItems.DBALL7_NAMEK_BLOCK_ITEM.get())
					).save(consumer, "dragonminez:namekballs");

			AdvancementHolder kikono = Advancement.Builder.advancement()
					.parent(namekdim)
					.display(
							MainItems.KIKONO_SHARD.get(),
							Component.translatable("advancements.dragonminez.kikono.title"),
							Component.translatable("advancements.dragonminez.kikono.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("kikono",
							InventoryChangeTrigger.TriggerInstance.hasItems(MainItems.KIKONO_SHARD.get())
					).save(consumer, "dragonminez:kikono");

			AdvancementHolder armorStation = Advancement.Builder.advancement()
					.parent(kikono)
					.display(
							MainBlocks.KIKONO_STATION.get(),
							Component.translatable("advancements.dragonminez.armorstation.title"),
							Component.translatable("advancements.dragonminez.armorstation.description"),
							null, FrameType.GOAL, true, true, false
					).addCriterion("armorstation",
							InventoryChangeTrigger.TriggerInstance.hasItems(MainBlocks.KIKONO_STATION.get())
					).save(consumer, "dragonminez:armorstation");

			AdvancementHolder patternz = Advancement.Builder.advancement()
					.parent(armorStation)
					.display(
							MainItems.BLANK_PATTERN_Z.get(),
							Component.translatable("advancements.dragonminez.patternz.title"),
							Component.translatable("advancements.dragonminez.patternz.description"),
							null, FrameType.GOAL, true, true, false
					).addCriterion("patternz",
							InventoryChangeTrigger.TriggerInstance.hasItems(MainItems.BLANK_PATTERN_Z.get())
					).save(consumer, "dragonminez:patternz");

			AdvancementHolder patternsuper = Advancement.Builder.advancement()
					.parent(armorStation)
					.display(
							MainItems.BLANK_PATTERN_SUPER.get(),
							Component.translatable("advancements.dragonminez.patternsuper.title"),
							Component.translatable("advancements.dragonminez.patternsuper.description"),
							null, FrameType.GOAL, true, true, false
					).addCriterion("patternsuper",
							InventoryChangeTrigger.TriggerInstance.hasItems(MainItems.BLANK_PATTERN_SUPER.get())
					).save(consumer, "dragonminez:patternsuper");

			AdvancementHolder gokuarmor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.GOKU_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.gokuarmor.title"),
							Component.translatable("advancements.dragonminez.gokuarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("gokuarmor", armorOf(MainItems.GOKU_ARMOR))
					.addCriterion("gokuarmor_kid", armorOf(MainItems.GOKU_KID_ARMOR))
					.addCriterion("gokuarmor_super", armorOf(MainItems.GOKU_SUPER_ARMOR))
					.addCriterion("gokuarmor_gt", armorOf(MainItems.GOKU_GT_ARMOR))
					.addCriterion("gokuarmor_yardrat", armorOf(MainItems.YARDRAT_ARMOR))
					.requirements(AdvancementRequirements.Strategy.OR)
					.save(consumer, "dragonminez:gokuarmor");

			AdvancementHolder gotenarmor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.GOTEN_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.gotenarmor.title"),
							Component.translatable("advancements.dragonminez.gotenarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("gotenarmor", armorOf(MainItems.GOTEN_ARMOR))
					.addCriterion("gotenarmor_super", armorOf(MainItems.GOTEN_SUPER_ARMOR))
					.requirements(AdvancementRequirements.Strategy.OR)
					.save(consumer, "dragonminez:gotenarmor");

			AdvancementHolder futuregohanarmor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.FUTURE_GOHAN_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.futuregohanarmor.title"),
							Component.translatable("advancements.dragonminez.futuregohanarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("futuregohanarmor",
							InventoryChangeTrigger.TriggerInstance.hasItems(MainItems.FUTURE_GOHAN_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
									MainItems.FUTURE_GOHAN_ARMOR.get(ArmorItem.Type.LEGGINGS).get(),
									MainItems.FUTURE_GOHAN_ARMOR.get(ArmorItem.Type.BOOTS).get())
					).save(consumer, "dragonminez:futuregohanarmor");

			AdvancementHolder vegetaarmor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.VEGETA_SAIYAN_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.vegetaarmor.title"),
							Component.translatable("advancements.dragonminez.vegetaarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("vegetaarmor", armorOf(MainItems.VEGETA_SAIYAN_ARMOR))
					.addCriterion("vegetaarmor_namek", armorOf(MainItems.VEGETA_NAMEK_ARMOR))
					.addCriterion("vegetaarmor_z", armorOf(MainItems.VEGETA_Z_ARMOR))
					.addCriterion("vegetaarmor_gt", armorOf(MainItems.VEGETA_GT_ARMOR))
					.requirements(AdvancementRequirements.Strategy.OR)
					.save(consumer, "dragonminez:vegetaarmor");

			AdvancementHolder vegettoarmor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.VEGETTO_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.vegettoarmor.title"),
							Component.translatable("advancements.dragonminez.vegettoarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("vegettoarmor",
							InventoryChangeTrigger.TriggerInstance.hasItems(MainItems.VEGETTO_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
									MainItems.VEGETTO_ARMOR.get(ArmorItem.Type.LEGGINGS).get(),
									MainItems.VEGETTO_ARMOR.get(ArmorItem.Type.BOOTS).get())
					).save(consumer, "dragonminez:vegettoarmor");

			AdvancementHolder gogetaarmor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.GOGETA_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.gogetaarmor.title"),
							Component.translatable("advancements.dragonminez.gogetaarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("gogetaarmor",
							InventoryChangeTrigger.TriggerInstance.hasItems(MainItems.GOGETA_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
									MainItems.GOGETA_ARMOR.get(ArmorItem.Type.LEGGINGS).get(),
									MainItems.GOGETA_ARMOR.get(ArmorItem.Type.BOOTS).get())
					).save(consumer, "dragonminez:gogetaarmor");

			AdvancementHolder demonbluegiarmor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.DEMON_GI_BLUE_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.demonbluegiarmor.title"),
							Component.translatable("advancements.dragonminez.demonbluegiarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("demonbluegiarmor", armorOf(MainItems.DEMON_GI_BLUE_ARMOR))
					.addCriterion("demonbluegiarmor_gohan_super", armorOf(MainItems.GOHAN_SUPER_ARMOR))
					.requirements(AdvancementRequirements.Strategy.OR)
					.save(consumer, "dragonminez:demonbluegiarmor");

			AdvancementHolder bardockarmor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.BARDOCK_DBZ_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.bardockarmor.title"),
							Component.translatable("advancements.dragonminez.bardockarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("bardockarmor", armorOf(MainItems.BARDOCK_DBZ_ARMOR))
					.addCriterion("bardockarmor_super", armorOf(MainItems.BARDOCK_SUPER_ARMOR))
					.requirements(AdvancementRequirements.Strategy.OR)
					.save(consumer, "dragonminez:bardockarmor");

			AdvancementHolder turlesarmor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.TURLES_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.turlesarmor.title"),
							Component.translatable("advancements.dragonminez.turlesarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("turlesarmor",
							InventoryChangeTrigger.TriggerInstance.hasItems(MainItems.TURLES_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
									MainItems.TURLES_ARMOR.get(ArmorItem.Type.LEGGINGS).get(),
									MainItems.TURLES_ARMOR.get(ArmorItem.Type.BOOTS).get())
					).save(consumer, "dragonminez:turlesarmor");

			AdvancementHolder tienarmor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.TIEN_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.tienarmor.title"),
							Component.translatable("advancements.dragonminez.tienarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("tienarmor",
							InventoryChangeTrigger.TriggerInstance.hasItems(MainItems.TIEN_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
									MainItems.TIEN_ARMOR.get(ArmorItem.Type.LEGGINGS).get(),
									MainItems.TIEN_ARMOR.get(ArmorItem.Type.BOOTS).get())
					).save(consumer, "dragonminez:tienarmor");

			AdvancementHolder trunksarmor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.TRUNKS_Z_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.trunksarmor.title"),
							Component.translatable("advancements.dragonminez.trunksarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("trunksarmor", armorOf(MainItems.TRUNKS_Z_ARMOR))
					.addCriterion("trunksarmor_super", armorOf(MainItems.TRUNKS_SUPER_ARMOR))
					.addCriterion("trunksarmor_kid", armorOf(MainItems.TRUNKS_KID_ARMOR))
					.requirements(AdvancementRequirements.Strategy.OR)
					.save(consumer, "dragonminez:trunksarmor");

			AdvancementHolder brolyarmor = Advancement.Builder.advancement()
					.parent(patternsuper)
					.display(
							MainItems.BROLY_SUPER_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.brolyarmor.title"),
							Component.translatable("advancements.dragonminez.brolyarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("brolyarmor", armorOf(MainItems.BROLY_SUPER_ARMOR))
					.addCriterion("brolyarmor_z", armorOf(MainItems.BROLY_Z_ARMOR))
					.requirements(AdvancementRequirements.Strategy.OR)
					.save(consumer, "dragonminez:brolyarmor");

			AdvancementHolder shinarmor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.SHIN_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.shinarmor.title"),
							Component.translatable("advancements.dragonminez.shinarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("shinarmor",
							InventoryChangeTrigger.TriggerInstance.hasItems(MainItems.SHIN_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
									MainItems.SHIN_ARMOR.get(ArmorItem.Type.LEGGINGS).get(),
									MainItems.SHIN_ARMOR.get(ArmorItem.Type.BOOTS).get())
					).save(consumer, "dragonminez:shinarmor");

			AdvancementHolder blackarmor = Advancement.Builder.advancement()
					.parent(patternsuper)
					.display(
							MainItems.BLACKGOKU_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.blackarmor.title"),
							Component.translatable("advancements.dragonminez.blackarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("blackarmor",
							InventoryChangeTrigger.TriggerInstance.hasItems(MainItems.BLACKGOKU_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
									MainItems.BLACKGOKU_ARMOR.get(ArmorItem.Type.LEGGINGS).get(),
									MainItems.BLACKGOKU_ARMOR.get(ArmorItem.Type.BOOTS).get())
					).save(consumer, "dragonminez:blackarmor");

			AdvancementHolder zamasuarmor = Advancement.Builder.advancement()
					.parent(patternsuper)
					.display(
							MainItems.ZAMASU_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.zamasuarmor.title"),
							Component.translatable("advancements.dragonminez.zamasuarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("zamasuarmor",
							InventoryChangeTrigger.TriggerInstance.hasItems(MainItems.ZAMASU_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
									MainItems.ZAMASU_ARMOR.get(ArmorItem.Type.LEGGINGS).get(),
									MainItems.ZAMASU_ARMOR.get(ArmorItem.Type.BOOTS).get())
					).save(consumer, "dragonminez:zamasuarmor");

			AdvancementHolder fusionzamasuarmor = Advancement.Builder.advancement()
					.parent(patternsuper)
					.display(
							MainItems.FUSION_ZAMASU_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.fusionzamasuarmor.title"),
							Component.translatable("advancements.dragonminez.fusionzamasuarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("fusionzamasuarmor",
							InventoryChangeTrigger.TriggerInstance.hasItems(MainItems.FUSION_ZAMASU_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
									MainItems.FUSION_ZAMASU_ARMOR.get(ArmorItem.Type.LEGGINGS).get(),
									MainItems.FUSION_ZAMASU_ARMOR.get(ArmorItem.Type.BOOTS).get())
					).save(consumer, "dragonminez:fusionzamasuarmor");

			AdvancementHolder pridetrooparmor = Advancement.Builder.advancement()
					.parent(patternsuper)
					.display(
							MainItems.PRIDE_TROOPS_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.pridetroopsarmor.title"),
							Component.translatable("advancements.dragonminez.pridetroopsarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("pridetrooparmor",
							InventoryChangeTrigger.TriggerInstance.hasItems(MainItems.PRIDE_TROOPS_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
									MainItems.PRIDE_TROOPS_ARMOR.get(ArmorItem.Type.LEGGINGS).get(),
									MainItems.PRIDE_TROOPS_ARMOR.get(ArmorItem.Type.BOOTS).get())
					).save(consumer, "dragonminez:pridetrooparmor");

			AdvancementHolder hitarmor = Advancement.Builder.advancement()
					.parent(patternsuper)
					.display(
							MainItems.HIT_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.hitarmor.title"),
							Component.translatable("advancements.dragonminez.hitarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("hitarmor",
							InventoryChangeTrigger.TriggerInstance.hasItems(MainItems.HIT_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
									MainItems.HIT_ARMOR.get(ArmorItem.Type.LEGGINGS).get(),
									MainItems.HIT_ARMOR.get(ArmorItem.Type.BOOTS).get())
					).save(consumer, "dragonminez:hitarmor");

			AdvancementHolder gasarmor = Advancement.Builder.advancement()
					.parent(patternsuper)
					.display(
							MainItems.GAS_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.gasarmor.title"),
							Component.translatable("advancements.dragonminez.gasarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("gasarmor",
							InventoryChangeTrigger.TriggerInstance.hasItems(MainItems.GAS_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
									MainItems.GAS_ARMOR.get(ArmorItem.Type.LEGGINGS).get(),
									MainItems.GAS_ARMOR.get(ArmorItem.Type.BOOTS).get())
					).save(consumer, "dragonminez:gasarmor");

			AdvancementHolder sacredkai = Advancement.Builder.advancement()
					.parent(root)
					.display(
							MainBlocks.SACRED_PLANET_GRASS_BLOCK.get(),
							Component.translatable("advancements.dragonminez.sacredkai.title"),
							Component.translatable("advancements.dragonminez.sacredkai.description"),
							null, FrameType.CHALLENGE, true, true, false
					).addCriterion("sacredkai",
							ChangeDimensionTrigger.TriggerInstance.changedDimensionTo(SacredKaiDimension.SACREDKAI_KEY)
					).save(consumer, "dragonminez:sacredkai");

			AdvancementHolder gerolab = Advancement.Builder.advancement()
					.parent(rockybiome)
					.display(
							MainItems.A16_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.gerolab.title"),
							Component.translatable("advancements.dragonminez.gerolab.description"),
							null, FrameType.GOAL, true, true, false
					).addCriterion("gerolab", inStructure("gero_lab")
					).save(consumer, "dragonminez:gerolab");

			AdvancementHolder babidi = Advancement.Builder.advancement()
					.parent(root)
					.display(
							net.minecraft.world.level.block.Blocks.SPAWNER,
							Component.translatable("advancements.dragonminez.babidi.title"),
							Component.translatable("advancements.dragonminez.babidi.description"),
							null, FrameType.GOAL, true, true, false
					).addCriterion("babidi", inStructure("babidi")
					).save(consumer, "dragonminez:babidi");

			AdvancementHolder cellarena = Advancement.Builder.advancement()
					.parent(root)
					.display(
							net.minecraft.world.level.block.Blocks.QUARTZ_BLOCK,
							Component.translatable("advancements.dragonminez.cellarena.title"),
							Component.translatable("advancements.dragonminez.cellarena.description"),
							null, FrameType.GOAL, true, true, false
					).addCriterion("cellarena", inStructure("cell_arena")
					).save(consumer, "dragonminez:cellarena");

			AdvancementHolder piccolohouse = Advancement.Builder.advancement()
					.parent(root)
					.display(
							MainItems.WEIGHT_PICCOLO_CAPE.get(),
							Component.translatable("advancements.dragonminez.piccolohouse.title"),
							Component.translatable("advancements.dragonminez.piccolohouse.description"),
							null, FrameType.GOAL, true, true, false
					).addCriterion("piccolohouse", inStructure("piccolo_house")
					).save(consumer, "dragonminez:piccolohouse");

			AdvancementHolder yamchahouse = Advancement.Builder.advancement()
					.parent(root)
					.display(
							Blocks.SAND,
							Component.translatable("advancements.dragonminez.yamchahouse.title"),
							Component.translatable("advancements.dragonminez.yamchahouse.description"),
							null, FrameType.GOAL, true, true, false
					).addCriterion("yamchahouse", inStructure("yamcha_house")
					).save(consumer, "dragonminez:yamchahouse");

			AdvancementHolder trunksship = Advancement.Builder.advancement()
					.parent(root)
					.display(
							MainItems.TRUNKS_Z_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.trunksship.title"),
							Component.translatable("advancements.dragonminez.trunksship.description"),
							null, FrameType.GOAL, true, true, false
					).addCriterion("trunksship", inStructure("trunks_ship")
					).save(consumer, "dragonminez:trunksship");

			AdvancementHolder vegetapod = Advancement.Builder.advancement()
					.parent(rockybiome)
					.display(
							MainItems.NAVE_SAIYAN_ITEM.get(),
							Component.translatable("advancements.dragonminez.vegetapod.title"),
							Component.translatable("advancements.dragonminez.vegetapod.description"),
							null, FrameType.GOAL, true, true, false
					).addCriterion("vegetapod", inStructure("vegeta_pod")
					).save(consumer, "dragonminez:vegetapod");

			AdvancementHolder friezaship = Advancement.Builder.advancement()
					.parent(namekdim)
					.display(
							net.minecraft.world.level.block.Blocks.IRON_BLOCK,
							Component.translatable("advancements.dragonminez.friezaship.title"),
							Component.translatable("advancements.dragonminez.friezaship.description"),
							null, FrameType.GOAL, true, true, false
					).addCriterion("friezaship", inStructure("frieza_ship")
					).save(consumer, "dragonminez:friezaship");

			AdvancementHolder oldkaipillar = Advancement.Builder.advancement()
					.parent(sacredkai)
					.display(
							MainBlocks.SACRED_CHRYSANTHEMUM_FLOWER.get(),
							Component.translatable("advancements.dragonminez.oldkaipillar.title"),
							Component.translatable("advancements.dragonminez.oldkaipillar.description"),
							null, FrameType.GOAL, true, true, false
					).addCriterion("oldkaipillar", inStructure("oldkai_pillar")
					).save(consumer, "dragonminez:oldkaipillar");

			AdvancementHolder greatsaiyamanarmor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.GREAT_SAIYAMAN_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.greatsaiyamanarmor.title"),
							Component.translatable("advancements.dragonminez.greatsaiyamanarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("greatsaiyamanarmor", armorOf(MainItems.GREAT_SAIYAMAN_ARMOR)
					).save(consumer, "dragonminez:greatsaiyamanarmor");

			AdvancementHolder piccoloarmor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.PICCOLO_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.piccoloarmor.title"),
							Component.translatable("advancements.dragonminez.piccoloarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("piccoloarmor", armorOf(MainItems.PICCOLO_ARMOR)
					).save(consumer, "dragonminez:piccoloarmor");

			AdvancementHolder majinbuuarmor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.MAJIN_BUU_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.majinbuuarmor.title"),
							Component.translatable("advancements.dragonminez.majinbuuarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("majinbuuarmor", armorOf(MainItems.MAJIN_BUU_ARMOR)
					).save(consumer, "dragonminez:majinbuuarmor");

			AdvancementHolder vegetabuuarmor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.VEGETA_BUU_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.vegetabuuarmor.title"),
							Component.translatable("advancements.dragonminez.vegetabuuarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("vegetabuuarmor", armorOf(MainItems.VEGETA_BUU_ARMOR)
					).save(consumer, "dragonminez:vegetabuuarmor");

			AdvancementHolder a16armor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.A16_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.a16armor.title"),
							Component.translatable("advancements.dragonminez.a16armor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("a16armor", armorOf(MainItems.A16_ARMOR)
					).save(consumer, "dragonminez:a16armor");

			AdvancementHolder a17armor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.A17_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.a17armor.title"),
							Component.translatable("advancements.dragonminez.a17armor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("a17armor", armorOf(MainItems.A17_ARMOR))
					.addCriterion("a17armor_super", armorOf(MainItems.A17_SUPER_ARMOR))
					.requirements(AdvancementRequirements.Strategy.OR)
					.save(consumer, "dragonminez:a17armor");

			AdvancementHolder a18armor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.A18_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.a18armor.title"),
							Component.translatable("advancements.dragonminez.a18armor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("a18armor", armorOf(MainItems.A18_ARMOR))
					.addCriterion("a18armor_kame", armorOf(MainItems.A18_KAME_ARMOR))
					.addCriterion("a18armor_tournament", armorOf(MainItems.A18_TOURNAMENT_ARMOR))
					.addCriterion("a18armor_cell", armorOf(MainItems.A18_CELL_ARMOR))
					.requirements(AdvancementRequirements.Strategy.OR)
					.save(consumer, "dragonminez:a18armor");

			AdvancementHolder orangehigharmor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.ORANGE_HIGH_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.orangehigharmor.title"),
							Component.translatable("advancements.dragonminez.orangehigharmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("orangehigharmor", armorOf(MainItems.ORANGE_HIGH_ARMOR)
					).save(consumer, "dragonminez:orangehigharmor");

			AdvancementHolder videlarmor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.VIDEL_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.videlarmor.title"),
							Component.translatable("advancements.dragonminez.videlarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("videlarmor", armorOf(MainItems.VIDEL_ARMOR)
					).save(consumer, "dragonminez:videlarmor");

			AdvancementHolder narukearmor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.NARUKE_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.narukearmor.title"),
							Component.translatable("advancements.dragonminez.narukearmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("narukearmor", armorOf(MainItems.NARUKE_ARMOR)
					).save(consumer, "dragonminez:narukearmor");

			AdvancementHolder strongestarmor = Advancement.Builder.advancement()
					.parent(patternz)
					.display(
							MainItems.STRONGEST_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.strongestarmor.title"),
							Component.translatable("advancements.dragonminez.strongestarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("strongestarmor", armorOf(MainItems.STRONGEST_ARMOR)
					).save(consumer, "dragonminez:strongestarmor");

			AdvancementHolder gokuwhisarmor = Advancement.Builder.advancement()
					.parent(patternsuper)
					.display(
							MainItems.GOKU_WHIS_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.gokuwhisarmor.title"),
							Component.translatable("advancements.dragonminez.gokuwhisarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("gokuwhisarmor", armorOf(MainItems.GOKU_WHIS_ARMOR)
					).save(consumer, "dragonminez:gokuwhisarmor");

			AdvancementHolder vegetasuperarmor = Advancement.Builder.advancement()
					.parent(patternsuper)
					.display(
							MainItems.VEGETA_SUPER_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.vegetasuperarmor.title"),
							Component.translatable("advancements.dragonminez.vegetasuperarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("vegetasuperarmor", armorOf(MainItems.VEGETA_SUPER_ARMOR))
					.addCriterion("vegetasuperarmor_whis", armorOf(MainItems.VEGETA_WHIS_ARMOR))
					.requirements(AdvancementRequirements.Strategy.OR)
					.save(consumer, "dragonminez:vegetasuperarmor");

			AdvancementHolder gammasarmor = Advancement.Builder.advancement()
					.parent(patternsuper)
					.display(
							MainItems.GAMMA1_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.gammasarmor.title"),
							Component.translatable("advancements.dragonminez.gammasarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("gammasarmor", armorOf(MainItems.GAMMA1_ARMOR))
					.addCriterion("gammasarmor_2", armorOf(MainItems.GAMMA2_ARMOR))
					.requirements(AdvancementRequirements.Strategy.OR)
					.save(consumer, "dragonminez:gammasarmor");

			AdvancementHolder granolaarmor = Advancement.Builder.advancement()
					.parent(patternsuper)
					.display(
							MainItems.GRANOLA_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.granolaarmor.title"),
							Component.translatable("advancements.dragonminez.granolaarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("granolaarmor", armorOf(MainItems.GRANOLA_ARMOR)
					).save(consumer, "dragonminez:granolaarmor");

			AdvancementHolder age1000armor = Advancement.Builder.advancement()
					.parent(patternsuper)
					.display(
							MainItems.AGE1000_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.age1000armor.title"),
							Component.translatable("advancements.dragonminez.age1000armor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("age1000armor", armorOf(MainItems.AGE1000_ARMOR)
					).save(consumer, "dragonminez:age1000armor");

			AdvancementHolder ginearmor = Advancement.Builder.advancement()
					.parent(patternsuper)
					.display(
							MainItems.GINE_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.ginearmor.title"),
							Component.translatable("advancements.dragonminez.ginearmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("ginearmor", armorOf(MainItems.GINE_ARMOR)
					).save(consumer, "dragonminez:ginearmor");

			AdvancementHolder kalearmor = Advancement.Builder.advancement()
					.parent(patternsuper)
					.display(
							MainItems.KALE_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.kalearmor.title"),
							Component.translatable("advancements.dragonminez.kalearmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("kalearmor", armorOf(MainItems.KALE_ARMOR)
					).save(consumer, "dragonminez:kalearmor");

			AdvancementHolder cauliflaarmor = Advancement.Builder.advancement()
					.parent(patternsuper)
					.display(
							MainItems.CAULIFLA_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.cauliflaarmor.title"),
							Component.translatable("advancements.dragonminez.cauliflaarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("cauliflaarmor", armorOf(MainItems.CAULIFLA_ARMOR)
					).save(consumer, "dragonminez:cauliflaarmor");

			AdvancementHolder beerusarmor = Advancement.Builder.advancement()
					.parent(patternsuper)
					.display(
							MainItems.BEERUS_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.beerusarmor.title"),
							Component.translatable("advancements.dragonminez.beerusarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("beerusarmor", armorOf(MainItems.BEERUS_ARMOR)
					).save(consumer, "dragonminez:beerusarmor");

			AdvancementHolder keflaarmor = Advancement.Builder.advancement()
					.parent(patternsuper)
					.display(
							MainItems.KEFLA_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.keflaarmor.title"),
							Component.translatable("advancements.dragonminez.keflaarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("keflaarmor", armorOf(MainItems.KEFLA_ARMOR)
					).save(consumer, "dragonminez:keflaarmor");

			AdvancementHolder majin21armor = Advancement.Builder.advancement()
					.parent(patternsuper)
					.display(
							MainItems.MAJIN21_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.majin21armor.title"),
							Component.translatable("advancements.dragonminez.majin21armor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("majin21armor", armorOf(MainItems.MAJIN21_ARMOR)
					).save(consumer, "dragonminez:majin21armor");

			AdvancementHolder whisarmor = Advancement.Builder.advancement()
					.parent(patternsuper)
					.display(
							MainItems.WHIS_ARMOR.get(ArmorItem.Type.CHESTPLATE).get(),
							Component.translatable("advancements.dragonminez.whisarmor.title"),
							Component.translatable("advancements.dragonminez.whisarmor.description"),
							null, FrameType.TASK, true, true, false
					).addCriterion("whisarmor", armorOf(MainItems.WHIS_ARMOR)
					).save(consumer, "dragonminez:whisarmor");
		}

		private static Criterion<PlayerTrigger.TriggerInstance> inStructure(String id) {
			return PlayerTrigger.TriggerInstance.located(
					LocationPredicate.Builder.inStructure(
							ResourceKey.create(Registries.STRUCTURE,
									new ResourceLocation(Reference.MOD_ID, id))));
		}

		private static Criterion<InventoryChangeTrigger.TriggerInstance> armorOf(Map<ArmorItem.Type, RegistryObject<Item>> set) {
			return InventoryChangeTrigger.TriggerInstance.hasItems(
					set.get(ArmorItem.Type.CHESTPLATE).get(),
					set.get(ArmorItem.Type.LEGGINGS).get(),
					set.get(ArmorItem.Type.BOOTS).get());
		}
	}
}