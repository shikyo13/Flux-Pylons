package com.zerotheabsolute.quantumflux.network;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import com.zerotheabsolute.quantumflux.util.GadgetAction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → Server: gadget settings change (beam style, color, priority, unlink, etc.)
 */
public record GadgetActionPayload(
        BlockPos pylonPos,
        GadgetAction action,
        BlockPos targetPos,
        int intPayload,
        int requestId
) implements CustomPacketPayload {

    public static final Type<GadgetActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(QuantumFlux.MODID, "gadget_action"));

    public static final StreamCodec<FriendlyByteBuf, GadgetActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pylonPos);
                buf.writeVarInt(p.action.ordinal());
                buf.writeBlockPos(p.targetPos);
                buf.writeInt(p.intPayload);
                buf.writeVarInt(p.requestId);
            },
            buf -> new GadgetActionPayload(
                    buf.readBlockPos(),
                    readAction(buf),
                    buf.readBlockPos(),
                    buf.readInt(),
                    buf.readVarInt()
            )
    );

    public GadgetActionPayload withRequestId(int id) {
        return new GadgetActionPayload(pylonPos, action, targetPos, intPayload, id);
    }

    private static GadgetAction readAction(FriendlyByteBuf buf) {
        int ordinal = buf.readVarInt();
        GadgetAction[] values = GadgetAction.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : GadgetAction.INVALID;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
