package com.zerotheabsolute.quantumflux.energy;

import net.minecraftforge.energy.EnergyStorage;

import java.util.function.IntConsumer;

/**
 * Receive-only energy storage for the Flux Pylon.
 * Energy can only leave via wireless distribution, never via extraction.
 */
public class PylonEnergyStorage extends EnergyStorage {

    private static final IntConsumer NO_OP_LISTENER = ignored -> {};

    /** Receives the energy value from immediately before each committed mutation. */
    private final IntConsumer changeListener;
    private int nominalCapacity;

    public PylonEnergyStorage(int capacity) {
        this(capacity, 0, NO_OP_LISTENER);
    }

    public PylonEnergyStorage(int capacity, int energy) {
        this(capacity, energy, NO_OP_LISTENER);
    }

    public PylonEnergyStorage(int capacity, int energy, IntConsumer changeListener) {
        super(sanitizeCapacity(capacity), sanitizeCapacity(capacity), 0);
        this.nominalCapacity = this.capacity;
        this.energy = Math.max(0, energy);
        this.capacity = Math.max(nominalCapacity, this.energy);
        this.changeListener = changeListener == null ? NO_OP_LISTENER : changeListener;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        if (toReceive <= 0) return 0;
        int accepted = Math.max(0, Math.min(nominalCapacity - energy, toReceive));
        if (!simulate && accepted > 0) {
            int previousEnergy = energy;
            energy += accepted;
            changeListener.accept(previousEnergy);
        }
        return accepted;
    }

    @Override
    public boolean canReceive() {
        return nominalCapacity > 0;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    /**
     * Used internally by the block entity to deduct energy during wireless distribution.
     *
     * @return the amount actually removed
     */
    public int consumeEnergy(int amount) {
        if (amount <= 0 || energy <= 0) return 0;

        int removed = Math.min(amount, energy);
        int previousEnergy = energy;
        energy -= removed;
        capacity = Math.max(nominalCapacity, energy);
        changeListener.accept(previousEnergy);
        return removed;
    }

    /**
     * Sets a clamped internal value for network balancing.
     *
     * @return true when the stored value changed
     */
    public boolean setEnergy(int energy) {
        int clampedEnergy = clampEnergy(energy, capacity);
        if (clampedEnergy == this.energy) return false;

        int previousEnergy = this.energy;
        this.energy = clampedEnergy;
        capacity = Math.max(nominalCapacity, clampedEnergy);
        changeListener.accept(previousEnergy);
        return true;
    }

    /** Retain excess after a configuration reduction; reject new input until it drains. */
    public void setNominalCapacity(int capacity) {
        nominalCapacity = sanitizeCapacity(capacity);
        this.capacity = Math.max(nominalCapacity, energy);
    }

    public int getNominalCapacity() { return nominalCapacity; }

    private static int sanitizeCapacity(int capacity) {
        return Math.max(0, capacity);
    }

    private static int clampEnergy(int energy, int capacity) {
        return Math.max(0, Math.min(energy, capacity));
    }
}
