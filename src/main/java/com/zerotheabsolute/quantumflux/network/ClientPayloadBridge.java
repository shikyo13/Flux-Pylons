package com.zerotheabsolute.quantumflux.network;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Distribution-neutral bridge for S2C payload delivery. The physical client
 * installs handlers during client setup, while dedicated servers retain no-op
 * consumers and never resolve classes containing net.minecraft.client types.
 */
public final class ClientPayloadBridge {

    private static Consumer<PylonSyncPayload> pylonSyncHandler = ignored -> {};
    private static Consumer<PylonTelemetryPayload> pylonTelemetryHandler = ignored -> {};
    private static Consumer<NetworkListSyncS2CPayload> networkListHandler = ignored -> {};
    private static Consumer<NetworkTelemetryPayload> networkTelemetryHandler = ignored -> {};
    private static Consumer<ActionResultS2CPayload> actionResultHandler = ignored -> {};

    private ClientPayloadBridge() {}

    public static void install(Consumer<PylonSyncPayload> pylonHandler,
                               Consumer<PylonTelemetryPayload> telemetryHandler,
                               Consumer<NetworkListSyncS2CPayload> networkHandler,
                               Consumer<NetworkTelemetryPayload> networkReadingsHandler,
                               Consumer<ActionResultS2CPayload> resultHandler) {
        pylonSyncHandler = Objects.requireNonNull(pylonHandler);
        pylonTelemetryHandler = Objects.requireNonNull(telemetryHandler);
        networkListHandler = Objects.requireNonNull(networkHandler);
        networkTelemetryHandler = Objects.requireNonNull(networkReadingsHandler);
        actionResultHandler = Objects.requireNonNull(resultHandler);
    }

    public static void handle(PylonSyncPayload payload) {
        pylonSyncHandler.accept(payload);
    }

    public static void handle(PylonTelemetryPayload payload) {
        pylonTelemetryHandler.accept(payload);
    }

    public static void handle(NetworkListSyncS2CPayload payload) {
        networkListHandler.accept(payload);
    }

    public static void handle(NetworkTelemetryPayload payload) {
        networkTelemetryHandler.accept(payload);
    }

    public static void handle(ActionResultS2CPayload payload) {
        actionResultHandler.accept(payload);
    }
}
