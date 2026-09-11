package com.zerotheabsolute.quantumflux.network;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.zerotheabsolute.quantumflux.network.PacketCodec;
import com.zerotheabsolute.quantumflux.network.QFPayload;
import net.minecraft.resources.ResourceLocation;

/** Open the resolved pylon only after the server accepts the interaction and sends its state. */
public record OpenPylonPayload(BlockPos pos) implements QFPayload {
    public static final Type<OpenPylonPayload> TYPE = new Type<>(
            new ResourceLocation(QuantumFlux.MODID, "open_pylon"));
    public static final PacketCodec<FriendlyByteBuf, OpenPylonPayload> STREAM_CODEC = PacketCodec.of(
            (buffer, payload) -> buffer.writeBlockPos(payload.pos), buffer -> new OpenPylonPayload(buffer.readBlockPos()));
    @Override public Type<? extends QFPayload> type() { return TYPE; }
}
