package com.zerotheabsolute.quantumflux.init;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import com.zerotheabsolute.quantumflux.menu.PylonUpgradeMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public final class QFMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, QuantumFlux.MODID);
    public static final RegistryObject<MenuType<PylonUpgradeMenu>> PYLON_UPGRADES =
            MENUS.register("pylon_upgrades", () -> IForgeMenuType.create(PylonUpgradeMenu::new));
    private QFMenus() {}
}
