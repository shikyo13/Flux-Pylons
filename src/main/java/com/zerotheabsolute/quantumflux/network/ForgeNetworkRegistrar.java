package com.zerotheabsolute.quantumflux.network;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.SimpleChannel;
import java.util.function.BiConsumer;

public final class ForgeNetworkRegistrar {
    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(QuantumFlux.MODID, "main"))
            .networkProtocolVersion(3).simpleChannel();
    public <T extends CustomPacketPayload> void playToClient(Class<T> type, StreamCodec<FriendlyByteBuf, T> codec,
                                                            BiConsumer<T, ForgePayloadContext> handler) {
        register(type, codec, handler, NetworkDirection.PLAY_TO_CLIENT);
    }
    public <T extends CustomPacketPayload> void playToServer(Class<T> type, StreamCodec<FriendlyByteBuf, T> codec,
                                                            BiConsumer<T, ForgePayloadContext> handler) {
        register(type, codec, handler, NetworkDirection.PLAY_TO_SERVER);
    }
    private <T extends CustomPacketPayload> void register(Class<T> type, StreamCodec<FriendlyByteBuf, T> codec,
                                                         BiConsumer<T, ForgePayloadContext> handler,
                                                         NetworkDirection<RegistryFriendlyByteBuf> direction) {
        CHANNEL.messageBuilder(type, direction)
                .encoder((value, buffer) -> codec.encode(buffer, value)).decoder(codec::decode)
                .consumerNetworkThread((value, context) -> {
                    handler.accept(value, new ForgePayloadContext(context));
                    context.setPacketHandled(true);
                }).add();
    }
}
