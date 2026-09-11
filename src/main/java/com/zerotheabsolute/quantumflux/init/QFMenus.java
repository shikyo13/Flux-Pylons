package com.zerotheabsolute.quantumflux.init;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import com.zerotheabsolute.quantumflux.menu.PylonUpgradeMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class QFMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, QuantumFlux.MODID);
    public static final DeferredHolder<MenuType<?>, MenuType<PylonUpgradeMenu>> PYLON_UPGRADES =
            MENUS.register("pylon_upgrades", () -> IMenuTypeExtension.create(PylonUpgradeMenu::new));
    private QFMenus() {}
}
