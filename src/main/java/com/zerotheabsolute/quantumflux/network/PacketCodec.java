package com.zerotheabsolute.quantumflux.network;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** Typed packet serialization shared by the Forge message registrations. */
public record PacketCodec<B, T>(BiConsumer<B, T> encoder, Function<B, T> decoder) {
    public void encode(B buffer, T value) { encoder.accept(buffer, value); }
    public T decode(B buffer) { return decoder.apply(buffer); }
    public static <B, T> PacketCodec<B, T> of(BiConsumer<B, T> encoder, Function<B, T> decoder) {
        return new PacketCodec<>(encoder, decoder);
    }
}
