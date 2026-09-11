package com.zerotheabsolute.quantumflux.data;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

public final class QFDataGenerator implements DataGeneratorEntrypoint {
    @Override public void onInitializeDataGenerator(FabricDataGenerator generator) {
        var pack = generator.createPack();
        pack.addProvider(QFModelProvider::new);
        pack.addProvider(QFLanguageProvider::new);
        pack.addProvider(QFBlockTagProvider::new);
        pack.addProvider(QFRecipeProvider::new);
        pack.addProvider(QFLootTableProvider::new);
    }
}
