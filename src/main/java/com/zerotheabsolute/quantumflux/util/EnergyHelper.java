package com.zerotheabsolute.quantumflux.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import com.zerotheabsolute.quantumflux.block.QuantumPylonBlock;
import team.reborn.energy.api.EnergyStorage;
import com.zerotheabsolute.quantumflux.energy.FabricEnergyReceiver;
import com.zerotheabsolute.quantumflux.energy.EnergyReceiver;
import org.jetbrains.annotations.Nullable;

public final class EnergyHelper {

    private EnergyHelper() {}

    @Nullable
    public static EnergyReceiver getEnergyCapability(Level level, BlockPos pos, @Nullable Direction direction) {
        if (level.isOutsideBuildHeight(pos)
                || !level.getWorldBorder().isWithinBounds(pos)
                || !level.hasChunkAt(pos)) {
            return null;
        }
        EnergyStorage storage = EnergyStorage.SIDED.find(level, pos, direction);
        return storage == null ? null : new FabricEnergyReceiver(storage);
    }

    public static boolean blockAcceptsEnergy(Level level, BlockPos pos) {
        // Wireless input is external automation, so honor exposed face controls.
        for (Direction dir : Direction.values()) {
            EnergyReceiver storage = getEnergyCapability(level, pos, dir);
            if (storage != null && storage.canReceive()) return true;
        }
        return false;
    }

    /** Pylons share through their network; wireless pylon links would circulate E. */
    public static boolean isWirelessReceiver(Level level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.hasChunkAt(pos) && !(level.getBlockState(pos).getBlock() instanceof QuantumPylonBlock);
    }
}
