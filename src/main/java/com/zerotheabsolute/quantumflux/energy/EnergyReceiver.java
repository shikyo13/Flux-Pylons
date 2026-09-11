package com.zerotheabsolute.quantumflux.energy;

/** Bounded receiver used by the shared distribution policy. */
public interface EnergyReceiver {
    int receiveEnergy(int amount, boolean simulate);
    boolean canReceive();
    int getEnergyStored();
    int getMaxEnergyStored();
}
