package com.zerotheabsolute.quantumflux.gametest;

import com.zerotheabsolute.quantumflux.energy.FabricEnergyReceiver;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.base.SimpleEnergyStorage;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public record EnergyReceiverFixture(BlockPos getBlockPos, Storage getEnergyStorage) {
    private static final Map<Level, Map<BlockPos, Storage>> RECEIVERS = new WeakHashMap<>();
    public static void register() {
        EnergyStorage.SIDED.registerForBlocks((level, pos, state, blockEntity, side) -> {
            Storage storage = RECEIVERS.getOrDefault(level, Map.of()).get(pos);
            return storage != null && (side == null || storage.externalInputEnabled) ? storage : null;
        }, Blocks.BARREL);
    }
    public static EnergyReceiverFixture place(GameTestHelper helper, BlockPos relativePos) {
        helper.setBlock(relativePos, Blocks.BARREL);
        BlockPos position = helper.absolutePos(relativePos);
        Storage storage = new Storage(100_000);
        RECEIVERS.computeIfAbsent(helper.getLevel(), ignored -> new HashMap<>()).put(position, storage);
        return new EnergyReceiverFixture(position, storage);
    }
    public static final class Storage extends SimpleEnergyStorage {
        public boolean externalInputEnabled = true;
        public boolean acceptingEnergy = true;
        private Storage(int capacity) { super(capacity, capacity, 0); }
        public void setEnergy(int energy) { amount = com.zerotheabsolute.quantumflux.util.Numbers.clamp(energy, 0, capacity); }
        public int getEnergyStored() { return (int) amount; }
        public int getMaxEnergyStored() { return (int) capacity; }
        public int receiveEnergy(int amount, boolean simulate) { return new FabricEnergyReceiver(this).receiveEnergy(amount, simulate); }
        @Override public long insert(long amount, TransactionContext transaction) {
            return acceptingEnergy ? super.insert(amount, transaction) : 0;
        }
    }
}
