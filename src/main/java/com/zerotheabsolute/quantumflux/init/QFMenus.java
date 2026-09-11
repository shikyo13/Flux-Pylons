package com.zerotheabsolute.quantumflux.init;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.function.Supplier;
import com.zerotheabsolute.quantumflux.menu.PylonUpgradeMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

public final class QFMenus {
    private static final RegistryEntries<MenuType<?>> MENUS = new RegistryEntries<>(BuiltInRegistries.MENU);
    public static final Supplier<MenuType<PylonUpgradeMenu>> PYLON_UPGRADES =
            MENUS.register("pylon_upgrades", () -> new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(PylonUpgradeMenu::new, net.minecraft.core.BlockPos.STREAM_CODEC));
    public static void register() {}
    private QFMenus() {}
}
