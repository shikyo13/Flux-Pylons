package com.zerotheabsolute.quantumflux.client.screen;

import com.zerotheabsolute.quantumflux.client.ClientDataCache;
import com.zerotheabsolute.quantumflux.client.ClientNetworkCache;
import com.zerotheabsolute.quantumflux.client.PylonReadout;
import com.zerotheabsolute.quantumflux.client.PylonStatus;
import com.zerotheabsolute.quantumflux.network.NetworkListSyncS2CPayload;
import com.zerotheabsolute.quantumflux.network.PylonSyncPayload;
import com.zerotheabsolute.quantumflux.network.NetworkTelemetryPayload;
import com.zerotheabsolute.quantumflux.util.BeamStyle;
import com.zerotheabsolute.quantumflux.util.PriorityMode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Pure presentation projections over the server-synchronized client caches. */
final class GadgetScreenModel {

    private GadgetScreenModel() {}

    static List<NetworkListSyncS2CPayload.NetworkSummary> sortedNetworks() {
        List<NetworkListSyncS2CPayload.NetworkSummary> result = new ArrayList<>(ClientNetworkCache.getNetworks());
        result.sort(Comparator.comparing(NetworkListSyncS2CPayload.NetworkSummary::isOwner).reversed()
                .thenComparingInt(NetworkListSyncS2CPayload.NetworkSummary::numericId));
        return result;
    }

    static List<NetworkTelemetryPayload.PylonSummary> sortedPylons(UUID networkId) {
        NetworkTelemetryPayload snapshot = ClientNetworkCache.snapshot(networkId);
        if (snapshot == null) return List.of();
        List<NetworkTelemetryPayload.PylonSummary> result = new ArrayList<>(snapshot.pylons());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            result.sort(Comparator.comparingDouble(entry -> minecraft.player.distanceToSqr(
                    entry.pos().getX() + 0.5D, entry.pos().getY() + 0.5D,
                    entry.pos().getZ() + 0.5D)));
        } else {
            result.sort(Comparator.comparing(NetworkTelemetryPayload.PylonSummary::pos));
        }
        return result;
    }

    static NetworkTelemetryPayload.PylonSummary pylonSummary(UUID networkId, BlockPos pos) {
        NetworkTelemetryPayload snapshot = ClientNetworkCache.snapshot(networkId);
        return snapshot == null ? null : snapshot.pylons().stream().filter(pylon -> pylon.pos().equals(pos))
                .findFirst().orElse(null);
    }

    static Telemetry telemetry(UUID networkId) {
        NetworkTelemetryPayload snapshot = ClientNetworkCache.snapshot(networkId);
        if (snapshot == null) return new Telemetry(0, 0, 0, 0, 0, 0);
        double peak = ClientNetworkCache.history(networkId).stream()
                .mapToDouble(ClientNetworkCache.HistoryPoint::throughput).max().orElse(0);
        return new Telemetry(snapshot.energy(), snapshot.capacity(), snapshot.throughput(), peak,
                snapshot.connections(), snapshot.loadedPylons());
    }

    static int liveEnergyPercent(UUID networkId, int fallback) {
        Telemetry telemetry = telemetry(networkId);
        if (telemetry.capacity() <= 0) return Math.clamp(fallback, 0, 100);
        return (int) Math.clamp(telemetry.energy() * 100L / telemetry.capacity(), 0L, 100L);
    }

    static Component networkRowLabel(NetworkListSyncS2CPayload.NetworkSummary network) {
        MutableComponent prefix = Component.literal("■ ").withStyle(style -> style.withColor(network.color()));
        return prefix.append(Component.translatable("screen.quantumflux.networks.entry",
                network.name(), network.numericId(), network.pylonCount(),
                liveEnergyPercent(network.uuid(), network.energyPercent())));
    }

    static Component networkTooltip(NetworkListSyncS2CPayload.NetworkSummary network) {
        return Component.translatable("screen.quantumflux.networks.tooltip",
                roleLabel(network), accessModeLabel(network.accessMode()));
    }

    static Component pylonRowLabel(UUID networkId, NetworkTelemetryPayload.PylonSummary data) {
        return Component.translatable("screen.quantumflux.pylons.status_entry", position(data.pos()),
                pylonSummaryStatus(networkId, data), formatRate(data.throughput()));
    }

    static Component pylonSummaryStatus(UUID networkId, NetworkTelemetryPayload.PylonSummary data) {
        if (!data.loaded()) return Component.translatable(data.availability() == NetworkTelemetryPayload.Availability.UNLOADED
                ? "screen.quantumflux.receiver.unloaded" : "screen.quantumflux.receiver.unavailable");
        if (!data.outputEnabled()) return PylonStatus.PAUSED.label();
        if (data.connections() == 0) return PylonStatus.NETWORK_BUFFER.label();
        var snapshot = ClientNetworkCache.snapshot(networkId);
        return PylonStatus.of(snapshot == null ? data.energy() : snapshot.energy(),
                data.throughput(), data.connections()).label();
    }

    static Component pylonSummaryHint(NetworkTelemetryPayload.PylonSummary data) {
        return Component.translatable(switch (data.availability()) {
            case LOADED -> "screen.quantumflux.pylons.remote_hint";
            case UNLOADED -> "screen.quantumflux.receiver.unloaded_hint";
            case MISSING -> "screen.quantumflux.receiver.unavailable_hint";
        });
    }

    static Component connectionRowLabel(PylonSyncPayload.ConnectionEntry connection) {
        String displayName = connection.displayName();
        Component machineName;
        if (displayName == null || displayName.isBlank()) {
            machineName = Component.translatable("screen.quantumflux.pylons.unknown_machine");
        } else if (displayName.startsWith("block.") || displayName.startsWith("item.")) {
            machineName = Component.translatable(displayName);
        } else {
            machineName = Component.literal(displayName);
        }
        return Component.translatable("screen.quantumflux.pylons.connection_entry",
                machineName, connection.status().label(), formatRate(connection.lastTransferred()));
    }

    static Component connectionTooltip(PylonSyncPayload.ConnectionEntry connection, Component action) {
        return connection.status().hint().copy().append("\n").append(action);
    }

    static Component roleLabel(NetworkListSyncS2CPayload.NetworkSummary network) {
        if (network.isOwner()) return Component.translatable("screen.quantumflux.role.owner");
        if (network.isMember()) return Component.translatable("screen.quantumflux.role.member");
        if (network.accessMode() == 1) return Component.translatable("screen.quantumflux.role.public_guest");
        return Component.translatable("screen.quantumflux.role.locked");
    }

    static int roleColor(NetworkListSyncS2CPayload.NetworkSummary network) {
        if (network.isOwner()) return 0xFF52E4F5;
        if (network.isMember()) return 0xFF5EE68A;
        return network.accessMode() == 1 ? 0xFF96A2B3 : 0xFFFFD166;
    }

    static Component priorityLabel(PriorityMode mode) {
        return Component.translatable("screen.quantumflux.priority." + mode.name().toLowerCase(Locale.ROOT));
    }

    static Component beamStyleLabel(BeamStyle style) {
        return Component.translatable("screen.quantumflux.beam." + style.name().toLowerCase(Locale.ROOT));
    }

    static Component accessModeLabel(int ordinal) {
        String key = switch (ordinal) {
            case 0 -> "private";
            case 1 -> "public";
            case 2 -> "password";
            default -> "unknown";
        };
        return Component.translatable("screen.quantumflux.access." + key);
    }

    static Component position(BlockPos pos) {
        return Component.translatable("screen.quantumflux.position", pos.getX(), pos.getY(), pos.getZ());
    }

    static Component mutationDisabledReason(boolean configurable, boolean near) {
        if (!configurable) return Component.translatable("screen.quantumflux.permission.configure");
        if (!near) return Component.translatable("screen.quantumflux.permission.distance");
        return Component.translatable("screen.quantumflux.permission.unavailable");
    }

    static Component settingsDisabledReason(NetworkListSyncS2CPayload.NetworkSummary network) {
        if (!network.isMember()) return Component.translatable("screen.quantumflux.permission.configure");
        return Component.translatable("screen.quantumflux.permission.unavailable");
    }

    static Component ownerOnly() {
        return Component.translatable("screen.quantumflux.permission.owner");
    }

    static int clampScroll(int offset, int size, int visible) {
        return Math.max(0, Math.min(offset, Math.max(0, size - Math.max(0, visible))));
    }

    static String formatEnergy(long energy) {
        return PylonReadout.formatEnergy(energy);
    }

    static String formatRate(double rate) {
        return PylonReadout.formatRate(rate);
    }

    record Telemetry(long energy, long capacity, double throughput,
                     double peak, int connections, int loadedPylons) {}
}
