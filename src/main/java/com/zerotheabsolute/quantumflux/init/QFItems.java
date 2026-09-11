package com.zerotheabsolute.quantumflux.init;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import com.zerotheabsolute.quantumflux.item.QuantumGadgetItem;
import com.zerotheabsolute.quantumflux.item.QuantumUpgradeItem;
import com.zerotheabsolute.quantumflux.item.UpgradeType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class QFItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(QuantumFlux.MODID);

    public static final DeferredItem<BlockItem> QUANTUM_PYLON = ITEMS.registerSimpleBlockItem(QFBlocks.QUANTUM_PYLON);

    public static final DeferredItem<QuantumGadgetItem> QUANTUM_GADGET = ITEMS.register(
            "quantum_gadget",
            () -> new QuantumGadgetItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );

    public static final DeferredItem<QuantumUpgradeItem> RANGE_UPGRADE = upgrade("range_upgrade", UpgradeType.RANGE);
    public static final DeferredItem<QuantumUpgradeItem> CAPACITY_UPGRADE = upgrade("capacity_upgrade", UpgradeType.CAPACITY);
    public static final DeferredItem<QuantumUpgradeItem> THROUGHPUT_UPGRADE = upgrade("throughput_upgrade", UpgradeType.THROUGHPUT);
    public static final DeferredItem<QuantumUpgradeItem> BUFFER_UPGRADE = upgrade("buffer_upgrade", UpgradeType.BUFFER);

    private static DeferredItem<QuantumUpgradeItem> upgrade(String name, UpgradeType type) {
        return ITEMS.register(name, () -> new QuantumUpgradeItem(type,
                new Item.Properties().stacksTo(UpgradeType.MAX_LEVEL).rarity(Rarity.UNCOMMON)));
    }

    private QFItems() {}
}
