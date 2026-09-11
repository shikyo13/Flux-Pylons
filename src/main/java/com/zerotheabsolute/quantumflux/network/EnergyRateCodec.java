package com.zerotheabsolute.quantumflux.network;

import net.minecraft.network.FriendlyByteBuf;

/** Exact twentieth-FE rates encoded as the integer FE measured over 20 ticks. */
final class EnergyRateCodec {
    private static final long MAX_SAMPLE = (long) Integer.MAX_VALUE * 20;

    private EnergyRateCodec() {}

    static void write(FriendlyByteBuf buffer, double rate) {
        double valid = Double.isFinite(rate) ? Math.clamp(rate, 0, Integer.MAX_VALUE) : 0;
        buffer.writeVarLong(Math.round(valid * 20));
    }

    static double read(FriendlyByteBuf buffer) {
        return Math.clamp(buffer.readVarLong(), 0, MAX_SAMPLE) / 20.0;
    }
}
