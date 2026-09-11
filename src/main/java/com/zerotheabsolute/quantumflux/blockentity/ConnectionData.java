package com.zerotheabsolute.quantumflux.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public class ConnectionData {

    private static final int MAX_DISPLAY_NAME_LENGTH = 96;

    private final BlockPos pos;
    private String displayName;
    private double lastTransferred;
    private long accumulatedTransferred;
    private boolean valid;

    public ConnectionData(BlockPos pos, String displayName) {
        this.pos = pos;
        this.displayName = sanitizeDisplayName(displayName);
        this.lastTransferred = 0;
        this.accumulatedTransferred = 0L;
        this.valid = true;
    }

    // ── Getters / Setters ──

    public BlockPos getPos() { return pos; }
    public String getDisplayName() { return displayName; }
    public boolean setDisplayName(String name) {
        String sanitized = sanitizeDisplayName(name);
        if (sanitized.equals(this.displayName)) return false;
        this.displayName = sanitized;
        return true;
    }
    public double getLastTransferred() { return lastTransferred; }
    public void recordTransfer(int amount) {
        if (amount > 0) accumulatedTransferred += amount;
    }
    public void rollTelemetryWindow(int ticks) {
        if (ticks <= 0) return;
        lastTransferred = Math.min(Integer.MAX_VALUE, (double) accumulatedTransferred / ticks);
        accumulatedTransferred = 0L;
    }
    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    // ── NBT ──

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Pos", pos.asLong());
        tag.putString("Name", displayName);
        return tag;
    }

    public static ConnectionData load(CompoundTag tag) {
        BlockPos pos = BlockPos.of(tag.getLong("Pos"));
        String name = sanitizeDisplayName(tag.getString("Name"));
        return new ConnectionData(pos, name);
    }

    private static String sanitizeDisplayName(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        StringBuilder sanitized = new StringBuilder(Math.min(value.length(), MAX_DISPLAY_NAME_LENGTH));
        value.codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint)
                        && Character.getType(codePoint) != Character.FORMAT)
                .limit(MAX_DISPLAY_NAME_LENGTH)
                .forEach(sanitized::appendCodePoint);
        return sanitized.isEmpty() ? "Unknown" : sanitized.toString();
    }
}
