package com.zerotheabsolute.quantumflux.network;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Open the resolved pylon only after the server accepts the interaction and sends its state. */
public record OpenPylonPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<OpenPylonPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(QuantumFlux.MODID, "open_pylon"));
    public static final StreamCodec<FriendlyByteBuf, OpenPylonPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBlockPos(payload.pos), buffer -> new OpenPylonPayload(buffer.readBlockPos()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
