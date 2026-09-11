package com.zerotheabsolute.quantumflux.client;

import com.zerotheabsolute.quantumflux.network.PylonSyncPayload;
import com.zerotheabsolute.quantumflux.network.PylonTelemetryPayload;
import com.zerotheabsolute.quantumflux.util.BeamStyle;
import com.zerotheabsolute.quantumflux.util.PriorityMode;
import com.zerotheabsolute.quantumflux.util.RedstoneMode;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side store for pylon data received via sync packets.
 * Read by the renderer and GUI. Entries expire after 60 ticks without refresh.
 */
public final class ClientDataCache {

    private static final Map<BlockPos, PylonClientData> CACHE = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Map<BlockPos, PylonSyncPayload.ConnectionEntry>> CONNECTIONS_BY_TARGET =
            new ConcurrentHashMap<>();
    private static final int EXPIRY_TICKS = 60;

    private ClientDataCache() {}

    // ── Data class ──

    public static class PylonClientData {
        public final BlockPos pos;
        public int energy, maxEnergy;
        public double throughput, peakThroughput;
        public long availableEnergy;
        public List<PylonSyncPayload.ConnectionEntry> connections;
        public BeamStyle beamStyle;
        public int beamColor;
        public float glowIntensity;
        public boolean beamsVisible;
        public float pulseSpeed;
        public PriorityMode priorityMode;
        public RedstoneMode redstoneMode;
        public boolean outputEnabled;
        public int effectiveRange, maxConnections, transferLimit;
        @Nullable public UUID networkId;
        public long lastUpdatedTick;

        public PylonClientData(PylonSyncPayload p, long tick) {
            this.pos = p.pos();
            update(p, tick);
        }

        public boolean isPowered() {
            return energy > 0 || throughput > 0;
        }

        public void update(PylonSyncPayload p, long tick) {
            this.energy = p.energy();
            this.maxEnergy = p.maxEnergy();
            this.throughput = p.throughput();
            this.peakThroughput = p.peakThroughput();
            this.availableEnergy = p.availableEnergy();
            this.connections = p.connections();
            this.beamStyle = p.beamStyle();
            this.beamColor = p.beamColor();
            this.glowIntensity = p.glowIntensity();
            this.beamsVisible = p.beamsVisible();
            this.pulseSpeed = p.pulseSpeed();
            this.priorityMode = p.priorityMode();
            this.redstoneMode = p.redstoneMode();
            this.outputEnabled = p.outputEnabled();
            this.effectiveRange = p.effectiveRange();
            this.maxConnections = p.maxConnections();
            this.transferLimit = p.transferLimit();
            this.networkId = p.networkId();
            this.lastUpdatedTick = tick;
        }
    }

    // ── API ──

    public static void receive(PylonSyncPayload payload, long gameTime) {
        PylonClientData existing = CACHE.get(payload.pos());
        if (existing != null) {
            removeConnectionIndex(existing);
            existing.update(payload, gameTime);
        } else {
            existing = new PylonClientData(payload, gameTime);
            CACHE.put(payload.pos(), existing);
        }
        indexConnections(existing);
    }

    public static void receiveTelemetry(PylonTelemetryPayload payload, long gameTime) {
        PylonClientData data = CACHE.get(payload.pos());
        if (data == null) return;
        data.energy = payload.energy();
        data.maxEnergy = payload.maxEnergy();
        data.throughput = payload.throughput();
        data.peakThroughput = payload.peakThroughput();
        data.availableEnergy = payload.availableEnergy();
        data.outputEnabled = payload.outputEnabled();
        if (payload.connections().size() == data.connections.size()) {
            removeConnectionIndex(data);
            List<PylonSyncPayload.ConnectionEntry> updated = new java.util.ArrayList<>(data.connections.size());
            for (int index = 0; index < data.connections.size(); index++) {
                PylonSyncPayload.ConnectionEntry connection = data.connections.get(index);
                updated.add(new PylonSyncPayload.ConnectionEntry(connection.pos(), connection.displayName(),
                        payload.connections().get(index).transferred(), payload.connections().get(index).status()));
            }
            data.connections = List.copyOf(updated);
            indexConnections(data);
        }
        data.lastUpdatedTick = gameTime;
    }

    public static PylonClientData get(BlockPos pos) {
        return CACHE.get(pos);
    }

    public static Map<BlockPos, PylonClientData> getAll() {
        return Collections.unmodifiableMap(CACHE);
    }

    public static void remove(BlockPos pos) {
        PylonClientData removed = CACHE.remove(pos);
        if (removed != null) removeConnectionIndex(removed);
    }

    public static void tick(long gameTime) {
        CACHE.forEach((pos, data) -> {
            if ((gameTime - data.lastUpdatedTick) > EXPIRY_TICKS && CACHE.remove(pos, data)) {
                removeConnectionIndex(data);
            }
        });
    }

    public static void clear() {
        CACHE.clear();
        CONNECTIONS_BY_TARGET.clear();
    }

    @Nullable
    public static LinkedTargetData getLinkedTarget(BlockPos target) {
        Map<BlockPos, PylonSyncPayload.ConnectionEntry> pylons = CONNECTIONS_BY_TARGET.get(target);
        if (pylons == null || pylons.isEmpty()) return null;
        Map.Entry<BlockPos, PylonSyncPayload.ConnectionEntry> first = pylons.entrySet().iterator().next();
        return new LinkedTargetData(first.getKey(), first.getValue());
    }

    private static void indexConnections(PylonClientData data) {
        for (PylonSyncPayload.ConnectionEntry connection : data.connections) {
            CONNECTIONS_BY_TARGET.computeIfAbsent(connection.pos(), ignored -> new ConcurrentHashMap<>())
                    .put(data.pos, connection);
        }
    }

    private static void removeConnectionIndex(PylonClientData data) {
        for (PylonSyncPayload.ConnectionEntry connection : data.connections) {
            CONNECTIONS_BY_TARGET.computeIfPresent(connection.pos(), (ignored, pylons) -> {
                pylons.remove(data.pos);
                return pylons.isEmpty() ? null : pylons;
            });
        }
    }

    public record LinkedTargetData(BlockPos pylonPos, PylonSyncPayload.ConnectionEntry connection) {}
}
