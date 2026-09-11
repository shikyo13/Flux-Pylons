package com.zerotheabsolute.quantumflux.menu;

import com.zerotheabsolute.quantumflux.blockentity.QuantumPylonBlockEntity;
import com.zerotheabsolute.quantumflux.init.QFDataComponents;
import com.zerotheabsolute.quantumflux.init.QFItems;
import com.zerotheabsolute.quantumflux.init.QFMenus;
import com.zerotheabsolute.quantumflux.item.QuantumUpgradeItem;
import com.zerotheabsolute.quantumflux.item.UpgradeType;
import com.zerotheabsolute.quantumflux.network.QFNetworking;
import com.zerotheabsolute.quantumflux.network.data.QuantumFluxNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/** Vanilla inventory synchronization and click handling for the four upgrade slots. */
public final class PylonUpgradeMenu extends AbstractContainerMenu {
    private static final int DATA_COUNT = 16;
    private final BlockPos pos;
    @Nullable private final QuantumPylonBlockEntity pylon;
    private final ContainerData data;

    public PylonUpgradeMenu(int id, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(id, inventory, extra.readBlockPos(), null, new ItemStackHandler(4) {
            @Override public int getSlotLimit(int slot) { return UpgradeType.MAX_LEVEL; }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                return stack.getItem() instanceof QuantumUpgradeItem item && item.type().ordinal() == slot;
            }
        }, new SimpleContainerData(DATA_COUNT));
    }

    public PylonUpgradeMenu(int id, Inventory inventory, QuantumPylonBlockEntity pylon) {
        this(id, inventory, pylon.getBlockPos(), pylon, pylon.getUpgrades(), readings(pylon));
    }

    private PylonUpgradeMenu(int id, Inventory inventory, BlockPos pos, @Nullable QuantumPylonBlockEntity pylon,
                             ItemStackHandler upgrades, ContainerData data) {
        super(QFMenus.PYLON_UPGRADES.get(), id);
        this.pos = pos;
        this.pylon = pylon;
        this.data = data;
        for (int slot = 0; slot < 4; slot++) {
            addSlot(new SlotItemHandler(upgrades, slot, 19 + slot * 60, 39) {
                @Override public void setChanged() {
                    super.setChanged();
                    // Vanilla shift-click merges can grow the live stack without
                    // calling ItemStackHandler.setStackInSlot.
                    if (pylon != null) pylon.onUpgradesChanged();
                }
                @Override public boolean mayPickup(Player player) {
                    return (getSlotIndex() != UpgradeType.BUFFER.ordinal() || canRemoveBuffers()) && super.mayPickup(player);
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 41 + column * 18, 132 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 41 + column * 18, 192));
        addDataSlots(data);
    }

    private static ContainerData readings(QuantumPylonBlockEntity pylon) {
        return new ContainerData() {
            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> pylon.getEffectiveRange();
                    case 1 -> pylon.getEffectiveMaxConnections();
                    case 2, 3 -> part(pylon.getEffectiveTransferLimit(), index);
                    case 4, 5 -> part(pylon.getEffectiveBufferSize(), index);
                    case 6, 7 -> part(pylon.getEnergyStorage().getEnergyStored(), index);
                    case 8 -> pylon.getConnections().size();
                    case 9 -> pylon.canRemoveBufferUpgrades() ? 1 : 0;
                    case 10, 11 -> part(pylon.getBaseBufferSize(), index);
                    case 12, 13 -> part(com.zerotheabsolute.quantumflux.QFConfig.MAX_TRANSFER_PER_TICK.get(), index);
                    case 14 -> com.zerotheabsolute.quantumflux.QFConfig.DEFAULT_RANGE.get();
                    case 15 -> com.zerotheabsolute.quantumflux.QFConfig.MAX_CONNECTIONS.get();
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return DATA_COUNT; }
        };
    }

    private static int part(int value, int index) { return (value >>> ((index & 1) * 16)) & 0xFFFF; }
    private int combined(int index) { return (data.get(index) & 0xFFFF) | ((data.get(index + 1) & 0xFFFF) << 16); }
    public BlockPos pylonPos() { return pos; }
    public int range() { return data.get(0); }
    public int maxConnections() { return data.get(1); }
    public int transferLimit() { return combined(2); }
    public int bufferLimit() { return combined(4); }
    public int storedEnergy() { return combined(6); }
    public int connections() { return data.get(8); }
    public boolean canRemoveBuffers() { return data.get(9) != 0; }
    public int baseBufferLimit() { return combined(10); }

    public int previewValue(UpgradeType type, int additionalLevels) {
        int base = switch (type) {
            case RANGE -> data.get(14);
            case CAPACITY -> data.get(15);
            case THROUGHPUT -> combined(12);
            case BUFFER -> baseBufferLimit();
        };
        ItemStack stack = slots.get(type.ordinal()).getItem();
        int level = stack.getItem() instanceof QuantumUpgradeItem item && item.type() == type ? stack.getCount() : 0;
        return type.effectiveValue(base, level + additionalLevels);
    }

    @Override
    public boolean stillValid(Player player) {
        if (pylon == null) return true;
        if (player.level() != pylon.getLevel() || pylon.isRemoved() || !player.mayBuild()
                || !player.level().hasChunkAt(pos) || player.level().getBlockEntity(pos) != pylon
                || player.distanceToSqr(pos.getCenter()) > 64) return false;
        if (!activeGadget(player.getMainHandItem()) && !activeGadget(player.getOffhandItem())) return false;
        if (pylon.getNetworkId() == null) return true;
        var network = QuantumFluxNetworkManager.get((ServerLevel) player.level()).getNetwork(pylon.getNetworkId());
        return network != null && network.canConfigure(player.getUUID());
    }

    private static boolean activeGadget(ItemStack stack) {
        return stack.is(QFItems.QUANTUM_GADGET.get()) && Boolean.TRUE.equals(stack.get(QFDataComponents.GADGET_ACTIVE.get()));
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (!stillValid(player)) return;
        if (player instanceof ServerPlayer serverPlayer && pylon != null
                && !QFNetworking.canConfigurePylonNow(serverPlayer, pylon)) return;
        super.clicked(slotId, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack moving = slot.getItem();
        ItemStack original = moving.copy();
        boolean moved;
        if (index < 4) moved = moveItemStackTo(moving, 4, slots.size(), true);
        else if (moving.getItem() instanceof QuantumUpgradeItem upgrade) {
            int destination = upgrade.type().ordinal();
            moved = moveItemStackTo(moving, destination, destination + 1, false);
        } else if (index < 31) moved = moveItemStackTo(moving, 31, slots.size(), false);
        else moved = moveItemStackTo(moving, 4, 31, false);
        if (!moved || original.getCount() == moving.getCount()) return ItemStack.EMPTY;
        slot.set(moving.isEmpty() ? ItemStack.EMPTY : moving);
        slot.onTake(player, moving);
        return original;
    }
}
