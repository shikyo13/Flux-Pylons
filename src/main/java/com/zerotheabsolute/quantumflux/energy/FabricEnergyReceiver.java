package com.zerotheabsolute.quantumflux.energy;

import team.reborn.energy.api.EnergyStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

/** Simulation aborts the Fabric transaction; delivery commits exactly the accepted amount. */
public record FabricEnergyReceiver(EnergyStorage storage) implements EnergyReceiver {
    @Override public int receiveEnergy(int amount, boolean simulate) {
        if (amount <= 0 || !canReceive()) return 0;
        try (Transaction transaction = Transaction.openOuter()) {
            long accepted = storage.insert(amount, transaction);
            if (accepted < 0 || accepted > amount) throw new IllegalStateException("Invalid energy insertion result: " + accepted);
            if (!simulate) transaction.commit();
            return (int) accepted;
        }
    }
    @Override public boolean canReceive() { return storage.supportsInsertion(); }
    @Override public int getEnergyStored() { return (int) com.zerotheabsolute.quantumflux.util.Numbers.clamp(storage.getAmount(), 0L, Integer.MAX_VALUE); }
    @Override public int getMaxEnergyStored() { return (int) com.zerotheabsolute.quantumflux.util.Numbers.clamp(storage.getCapacity(), 0L, Integer.MAX_VALUE); }
}
