package com.zerotheabsolute.quantumflux.data;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = QuantumFlux.MODID, bus = EventBusSubscriber.Bus.MOD)
public class QFDataGenerator {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator gen = event.getGenerator();
        PackOutput output = gen.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        gen.addProvider(event.includeClient(), new QFBlockStateProvider(output, existingFileHelper));
        gen.addProvider(event.includeClient(), new QFItemModelProvider(output, existingFileHelper));
        gen.addProvider(event.includeClient(), new QFLanguageProvider(output));

        QFBlockTagProvider blockTags = new QFBlockTagProvider(output, lookupProvider, existingFileHelper);
        gen.addProvider(event.includeServer(), blockTags);
        gen.addProvider(event.includeServer(), new QFRecipeProvider(output, lookupProvider));
        gen.addProvider(event.includeServer(), new QFLootTableProvider(output, lookupProvider));
    }
}
