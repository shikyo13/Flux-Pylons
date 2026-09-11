package com.zerotheabsolute.quantumflux.data;

import com.zerotheabsolute.quantumflux.block.QuantumPylonBlock;
import com.zerotheabsolute.quantumflux.init.QFBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

// Note: Uses createSinglePropConditionTable to prevent dupe bug.
// Only the BOTTOM half drops the pylon item (same pattern as vanilla beds/doors).

public class QFLootTableProvider extends LootTableProvider {

    public QFLootTableProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, Set.of(), List.of(
                new SubProviderEntry(QFBlockLoot::new, LootContextParamSets.BLOCK)
        ), registries);
    }

    public static class QFBlockLoot extends BlockLootSubProvider {

        protected QFBlockLoot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
        }

        @Override
        protected void generate() {
            add(QFBlocks.QUANTUM_PYLON.get(), createSinglePropConditionTable(
                    QFBlocks.QUANTUM_PYLON.get(),
                    QuantumPylonBlock.HALF,
                    QuantumPylonBlock.PylonHalf.BOTTOM));
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return List.of(QFBlocks.QUANTUM_PYLON.get());
        }
    }
}
