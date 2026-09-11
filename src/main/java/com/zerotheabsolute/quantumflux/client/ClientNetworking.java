package com.zerotheabsolute.quantumflux.client;

import com.zerotheabsolute.quantumflux.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

final class ClientNetworking {
    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PylonSyncPayload.TYPE, (payload, ctx) -> ctx.client().execute(() -> ClientPayloadBridge.handle(payload)));
        ClientPlayNetworking.registerGlobalReceiver(PylonTelemetryPayload.TYPE, (payload, ctx) -> ctx.client().execute(() -> ClientPayloadBridge.handle(payload)));
        ClientPlayNetworking.registerGlobalReceiver(NetworkListSyncS2CPayload.TYPE, (payload, ctx) -> ctx.client().execute(() -> ClientPayloadBridge.handle(payload)));
        ClientPlayNetworking.registerGlobalReceiver(NetworkTelemetryPayload.TYPE, (payload, ctx) -> ctx.client().execute(() -> ClientPayloadBridge.handle(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ActionResultS2CPayload.TYPE, (payload, ctx) -> ctx.client().execute(() -> ClientPayloadBridge.handle(payload)));
        ClientPlayNetworking.registerGlobalReceiver(OpenPylonPayload.TYPE, (payload, ctx) -> ctx.client().execute(() -> ClientScreenBridge.openPylonScreen(payload.pos())));
    }
}
