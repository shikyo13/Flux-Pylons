package com.zerotheabsolute.quantumflux.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.PacketDistributor;

public final class ForgePacketDistributor {
    private ForgePacketDistributor() {}
    public static void sendToServer(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        ForgeNetworkRegistrar.CHANNEL.send(payload, PacketDistributor.SERVER.noArg());
    }
    public static void sendToPlayer(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        ForgeNetworkRegistrar.CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
    }
    public static void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos position, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        var chunk = level.getChunkSource().getChunkNow(position.x, position.z);
        if (chunk != null) {
            ForgeNetworkRegistrar.CHANNEL.send(payload, PacketDistributor.TRACKING_CHUNK.with(chunk));
        }
    }
}
