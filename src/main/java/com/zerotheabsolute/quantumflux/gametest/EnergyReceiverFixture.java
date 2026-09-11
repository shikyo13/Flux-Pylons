package com.zerotheabsolute.quantumflux.gametest;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.EnergyStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Development-only receiving capability. The entire gametest package is excluded from releases. */
@EventBusSubscriber(modid = QuantumFlux.MODID, bus = EventBusSubscriber.Bus.MOD)
public record EnergyReceiverFixture(BlockPos getBlockPos, Storage getEnergyStorage) {
    private static final Map<Level, Map<BlockPos, Storage>> RECEIVERS = new WeakHashMap<>();

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, blockEntity, side) -> {
                    Storage storage = RECEIVERS.getOrDefault(level, Map.of()).get(pos);
                    return storage != null && (side == null || storage.externalInputEnabled) ? storage : null;
                },
                Blocks.BARREL);
    }

    public static EnergyReceiverFixture place(GameTestHelper helper, BlockPos relativePos) {
        helper.setBlock(relativePos, Blocks.BARREL);
        BlockPos position = helper.absolutePos(relativePos);
        Storage storage = new Storage(100_000);
        RECEIVERS.computeIfAbsent(helper.getLevel(), ignored -> new HashMap<>()).put(position, storage);
        helper.getLevel().invalidateCapabilities(position);
        return new EnergyReceiverFixture(position, storage);
    }

    public static final class Storage extends EnergyStorage {
        public boolean externalInputEnabled = true;
        public boolean acceptingEnergy = true;

        private Storage(int capacity) {
            super(capacity);
        }

        public void setEnergy(int amount) {
            energy = Math.clamp(amount, 0, capacity);
        }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            return acceptingEnergy ? super.receiveEnergy(amount, simulate) : 0;
        }
    }
}
