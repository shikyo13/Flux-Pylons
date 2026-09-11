package com.zerotheabsolute.quantumflux.energy;

import team.reborn.energy.api.EnergyStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;

import java.util.function.IntConsumer;

/**
 * Receive-only energy storage for the Flux Pylon.
 * Energy can only leave via wireless distribution, never via extraction.
 */
public class PylonEnergyStorage extends SnapshotParticipant<Integer> implements EnergyStorage, EnergyReceiver {

    private static final IntConsumer NO_OP_LISTENER = ignored -> {};

    /** Receives the energy value from immediately before each committed mutation. */
    private final IntConsumer changeListener;
    private int nominalCapacity;
    protected int capacity;
    protected int energy;
    private int committedEnergy;

    public PylonEnergyStorage(int capacity) {
        this(capacity, 0, NO_OP_LISTENER);
    }

    public PylonEnergyStorage(int capacity, int energy) {
        this(capacity, energy, NO_OP_LISTENER);
    }

    public PylonEnergyStorage(int capacity, int energy, IntConsumer changeListener) {
        this.capacity = sanitizeCapacity(capacity);
        this.nominalCapacity = this.capacity;
        this.energy = Math.max(0, energy);
        this.committedEnergy = this.energy;
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
            committedEnergy = this.energy;
            changeListener.accept(previousEnergy);
        }
        return accepted;
    }

    @Override
    public boolean canReceive() {
        return nominalCapacity > 0;
    }

    public boolean canExtract() {
        return false;
    }

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
        committedEnergy = this.energy;
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
        committedEnergy = this.energy;
        changeListener.accept(previousEnergy);
        return true;
    }

    /** Retain excess after a configuration reduction; reject new input until it drains. */
    public void setNominalCapacity(int capacity) {
        nominalCapacity = sanitizeCapacity(capacity);
        this.capacity = Math.max(nominalCapacity, energy);
    }

    public int getNominalCapacity() { return nominalCapacity; }

    @Override public long insert(long amount, TransactionContext transaction) {
        if (amount < 0) throw new IllegalArgumentException("Negative energy insertion");
        int accepted = (int) Math.min(Math.max(0L, (long) nominalCapacity - energy), amount);
        if (accepted > 0) { updateSnapshots(transaction); energy += accepted; }
        return accepted;
    }
    @Override public long extract(long amount, TransactionContext transaction) {
        if (amount < 0) throw new IllegalArgumentException("Negative energy extraction");
        return 0;
    }
    @Override public boolean supportsInsertion() { return canReceive(); }
    @Override public boolean supportsExtraction() { return false; }
    @Override public long getAmount() { return energy; }
    @Override public long getCapacity() { return capacity; }
    @Override public int getEnergyStored() { return energy; }
    @Override public int getMaxEnergyStored() { return capacity; }
    @Override protected Integer createSnapshot() { return energy; }
    @Override protected void readSnapshot(Integer value) { energy = value; capacity = Math.max(nominalCapacity, energy); }
    @Override protected void onFinalCommit() { int previous = committedEnergy; committedEnergy = energy; changeListener.accept(previous); }

    private static int sanitizeCapacity(int capacity) {
        return Math.max(0, capacity);
    }

    private static int clampEnergy(int energy, int capacity) {
        return Math.max(0, Math.min(energy, capacity));
    }
}
