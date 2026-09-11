package com.zerotheabsolute.quantumflux.inventory;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Vanilla menu inventory, retaining the pylon's existing item NBT layout. */
public class ItemInventory implements Container {
    protected final NonNullList<ItemStack> stacks;
    public ItemInventory(int size) { stacks = NonNullList.withSize(size, ItemStack.EMPTY); }
    public int getSlots() { return stacks.size(); }
    public ItemStack getStackInSlot(int slot) { return stacks.get(slot); }
    public void setStackInSlot(int slot, ItemStack stack) { stacks.set(slot, stack); onContentsChanged(slot); }
    public int getSlotLimit(int slot) { return 64; }
    public boolean isItemValid(int slot, ItemStack stack) { return true; }
    protected void onContentsChanged(int slot) {}
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !isItemValid(slot, stack)) return stack;
        ItemStack stored = stacks.get(slot);
        if (!stored.isEmpty() && !ItemStack.isSameItemSameComponents(stored, stack)) return stack;
        int accepted = Math.min(stack.getCount(), Math.max(0, Math.min(getSlotLimit(slot), stack.getMaxStackSize()) - stored.getCount()));
        if (accepted == 0) return stack;
        if (!simulate) {
            if (stored.isEmpty()) stacks.set(slot, stack.copyWithCount(accepted)); else stored.grow(accepted);
            onContentsChanged(slot);
        }
        return accepted == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - accepted);
    }
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || stacks.get(slot).isEmpty()) return ItemStack.EMPTY;
        ItemStack stored = stacks.get(slot);
        int removed = Math.min(amount, stored.getCount());
        ItemStack result = stored.copyWithCount(removed);
        if (!simulate) {
            if (removed == stored.getCount()) stacks.set(slot, ItemStack.EMPTY); else stored.shrink(removed);
            onContentsChanged(slot);
        }
        return result;
    }
    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag(); ListTag items = new ListTag();
        for (int slot = 0; slot < stacks.size(); slot++) if (!stacks.get(slot).isEmpty()) {
            CompoundTag item = (CompoundTag) stacks.get(slot).save(registries);
            item.putInt("Slot", slot); items.add(item);
        }
        tag.putInt("Size", stacks.size()); tag.put("Items", items); return tag;
    }
    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        stacks.clear(); ListTag items = tag.getList("Items", 10);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag item = items.getCompound(i); int slot = item.getInt("Slot");
            if (slot >= 0 && slot < stacks.size()) stacks.set(slot, ItemStack.parseOptional(registries, item));
        }
    }
    @Override public int getContainerSize() { return getSlots(); }
    @Override public boolean isEmpty() { return stacks.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return getStackInSlot(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { return extractItem(slot, amount, false); }
    @Override public ItemStack removeItemNoUpdate(int slot) { return stacks.set(slot, ItemStack.EMPTY); }
    @Override public void setItem(int slot, ItemStack stack) { setStackInSlot(slot, stack); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return isItemValid(slot, stack); }
    @Override public void setChanged() { for (int i = 0; i < stacks.size(); i++) onContentsChanged(i); }
    @Override public boolean stillValid(Player player) { return true; }
    @Override public void clearContent() { stacks.clear(); setChanged(); }
}
