package com.zerotheabsolute.quantumflux.data;

import com.zerotheabsolute.quantumflux.block.QuantumPylonBlock;
import com.zerotheabsolute.quantumflux.init.QFBlocks;
import com.zerotheabsolute.quantumflux.init.QFItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelTemplates;
import net.minecraft.resources.ResourceLocation;

public final class QFModelProvider extends FabricModelProvider {
    public QFModelProvider(FabricDataOutput output) { super(output); }

    @Override public void generateBlockStateModels(BlockModelGenerators generator) {
        generator.blockStateOutput.accept(MultiVariantGenerator.multiVariant(QFBlocks.QUANTUM_PYLON.get())
                .with(PropertyDispatch.properties(QuantumPylonBlock.ACTIVE, QuantumPylonBlock.HALF)
                        .generate((active, half) -> Variant.variant().with(VariantProperties.MODEL,
                                ResourceLocation.fromNamespaceAndPath("quantumflux", "block/quantum_pylon_"
                                        + half.getSerializedName() + (active ? "_active" : ""))))));
        // The pylon and gadget item geometry lives in src/main/resources.
        generator.skipAutoItemBlock(QFBlocks.QUANTUM_PYLON.get());
    }

    @Override public void generateItemModels(ItemModelGenerators generator) {
        generator.generateFlatItem(QFItems.RANGE_UPGRADE.get(), ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(QFItems.CAPACITY_UPGRADE.get(), ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(QFItems.THROUGHPUT_UPGRADE.get(), ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(QFItems.BUFFER_UPGRADE.get(), ModelTemplates.FLAT_ITEM);
    }
}
