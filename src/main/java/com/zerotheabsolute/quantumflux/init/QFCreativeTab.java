package com.zerotheabsolute.quantumflux.init;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public final class QFCreativeTab {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, QuantumFlux.MODID);

    public static final RegistryObject<CreativeModeTab> QUANTUM_FLUX_TAB =
            CREATIVE_MODE_TABS.register("quantum_flux", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.quantumflux"))
                            .icon(() -> new ItemStack(QFBlocks.QUANTUM_PYLON.get()))
                            .displayItems((params, output) -> {
                                output.accept(QFBlocks.QUANTUM_PYLON.get());
                                output.accept(QFItems.QUANTUM_GADGET.get());
                                output.accept(QFItems.RANGE_UPGRADE.get());
                                output.accept(QFItems.CAPACITY_UPGRADE.get());
                                output.accept(QFItems.THROUGHPUT_UPGRADE.get());
                                output.accept(QFItems.BUFFER_UPGRADE.get());
                            })
                            .build());

    private QFCreativeTab() {}
}
