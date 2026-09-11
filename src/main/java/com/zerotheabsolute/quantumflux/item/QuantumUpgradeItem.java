package com.zerotheabsolute.quantumflux.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;

public final class QuantumUpgradeItem extends Item {
    private final UpgradeType type;

    public QuantumUpgradeItem(UpgradeType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public UpgradeType type() { return type; }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.quantumflux.upgrade." + type.key()));
        lines.add(Component.translatable("tooltip.quantumflux.upgrade_limit", UpgradeType.MAX_LEVEL));
    }
}
