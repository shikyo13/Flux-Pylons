package com.zerotheabsolute.quantumflux.data;

import com.zerotheabsolute.quantumflux.block.QuantumPylonBlock;
import com.zerotheabsolute.quantumflux.init.QFBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import net.minecraft.core.HolderLookup;
import java.util.concurrent.CompletableFuture;

public final class QFLootTableProvider extends FabricBlockLootTableProvider {
    public QFLootTableProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }
    @Override public void generate() {
        // Only the bottom half drops the pylon item.
        add(QFBlocks.QUANTUM_PYLON.get(), createSinglePropConditionTable(QFBlocks.QUANTUM_PYLON.get(),
                QuantumPylonBlock.HALF, QuantumPylonBlock.PylonHalf.BOTTOM));
    }
}
