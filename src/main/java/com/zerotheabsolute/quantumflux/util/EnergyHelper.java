package com.zerotheabsolute.quantumflux.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import com.zerotheabsolute.quantumflux.block.QuantumPylonBlock;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

public final class EnergyHelper {

    private EnergyHelper() {}

    @Nullable
    public static IEnergyStorage getEnergyCapability(Level level, BlockPos pos, @Nullable Direction direction) {
        if (level.isOutsideBuildHeight(pos)
                || !level.getWorldBorder().isWithinBounds(pos)
                || !level.hasChunkAt(pos)) {
            return null;
        }
        var blockEntity = level.getBlockEntity(pos);
        return blockEntity == null ? null : blockEntity.getCapability(ForgeCapabilities.ENERGY, direction).orElse(null);
    }

    public static boolean blockAcceptsEnergy(Level level, BlockPos pos) {
        // Wireless input is external automation, so honor exposed face controls.
        for (Direction dir : Direction.values()) {
            IEnergyStorage storage = getEnergyCapability(level, pos, dir);
            if (storage != null && storage.canReceive()) return true;
        }
        return false;
    }

    /** Pylons share through their network; wireless pylon links would circulate FE. */
    public static boolean isWirelessReceiver(Level level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.hasChunkAt(pos) && !(level.getBlockState(pos).getBlock() instanceof QuantumPylonBlock);
    }
}
