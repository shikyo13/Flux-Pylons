package com.zerotheabsolute.quantumflux.client;

import com.zerotheabsolute.quantumflux.network.NetworkListSyncS2CPayload;
import com.zerotheabsolute.quantumflux.network.NetworkTelemetryPayload;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-side cache of network summaries received from the server.
 * Read by the gadget screen to display network list.
 */
public final class ClientNetworkCache {

    private static List<NetworkListSyncS2CPayload.NetworkSummary> networks = Collections.emptyList();
    private static final int MAX_CACHED_NETWORKS = 8;
    private static final int HISTORY_SAMPLES = 60;
    private static final Map<UUID, LiveNetwork> LIVE = new LinkedHashMap<>();

    public record HistoryPoint(long sampleTick, double throughput) {}

    private static final class LiveNetwork {
        NetworkTelemetryPayload snapshot;
        long lastClientTick;
        long latestSample = Long.MIN_VALUE;
        final ArrayDeque<HistoryPoint> history = new ArrayDeque<>();
    }

    private ClientNetworkCache() {}

    public static void receive(List<NetworkListSyncS2CPayload.NetworkSummary> incoming) {
        networks = List.copyOf(incoming);
        LIVE.keySet().removeIf(id -> {
            var network = getNetwork(id);
            return network == null || (!network.isMember() && network.accessMode() != 1);
        });
    }

    public static void receiveTelemetry(NetworkTelemetryPayload payload, long clientTick) {
        LiveNetwork data = LIVE.computeIfAbsent(payload.networkId(), ignored -> new LiveNetwork());
        if (payload.sampleTick() < data.latestSample) return;
        data.snapshot = payload;
        data.lastClientTick = clientTick;
        if (payload.sampleTick() > data.latestSample) {
            if (!data.history.isEmpty() && payload.sampleTick() / 20 == data.history.getLast().sampleTick() / 20) {
                data.history.removeLast();
            }
            data.history.addLast(new HistoryPoint(payload.sampleTick(), payload.throughput()));
            data.latestSample = payload.sampleTick();
        }
        while (!data.history.isEmpty() && (data.history.size() > HISTORY_SAMPLES
                || payload.sampleTick() - data.history.getFirst().sampleTick() >= 1_200)) {
            data.history.removeFirst();
        }
        // Refresh insertion order, retaining only the most recently viewed networks.
        LIVE.remove(payload.networkId());
        LIVE.put(payload.networkId(), data);
        while (LIVE.size() > MAX_CACHED_NETWORKS) LIVE.remove(LIVE.keySet().iterator().next());
    }

    public static NetworkTelemetryPayload snapshot(UUID networkId) {
        LiveNetwork data = LIVE.get(networkId);
        return data == null ? null : data.snapshot;
    }

    public static List<HistoryPoint> history(UUID networkId) {
        LiveNetwork data = LIVE.get(networkId);
        return data == null || data.snapshot == null ? List.of() : List.copyOf(data.history);
    }

    public static void tick(long gameTime) {
        for (LiveNetwork data : LIVE.values()) {
            if (gameTime < data.lastClientTick || gameTime - data.lastClientTick > 60) data.snapshot = null;
        }
    }

    public static List<NetworkListSyncS2CPayload.NetworkSummary> getNetworks() {
        return networks;
    }

    public static NetworkListSyncS2CPayload.NetworkSummary getNetwork(UUID networkId) {
        for (var n : networks) {
            if (n.uuid().equals(networkId)) return n;
        }
        return null;
    }

    public static void clear() {
        networks = Collections.emptyList();
        LIVE.clear();
    }
}
