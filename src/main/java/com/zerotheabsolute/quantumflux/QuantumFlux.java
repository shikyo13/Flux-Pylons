package com.zerotheabsolute.quantumflux;

import com.mojang.logging.LogUtils;
import com.zerotheabsolute.quantumflux.event.GadgetInteractionHandler;
import com.zerotheabsolute.quantumflux.init.QFBlockEntities;
import com.zerotheabsolute.quantumflux.init.QFBlocks;
import com.zerotheabsolute.quantumflux.init.QFCreativeTab;
import com.zerotheabsolute.quantumflux.init.QFDataComponents;
import com.zerotheabsolute.quantumflux.init.QFItems;
import com.zerotheabsolute.quantumflux.init.QFSounds;
import com.zerotheabsolute.quantumflux.init.QFMenus;
import com.zerotheabsolute.quantumflux.network.QFNetworking;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(QuantumFlux.MODID)
public class QuantumFlux {

    public static final String MODID = "quantumflux";
    private static final Logger LOGGER = LogUtils.getLogger();

    public QuantumFlux(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Flux Pylons initializing");

        QFBlocks.BLOCKS.register(modEventBus);
        QFItems.ITEMS.register(modEventBus);
        QFBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        QFCreativeTab.CREATIVE_MODE_TABS.register(modEventBus);
        QFDataComponents.DATA_COMPONENTS.register(modEventBus);
        QFSounds.SOUNDS.register(modEventBus);
        QFMenus.MENUS.register(modEventBus);

        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(QFNetworking::register);

        NeoForge.EVENT_BUS.register(GadgetInteractionHandler.class);

        modContainer.registerConfig(ModConfig.Type.SERVER, QFConfig.SERVER_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, QFConfig.CLIENT_SPEC);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                QFBlockEntities.QUANTUM_PYLON_BE.get(),
                (be, direction) -> be.getEnergyStorage()
        );
    }
}
