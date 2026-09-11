package com.zerotheabsolute.quantumflux.network;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import java.util.function.BiConsumer;

public final class ForgeNetworkRegistrar {
    private static final String PROTOCOL = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(QuantumFlux.MODID, "main"), () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);
    private int nextId;

    public <T extends QFPayload> void playToClient(Class<T> type, PacketCodec<FriendlyByteBuf, T> codec,
                                                 BiConsumer<T, ForgePayloadContext> handler) {
        register(type, codec, handler, NetworkDirection.PLAY_TO_CLIENT);
    }
    public <T extends QFPayload> void playToServer(Class<T> type, PacketCodec<FriendlyByteBuf, T> codec,
                                                 BiConsumer<T, ForgePayloadContext> handler) {
        register(type, codec, handler, NetworkDirection.PLAY_TO_SERVER);
    }
    private <T extends QFPayload> void register(Class<T> type, PacketCodec<FriendlyByteBuf, T> codec,
                                               BiConsumer<T, ForgePayloadContext> handler, NetworkDirection direction) {
        CHANNEL.registerMessage(nextId++, type, (value, buffer) -> codec.encode(buffer, value), codec::decode,
                (value, supplier) -> {
                    var context = supplier.get();
                    handler.accept(value, new ForgePayloadContext(context));
                    context.setPacketHandled(true);
                }, Optional.of(direction));
    }
}
