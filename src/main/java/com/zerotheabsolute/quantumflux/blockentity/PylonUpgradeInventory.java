package com.zerotheabsolute.quantumflux.blockentity;

import com.zerotheabsolute.quantumflux.item.QuantumUpgradeItem;
import com.zerotheabsolute.quantumflux.item.UpgradeType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/** Four typed slots owned by the pylon; buffer removal cannot discard stored FE. */
public final class PylonUpgradeInventory extends ItemStackHandler {
    private final QuantumPylonBlockEntity pylon;

    PylonUpgradeInventory(QuantumPylonBlockEntity pylon) {
        super(UpgradeType.values().length);
        this.pylon = pylon;
    }

    @Override public int getSlotLimit(int slot) { return UpgradeType.MAX_LEVEL; }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot >= 0 && slot < getSlots() && stack.getItem() instanceof QuantumUpgradeItem upgrade
                && upgrade.type().ordinal() == slot;
    }

    public int level(UpgradeType type) {
        ItemStack stack = getStackInSlot(type.ordinal());
        return isItemValid(type.ordinal(), stack) ? com.zerotheabsolute.quantumflux.util.Numbers.clamp(stack.getCount(), 0, UpgradeType.MAX_LEVEL) : 0;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot == UpgradeType.BUFFER.ordinal() && !pylon.canRemoveBufferUpgrades()) return ItemStack.EMPTY;
        return super.extractItem(slot, amount, simulate);
    }

    @Override
    protected void onContentsChanged(int slot) { pylon.onUpgradesChanged(); }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        CompoundTag bounded = tag.copy();
        bounded.putInt("Size", UpgradeType.values().length);
        super.deserializeNBT( bounded);
        // Unexpected old contents remain recoverable items, but grant no stats.
    }

    public void dropContents() {
        var level = pylon.getLevel();
        if (level == null || level.isClientSide) return;
        for (int slot = 0; slot < getSlots(); slot++) {
            ItemStack stack = stacks.set(slot, ItemStack.EMPTY);
            if (!stack.isEmpty()) net.minecraft.world.level.block.Block.popResource(level, pylon.getBlockPos(), stack);
        }
    }
}
