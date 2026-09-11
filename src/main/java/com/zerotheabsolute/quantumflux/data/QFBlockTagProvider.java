package com.zerotheabsolute.quantumflux.data;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import com.zerotheabsolute.quantumflux.init.QFBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public class QFBlockTagProvider extends BlockTagsProvider {

    public QFBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                              ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, QuantumFlux.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(QFBlocks.QUANTUM_PYLON.get());
        tag(BlockTags.NEEDS_IRON_TOOL).add(QFBlocks.QUANTUM_PYLON.get());
    }
}
