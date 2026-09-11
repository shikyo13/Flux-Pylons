package com.zerotheabsolute.quantumflux.util;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Receiver observations made by the server, without guessing machine internals. */
public enum ConnectionStatus {
    TRANSFERRING, WAITING, NO_POWER, FULL, NOT_ACCEPTING, INPUT_UNAVAILABLE,
    UNLOADED, OUT_OF_RANGE, CONNECTION_LIMIT, PYLON_NETWORK, UNAVAILABLE, PAUSED;

    public boolean showsBeam() {
        return switch (this) {
            case TRANSFERRING, WAITING, NO_POWER, FULL, NOT_ACCEPTING, INPUT_UNAVAILABLE -> true;
            default -> false;
        };
    }

    public Component label() {
        return Component.translatable("screen.quantumflux.receiver." + name().toLowerCase(Locale.ROOT));
    }

    public Component hint() {
        return Component.translatable("screen.quantumflux.receiver." + name().toLowerCase(Locale.ROOT) + "_hint");
    }
}
