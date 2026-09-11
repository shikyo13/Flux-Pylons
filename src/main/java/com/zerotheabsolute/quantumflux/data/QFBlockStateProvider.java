package com.zerotheabsolute.quantumflux.data;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import com.zerotheabsolute.quantumflux.block.QuantumPylonBlock;
import com.zerotheabsolute.quantumflux.init.QFBlocks;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

public class QFBlockStateProvider extends BlockStateProvider {

    public QFBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, QuantumFlux.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        ModelFile bottomOff    = models().getExistingFile(modLoc("block/quantum_pylon_bottom"));
        ModelFile bottomActive = models().getExistingFile(modLoc("block/quantum_pylon_bottom_active"));
        ModelFile topOff       = models().getExistingFile(modLoc("block/quantum_pylon_top"));
        ModelFile topActive    = models().getExistingFile(modLoc("block/quantum_pylon_top_active"));

        getVariantBuilder(QFBlocks.QUANTUM_PYLON.get()).forAllStates(state -> {
            boolean isActive = state.getValue(QuantumPylonBlock.ACTIVE);
            boolean isBottom = state.getValue(QuantumPylonBlock.HALF) == QuantumPylonBlock.PylonHalf.BOTTOM;

            ModelFile model;
            if (isBottom) {
                model = isActive ? bottomActive : bottomOff;
            } else {
                model = isActive ? topActive : topOff;
            }

            return ConfiguredModel.builder()
                    .modelFile(model)
                    .build();
        });
    }
}
