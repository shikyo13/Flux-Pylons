package com.zerotheabsolute.quantumflux.network;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import com.zerotheabsolute.quantumflux.util.GadgetAction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.zerotheabsolute.quantumflux.network.PacketCodec;
import com.zerotheabsolute.quantumflux.network.QFPayload;
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
) implements QFPayload {

    public static final Type<GadgetActionPayload> TYPE =
            new Type<>(new ResourceLocation(QuantumFlux.MODID, "gadget_action"));

    public static final PacketCodec<FriendlyByteBuf, GadgetActionPayload> STREAM_CODEC = PacketCodec.of(
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
    public Type<? extends QFPayload> type() {
        return TYPE;
    }
}
