package com.zerotheabsolute.quantumflux.item;

import java.util.Locale;

public enum UpgradeType {
    RANGE, CAPACITY, THROUGHPUT, BUFFER;

    public static final int MAX_LEVEL = 4;

    public int effectiveValue(int base, int level) {
        long value = Math.max(0, base);
        int count = com.zerotheabsolute.quantumflux.util.Numbers.clamp(level, 0, MAX_LEVEL);
        return switch (this) {
            case RANGE -> (int) Math.min(256, value + count * 4);
            case CAPACITY -> (int) Math.min(128, value + count * 2);
            case THROUGHPUT -> (int) Math.min(Integer.MAX_VALUE, value * (2 + count) / 2);
            case BUFFER -> (int) Math.min(Integer.MAX_VALUE, value << count);
        };
    }

    public String key() { return name().toLowerCase(Locale.ROOT); }
}
