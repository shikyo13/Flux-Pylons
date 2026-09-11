package com.zerotheabsolute.quantumflux.util;

import net.minecraft.network.chat.Component;
import java.util.Locale;

/** Gates local wireless output; input and shared storage remain available. */
public enum RedstoneMode {
    IGNORE, WHEN_POWERED, WHEN_UNPOWERED;

    public boolean allows(boolean signal) {
        return switch (this) {
            case IGNORE -> true;
            case WHEN_POWERED -> signal;
            case WHEN_UNPOWERED -> !signal;
        };
    }

    public Component label() {
        return Component.translatable("screen.quantumflux.redstone.mode." + name().toLowerCase(Locale.ROOT));
    }
}
