package com.zerotheabsolute.quantumflux.init;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.function.Supplier;
import com.zerotheabsolute.quantumflux.item.QuantumGadgetItem;
import com.zerotheabsolute.quantumflux.item.QuantumUpgradeItem;
import com.zerotheabsolute.quantumflux.item.UpgradeType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class QFItems {

    private static final RegistryEntries<Item> ITEMS = new RegistryEntries<>(BuiltInRegistries.ITEM);

    public static final Supplier<BlockItem> QUANTUM_PYLON = ITEMS.register("quantum_pylon", () -> new BlockItem(QFBlocks.QUANTUM_PYLON.get(), new Item.Properties()));

    public static final Supplier<QuantumGadgetItem> QUANTUM_GADGET = ITEMS.register(
            "quantum_gadget",
            () -> new QuantumGadgetItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );

    public static final Supplier<QuantumUpgradeItem> RANGE_UPGRADE = upgrade("range_upgrade", UpgradeType.RANGE);
    public static final Supplier<QuantumUpgradeItem> CAPACITY_UPGRADE = upgrade("capacity_upgrade", UpgradeType.CAPACITY);
    public static final Supplier<QuantumUpgradeItem> THROUGHPUT_UPGRADE = upgrade("throughput_upgrade", UpgradeType.THROUGHPUT);
    public static final Supplier<QuantumUpgradeItem> BUFFER_UPGRADE = upgrade("buffer_upgrade", UpgradeType.BUFFER);

    private static Supplier<QuantumUpgradeItem> upgrade(String name, UpgradeType type) {
        return ITEMS.register(name, () -> new QuantumUpgradeItem(type,
                new Item.Properties().stacksTo(UpgradeType.MAX_LEVEL).rarity(Rarity.UNCOMMON)));
    }

    public static void register() {}
    private QFItems() {}
}
