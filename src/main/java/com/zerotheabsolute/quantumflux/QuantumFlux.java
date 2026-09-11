package com.zerotheabsolute.quantumflux;

import com.zerotheabsolute.quantumflux.event.GadgetInteractionHandler;
import com.zerotheabsolute.quantumflux.init.*;
import com.zerotheabsolute.quantumflux.network.QFNetworking;
import net.fabricmc.api.ModInitializer;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import net.neoforged.fml.config.ModConfig;
import team.reborn.energy.api.EnergyStorage;

public final class QuantumFlux implements ModInitializer {
    public static final String MODID = "quantumflux";
    @Override public void onInitialize() {
        QFDataComponents.register();
        QFBlocks.register();
        QFItems.register();
        QFBlockEntities.register();
        QFMenus.register();
        QFCreativeTab.register();
        QFSounds.register();
        EnergyStorage.SIDED.registerForBlockEntity((pylon, direction) -> pylon.getEnergyStorage(), QFBlockEntities.QUANTUM_PYLON_BE.get());
        NeoForgeConfigRegistry.INSTANCE.register(MODID, ModConfig.Type.SERVER, QFConfig.SERVER_SPEC);
        NeoForgeConfigRegistry.INSTANCE.register(MODID, ModConfig.Type.CLIENT, QFConfig.CLIENT_SPEC);
        QFNetworking.register();
        GadgetInteractionHandler.register();
    }
}
