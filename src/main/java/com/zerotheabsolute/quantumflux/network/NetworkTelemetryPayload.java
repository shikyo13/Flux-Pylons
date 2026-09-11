package com.zerotheabsolute.quantumflux.network;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import com.zerotheabsolute.quantumflux.QFConfig;
import com.zerotheabsolute.quantumflux.blockentity.QuantumPylonBlockEntity;
import com.zerotheabsolute.quantumflux.network.data.QFNetwork;
import com.zerotheabsolute.quantumflux.network.data.QuantumFluxNetworkManager;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.zerotheabsolute.quantumflux.network.PacketCodec;
import com.zerotheabsolute.quantumflux.network.QFPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Selected-network readings, including pylons outside the player's tracking distance. */
public record NetworkTelemetryPayload(UUID networkId, long sampleTick, List<PylonSummary> pylons)
        implements QFPayload {
    public static final Type<NetworkTelemetryPayload> TYPE = new Type<>(
            new ResourceLocation(QuantumFlux.MODID, "network_telemetry"));
    public static final PacketCodec<FriendlyByteBuf, NetworkTelemetryPayload> STREAM_CODEC =
            PacketCodec.of(NetworkTelemetryPayload::write, NetworkTelemetryPayload::read);

    public enum Availability { LOADED, UNLOADED, MISSING }

    public record PylonSummary(BlockPos pos, Availability availability, int energy, int capacity,
                               double throughput, int connections, int range,
                               int maxConnections, int transferLimit, boolean outputEnabled) {
        public boolean loaded() { return availability == Availability.LOADED; }
    }

    public NetworkTelemetryPayload {
        pylons = List.copyOf(pylons);
    }

    public long energy() { return pylons.stream().filter(PylonSummary::loaded).mapToLong(PylonSummary::energy).sum(); }
    public long capacity() { return pylons.stream().filter(PylonSummary::loaded).mapToLong(PylonSummary::capacity).sum(); }
    public double throughput() {
        return pylons.stream().filter(PylonSummary::loaded)
                .mapToLong(pylon -> Math.round(pylon.throughput * 20)).sum() / 20.0;
    }
    public int connections() { return pylons.stream().filter(PylonSummary::loaded).mapToInt(PylonSummary::connections).sum(); }
    public int loadedPylons() { return (int) pylons.stream().filter(PylonSummary::loaded).count(); }

    public static NetworkTelemetryPayload capture(ServerLevel level, QFNetwork network) {
        List<PylonSummary> rows = new ArrayList<>();
        for (BlockPos pos : network.getPylonPositions().stream().sorted()
                .limit(QuantumFluxNetworkManager.MAX_PYLONS_PER_NETWORK).toList()) {
            boolean inWorld = !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos);
            boolean chunkLoaded = inWorld && level.hasChunkAt(pos);
            if (chunkLoaded && level.getBlockEntity(pos) instanceof QuantumPylonBlockEntity pylon
                    && !pylon.isRemoved() && network.getUuid().equals(pylon.getNetworkId())) {
                rows.add(new PylonSummary(pos, Availability.LOADED,
                        pylon.getEnergyStorage().getEnergyStored(), pylon.getEnergyStorage().getMaxEnergyStored(),
                        pylon.getTotalThroughput(), pylon.getConnections().size(), pylon.getEffectiveRange(),
                        pylon.getEffectiveMaxConnections(), pylon.getEffectiveTransferLimit(), pylon.isOutputEnabled()));
            } else {
                rows.add(new PylonSummary(pos, inWorld && !chunkLoaded ? Availability.UNLOADED : Availability.MISSING,
                        0, 0, 0, 0, 0, 0, 0, false));
            }
        }
        return new NetworkTelemetryPayload(network.getUuid(), level.getGameTime(), rows);
    }

    private static void write(FriendlyByteBuf buf, NetworkTelemetryPayload payload) {
        buf.writeUUID(payload.networkId);
        buf.writeVarLong(payload.sampleTick);
        int count = Math.min(payload.pylons.size(), QuantumFluxNetworkManager.MAX_PYLONS_PER_NETWORK);
        buf.writeVarInt(count);
        for (int index = 0; index < count; index++) {
            PylonSummary row = payload.pylons.get(index);
            buf.writeBlockPos(row.pos);
            buf.writeByte(row.availability.ordinal());
            if (!row.loaded()) continue;
            buf.writeVarInt(row.energy);
            buf.writeVarInt(row.capacity);
            EnergyRateCodec.write(buf, row.throughput);
            buf.writeVarInt(row.connections);
            buf.writeVarInt(row.range);
            buf.writeVarInt(row.maxConnections);
            buf.writeVarInt(row.transferLimit);
            buf.writeBoolean(row.outputEnabled);
        }
    }

    private static NetworkTelemetryPayload read(FriendlyByteBuf buf) {
        UUID networkId = buf.readUUID();
        long sampleTick = Math.max(0, buf.readVarLong());
        int count = buf.readVarInt();
        if (count < 0 || count > QuantumFluxNetworkManager.MAX_PYLONS_PER_NETWORK) {
            throw new DecoderException("Invalid Flux Pylons pylon summary count: " + count);
        }
        List<PylonSummary> pylons = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            BlockPos pos = buf.readBlockPos();
            int availability = buf.readUnsignedByte();
            if (availability >= Availability.values().length) throw new DecoderException("Invalid pylon availability");
            Availability state = Availability.values()[availability];
            if (state != Availability.LOADED) {
                pylons.add(new PylonSummary(pos, state, 0, 0, 0, 0, 0, 0, 0, false));
                continue;
            }
            int energy = Math.max(0, buf.readVarInt());
            int capacity = Math.max(0, buf.readVarInt());
            double rate = EnergyRateCodec.read(buf);
            int connections = com.zerotheabsolute.quantumflux.util.Numbers.clamp(buf.readVarInt(), 0, PylonSyncPayload.MAX_SYNCED_CONNECTIONS);
            int range = com.zerotheabsolute.quantumflux.util.Numbers.clamp(buf.readVarInt(), 1, 256);
            int maxConnections = com.zerotheabsolute.quantumflux.util.Numbers.clamp(buf.readVarInt(), 1, PylonSyncPayload.MAX_SYNCED_CONNECTIONS);
            int limit = Math.max(0, buf.readVarInt());
            boolean outputEnabled = buf.readBoolean();
            pylons.add(new PylonSummary(pos, state, Math.min(energy, capacity), capacity,
                    rate, connections, range, maxConnections, limit, outputEnabled));
        }
        return new NetworkTelemetryPayload(networkId, sampleTick, pylons);
    }

    @Override public Type<? extends QFPayload> type() { return TYPE; }
}
