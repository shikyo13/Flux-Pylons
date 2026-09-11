package com.zerotheabsolute.quantumflux.init;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import com.zerotheabsolute.quantumflux.item.QuantumGadgetItem;
import com.zerotheabsolute.quantumflux.item.QuantumUpgradeItem;
import com.zerotheabsolute.quantumflux.item.UpgradeType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public final class QFItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ITEMS, QuantumFlux.MODID);

    public static final RegistryObject<BlockItem> QUANTUM_PYLON = ITEMS.register("quantum_pylon", () -> new BlockItem(QFBlocks.QUANTUM_PYLON.get(), new Item.Properties()));

    public static final RegistryObject<QuantumGadgetItem> QUANTUM_GADGET = ITEMS.register(
            "quantum_gadget",
            () -> new QuantumGadgetItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );

    public static final RegistryObject<QuantumUpgradeItem> RANGE_UPGRADE = upgrade("range_upgrade", UpgradeType.RANGE);
    public static final RegistryObject<QuantumUpgradeItem> CAPACITY_UPGRADE = upgrade("capacity_upgrade", UpgradeType.CAPACITY);
    public static final RegistryObject<QuantumUpgradeItem> THROUGHPUT_UPGRADE = upgrade("throughput_upgrade", UpgradeType.THROUGHPUT);
    public static final RegistryObject<QuantumUpgradeItem> BUFFER_UPGRADE = upgrade("buffer_upgrade", UpgradeType.BUFFER);

    private static RegistryObject<QuantumUpgradeItem> upgrade(String name, UpgradeType type) {
        return ITEMS.register(name, () -> new QuantumUpgradeItem(type,
                new Item.Properties().stacksTo(UpgradeType.MAX_LEVEL).rarity(Rarity.UNCOMMON)));
    }

    private QFItems() {}
}
