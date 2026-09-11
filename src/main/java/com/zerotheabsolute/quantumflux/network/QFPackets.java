package com.zerotheabsolute.quantumflux.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import java.util.HashMap;
import java.util.Map;

public final class QFPackets {
    private static final Map<ResourceLocation, PacketCodec<FriendlyByteBuf, ?>> CODECS = new HashMap<>();
    private QFPackets() {}
    public static <T extends QFPayload> void register(QFPayload.Type<T> type, PacketCodec<FriendlyByteBuf, T> codec) {
        if (CODECS.putIfAbsent(type.id(), codec) != null) throw new IllegalStateException("Duplicate packet: " + type.id());
    }
    @SuppressWarnings("unchecked")
    public static FriendlyByteBuf encode(QFPayload payload) {
        var codec = (PacketCodec<FriendlyByteBuf, QFPayload>) CODECS.get(payload.type().id());
        if (codec == null) throw new IllegalArgumentException("Unknown packet: " + payload.type().id());
        FriendlyByteBuf buffer = PacketByteBufs.create();
        codec.encode(buffer, payload);
        return buffer;
    }
    public static void sendToPlayer(ServerPlayer player, QFPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.type().id())) ServerPlayNetworking.send(player, payload.type().id(), encode(payload));
    }
    public static void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos chunk, QFPayload payload) {
        for (ServerPlayer player : PlayerLookup.tracking(level, chunk)) sendToPlayer(player, payload);
    }
}
