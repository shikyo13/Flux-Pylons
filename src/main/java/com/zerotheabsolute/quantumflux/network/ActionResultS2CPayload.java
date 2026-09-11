package com.zerotheabsolute.quantumflux.network;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Correlated server acknowledgement for every gadget-screen mutation. */
public record ActionResultS2CPayload(int requestId, Result result) implements CustomPacketPayload {

    public enum Result {
        APPLIED(true, "screen.quantumflux.result.applied"),
        NO_CHANGE(true, "screen.quantumflux.result.no_change"),
        INVALID_REQUEST(false, "screen.quantumflux.result.invalid_request"),
        RATE_LIMITED(false, "screen.quantumflux.result.rate_limited"),
        GADGET_REQUIRED(false, "screen.quantumflux.result.gadget_required"),
        NOT_ALLOWED(false, "screen.quantumflux.result.not_allowed"),
        NOT_FOUND(false, "screen.quantumflux.result.not_found"),
        OUT_OF_RANGE(false, "screen.quantumflux.result.out_of_range"),
        LIMIT_REACHED(false, "screen.quantumflux.result.limit_reached"),
        WRONG_PASSWORD(false, "screen.quantumflux.result.wrong_password"),
        PLAYER_NOT_FOUND(false, "screen.quantumflux.result.player_not_found");

        private final boolean success;
        private final String translationKey;

        Result(boolean success, String translationKey) {
            this.success = success;
            this.translationKey = translationKey;
        }

        public boolean success() { return success; }
        public String translationKey() { return translationKey; }
    }

    public static final Type<ActionResultS2CPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(QuantumFlux.MODID, "action_result"));
    public static final StreamCodec<FriendlyByteBuf, ActionResultS2CPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.requestId);
                buf.writeVarInt(payload.result.ordinal());
            },
            buf -> new ActionResultS2CPayload(buf.readVarInt(), readResult(buf))
    );

    private static Result readResult(FriendlyByteBuf buf) {
        int ordinal = buf.readVarInt();
        Result[] values = Result.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Result.INVALID_REQUEST;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
