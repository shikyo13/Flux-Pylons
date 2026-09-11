package com.zerotheabsolute.quantumflux.data;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import com.zerotheabsolute.quantumflux.init.QFBlocks;
import com.zerotheabsolute.quantumflux.init.QFItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;

public class QFRecipeProvider extends RecipeProvider {

    public QFRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    public void buildRecipes(RecipeOutput output) {
        // Flux Pylon: I E I / E D E / I R I
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, QFBlocks.QUANTUM_PYLON.get())
                .pattern("IEI")
                .pattern("EDE")
                .pattern("IRI")
                .define('I', Items.IRON_INGOT)
                .define('E', Items.ENDER_PEARL)
                .define('D', Items.DIAMOND)
                .define('R', Items.REDSTONE_BLOCK)
                .unlockedBy("has_ender_pearl", has(Items.ENDER_PEARL))
                .save(output);

        // Flux Gadget: _ A _ / I G I / _ R _
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, QFItems.QUANTUM_GADGET.get())
                .pattern(" A ")
                .pattern("IGI")
                .pattern(" R ")
                .define('A', Items.COPPER_INGOT)
                .define('I', Items.IRON_INGOT)
                .define('G', Items.GLASS_PANE)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_copper", has(Items.COPPER_INGOT))
                .save(output);
        upgrade(output, QFItems.RANGE_UPGRADE.get(), Items.IRON_INGOT, Items.ENDER_PEARL);
        wiredUpgrade(output, QFItems.CAPACITY_UPGRADE.get(), Items.IRON_INGOT);
        wiredUpgrade(output, QFItems.THROUGHPUT_UPGRADE.get(), Items.GOLD_INGOT);
        upgrade(output, QFItems.BUFFER_UPGRADE.get(), Items.DIAMOND, Items.REDSTONE_BLOCK);
    }

    private void upgrade(RecipeOutput output, net.minecraft.world.level.ItemLike result,
                         net.minecraft.world.level.ItemLike frame, net.minecraft.world.level.ItemLike core) {
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, result)
                .pattern(" M ").pattern("MCM").pattern(" M ")
                .define('M', frame).define('C', core)
                .unlockedBy("has_core", has(core)).save(output);
    }

    private void wiredUpgrade(RecipeOutput output, net.minecraft.world.level.ItemLike result,
                              net.minecraft.world.level.ItemLike frame) {
        // Copper contacts distinguish these grids from vanilla's compass and clock.
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, result)
                .pattern("CMC").pattern("MRM").pattern(" M ")
                .define('M', frame).define('C', Items.COPPER_INGOT).define('R', Items.REDSTONE)
                .unlockedBy("has_core", has(Items.REDSTONE)).save(output);
    }
}
