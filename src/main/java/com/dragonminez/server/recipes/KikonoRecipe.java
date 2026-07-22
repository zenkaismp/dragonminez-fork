package com.dragonminez.server.recipes;

import com.dragonminez.common.init.MainRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

import java.util.List;

public class KikonoRecipe implements Recipe<SimpleContainer> {
	private final ItemStack output;
	private final NonNullList<Ingredient> recipeItems;
	private final Ingredient pattern;
	private final Ingredient template;
	private final int craftingTime;
	private final int energyCost;

	public KikonoRecipe(ItemStack output, NonNullList<Ingredient> recipeItems, Ingredient pattern, Ingredient template, int craftingTime, int energyCost) {
		this.output = output;
		this.recipeItems = recipeItems;
		this.pattern = pattern;
		this.template = template;
		this.craftingTime = craftingTime;
		this.energyCost = energyCost;
	}


	@Override
	public boolean matches(SimpleContainer pContainer, Level pLevel) {
		if(pLevel.isClientSide()) return false;
		if (!pattern.test(pContainer.getItem(9))) return false;
		if (!template.test(pContainer.getItem(10))) return false;
		for (int i = 0; i < recipeItems.size(); i++) {
			if (!recipeItems.get(i).test(pContainer.getItem(i))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack assemble(SimpleContainer pContainer, RegistryAccess pRegistryAccess) {
		return output.copy();
	}

	@Override
	public boolean canCraftInDimensions(int pWidth, int pHeight) {
		return true;
	}

	@Override
	public ItemStack getResultItem(RegistryAccess pRegistryAccess) {
		return output.copy();
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return MainRecipes.KIKONO_SERIALIZER.get();
	}

	@Override
	public RecipeType<?> getType() {
		return MainRecipes.KIKONO_TYPE.get();
	}

	@Override
	public NonNullList<Ingredient> getIngredients() {
		NonNullList<Ingredient> allIngredients = NonNullList.create();
		allIngredients.addAll(recipeItems);
		allIngredients.add(pattern);
		allIngredients.add(template);
		return allIngredients;
	}

	public NonNullList<Ingredient> getInputs() {
		return this.recipeItems;
	}

	public Ingredient getPattern() {
		return this.pattern;
	}

	public Ingredient getTemplate() {
		return this.template;
	}

	public int getEnergyCost() {
		return this.energyCost;
	}

	public int getCraftingTime() {
		return this.craftingTime;
	}

	private static NonNullList<Ingredient> toNonNull(List<Ingredient> inputs) {
		NonNullList<Ingredient> list = NonNullList.withSize(9, Ingredient.EMPTY);
		for (int i = 0; i < inputs.size() && i < 9; i++) list.set(i, inputs.get(i));
		return list;
	}

	public static class Serializer implements RecipeSerializer<KikonoRecipe> {
		public static final Serializer INSTANCE = new Serializer();

		// {"item":"id","count":n} — matches KikonoRecipeBuilder's output/ingredient JSON.
		private static final Codec<ItemStack> OUTPUT_CODEC = RecordCodecBuilder.create(i -> i.group(
				BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(ItemStack::getItem),
				Codec.INT.optionalFieldOf("count", 1).forGetter(ItemStack::getCount)
		).apply(i, (item, count) -> new ItemStack(item, count)));

		public static final Codec<KikonoRecipe> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				OUTPUT_CODEC.fieldOf("output").forGetter(r -> r.output),
				Ingredient.CODEC.fieldOf("pattern").forGetter(r -> r.pattern),
				Ingredient.CODEC.fieldOf("template").forGetter(r -> r.template),
				Ingredient.CODEC.listOf().fieldOf("inputs").forGetter(r -> List.copyOf(r.recipeItems)),
				Codec.INT.optionalFieldOf("crafting_time", 100).forGetter(r -> r.craftingTime),
				Codec.INT.optionalFieldOf("energy_cost", 1000).forGetter(r -> r.energyCost)
		).apply(inst, (output, pattern, template, inputs, time, energy) ->
				new KikonoRecipe(output, toNonNull(inputs), pattern, template, time, energy)));

		@Override
		public Codec<KikonoRecipe> codec() {
			return CODEC;
		}

		@Override
		public KikonoRecipe fromNetwork(FriendlyByteBuf pBuffer) {
			ItemStack output = pBuffer.readItem();
			Ingredient pattern = Ingredient.fromNetwork(pBuffer);
			Ingredient template = Ingredient.fromNetwork(pBuffer);
			int time = pBuffer.readInt();
			int energy = pBuffer.readInt();

			NonNullList<Ingredient> inputs = NonNullList.withSize(9, Ingredient.EMPTY);
			for (int i = 0; i < 9; i++) {
				inputs.set(i, Ingredient.fromNetwork(pBuffer));
			}

			return new KikonoRecipe(output, inputs, pattern, template, time, energy);
		}

		@Override
		public void toNetwork(FriendlyByteBuf pBuffer, KikonoRecipe pRecipe) {
			pBuffer.writeItemStack(pRecipe.output, false);
			pRecipe.pattern.toNetwork(pBuffer);
			pRecipe.template.toNetwork(pBuffer);
			pBuffer.writeInt(pRecipe.craftingTime);
			pBuffer.writeInt(pRecipe.energyCost);

			for (Ingredient ing : pRecipe.recipeItems) {
				ing.toNetwork(pBuffer);
			}
		}
	}
}
