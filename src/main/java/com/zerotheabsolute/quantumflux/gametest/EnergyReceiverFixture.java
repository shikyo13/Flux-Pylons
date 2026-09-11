package com.zerotheabsolute.quantumflux.gametest;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;


import net.minecraftforge.energy.EnergyStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Development-only receiving capability. The entire gametest package is excluded from releases. */
@EventBusSubscriber(modid = QuantumFlux.MODID, bus = EventBusSubscriber.Bus.FORGE)
public record EnergyReceiverFixture(BlockPos getBlockPos, Storage getEnergyStorage) {
    private static final Map<Level, Map<BlockPos, Storage>> RECEIVERS = new WeakHashMap<>();

    @SubscribeEvent
    public static void attach(net.minecraftforge.event.AttachCapabilitiesEvent<net.minecraft.world.level.block.entity.BlockEntity> event) {
        if (!(event.getObject() instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel)) return;
        event.addCapability(new net.minecraft.resources.ResourceLocation(QuantumFlux.MODID, "test_receiver"),
                new net.minecraftforge.common.capabilities.ICapabilityProvider() {
                    @Override
                    public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
                            net.minecraftforge.common.capabilities.Capability<T> capability,
                            @org.jetbrains.annotations.Nullable net.minecraft.core.Direction side) {
                        if (capability != net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY) return net.minecraftforge.common.util.LazyOptional.empty();
                        Storage storage = RECEIVERS.getOrDefault(barrel.getLevel(), Map.of()).get(barrel.getBlockPos());
                        if (storage == null || (side != null && !storage.externalInputEnabled)) return net.minecraftforge.common.util.LazyOptional.empty();
                        return net.minecraftforge.common.util.LazyOptional.of(() -> storage).cast();
                    }
                });
    }

    public static EnergyReceiverFixture place(GameTestHelper helper, BlockPos relativePos) {
        helper.setBlock(relativePos, Blocks.BARREL);
        BlockPos position = helper.absolutePos(relativePos);
        Storage storage = new Storage(100_000);
        RECEIVERS.computeIfAbsent(helper.getLevel(), ignored -> new HashMap<>()).put(position, storage);
        return new EnergyReceiverFixture(position, storage);
    }

    public static final class Storage extends EnergyStorage {
        public boolean externalInputEnabled = true;
        public boolean acceptingEnergy = true;

        private Storage(int capacity) {
            super(capacity);
        }

        public void setEnergy(int amount) {
            energy = com.zerotheabsolute.quantumflux.util.Numbers.clamp(amount, 0, capacity);
        }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            return acceptingEnergy ? super.receiveEnergy(amount, simulate) : 0;
        }
    }
}
