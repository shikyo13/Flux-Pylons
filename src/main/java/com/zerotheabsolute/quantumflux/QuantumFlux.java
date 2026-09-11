package com.zerotheabsolute.quantumflux;

import com.mojang.logging.LogUtils;
import com.zerotheabsolute.quantumflux.event.GadgetInteractionHandler;
import com.zerotheabsolute.quantumflux.init.QFBlockEntities;
import com.zerotheabsolute.quantumflux.init.QFBlocks;
import com.zerotheabsolute.quantumflux.init.QFCreativeTab;
import com.zerotheabsolute.quantumflux.init.QFItems;
import com.zerotheabsolute.quantumflux.init.QFSounds;
import com.zerotheabsolute.quantumflux.init.QFMenus;
import com.zerotheabsolute.quantumflux.network.QFNetworking;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;

@Mod(QuantumFlux.MODID)
public class QuantumFlux {

    public static final String MODID = "quantumflux";
    private static final Logger LOGGER = LogUtils.getLogger();

    public QuantumFlux() {
        IEventBus modEventBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        var modContainer = net.minecraftforge.fml.ModLoadingContext.get();
        LOGGER.info("Flux Pylons initializing");

        QFBlocks.BLOCKS.register(modEventBus);
        QFItems.ITEMS.register(modEventBus);
        QFBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        QFCreativeTab.CREATIVE_MODE_TABS.register(modEventBus);
        QFSounds.SOUNDS.register(modEventBus);
        QFMenus.MENUS.register(modEventBus);

        QFNetworking.register();

        MinecraftForge.EVENT_BUS.register(GadgetInteractionHandler.class);

        modContainer.registerConfig(ModConfig.Type.SERVER, QFConfig.SERVER_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, QFConfig.CLIENT_SPEC);
    }

}
