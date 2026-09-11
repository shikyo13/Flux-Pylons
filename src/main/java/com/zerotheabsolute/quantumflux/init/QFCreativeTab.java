package com.zerotheabsolute.quantumflux.init;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class QFCreativeTab {

    private static final RegistryEntries<CreativeModeTab> CREATIVE_MODE_TABS = new RegistryEntries<>(BuiltInRegistries.CREATIVE_MODE_TAB);

    public static final Supplier<CreativeModeTab> QUANTUM_FLUX_TAB =
            CREATIVE_MODE_TABS.register("quantum_flux", () ->
                    net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup.builder()
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

    public static void register() {}
    private QFCreativeTab() {}
}
