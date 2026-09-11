package com.zerotheabsolute.quantumflux.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.PacketDistributor;

public final class ForgePacketDistributor {
    private ForgePacketDistributor() {}
    public static void sendToServer(QFPayload payload) {
        ForgeNetworkRegistrar.CHANNEL.sendToServer(payload);
    }
    public static void sendToPlayer(ServerPlayer player, QFPayload payload) {
        ForgeNetworkRegistrar.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }
    public static void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos position, QFPayload payload) {
        var chunk = level.getChunkSource().getChunkNow(position.x, position.z);
        if (chunk != null) {
            ForgeNetworkRegistrar.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), payload);
        }
    }
}
