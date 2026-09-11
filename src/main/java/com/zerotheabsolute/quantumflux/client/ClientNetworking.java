package com.zerotheabsolute.quantumflux.client;

import com.zerotheabsolute.quantumflux.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientNetworking {
    public static void sendToServer(QFPayload payload) {
        ClientPlayNetworking.send(payload.type().id(), QFPackets.encode(payload));
    }
    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PylonSyncPayload.TYPE.id(), (client, connection, buffer, response) -> {
            var payload = PylonSyncPayload.STREAM_CODEC.decode(buffer);
            client.execute(() -> ClientPayloadBridge.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(PylonTelemetryPayload.TYPE.id(), (client, connection, buffer, response) -> {
            var payload = PylonTelemetryPayload.STREAM_CODEC.decode(buffer);
            client.execute(() -> ClientPayloadBridge.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(NetworkListSyncS2CPayload.TYPE.id(), (client, connection, buffer, response) -> {
            var payload = NetworkListSyncS2CPayload.STREAM_CODEC.decode(buffer);
            client.execute(() -> ClientPayloadBridge.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(NetworkTelemetryPayload.TYPE.id(), (client, connection, buffer, response) -> {
            var payload = NetworkTelemetryPayload.STREAM_CODEC.decode(buffer);
            client.execute(() -> ClientPayloadBridge.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(ActionResultS2CPayload.TYPE.id(), (client, connection, buffer, response) -> {
            var payload = ActionResultS2CPayload.STREAM_CODEC.decode(buffer);
            client.execute(() -> ClientPayloadBridge.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(OpenPylonPayload.TYPE.id(), (client, connection, buffer, response) -> {
            var payload = OpenPylonPayload.STREAM_CODEC.decode(buffer);
            client.execute(() -> ClientScreenBridge.openPylonScreen(payload.pos()));
        });
    }
}
