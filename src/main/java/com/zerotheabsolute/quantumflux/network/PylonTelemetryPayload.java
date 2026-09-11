package com.zerotheabsolute.quantumflux.network;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import com.zerotheabsolute.quantumflux.util.ConnectionStatus;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Compact periodic state. Connection structure is sent only when it changes. */
public record PylonTelemetryPayload(
        BlockPos pos,
        int energy,
        int maxEnergy,
        double throughput,
        double peakThroughput,
        long availableEnergy,
        boolean outputEnabled,
        List<ConnectionReading> connections
) implements CustomPacketPayload {

    public record ConnectionReading(double transferred, ConnectionStatus status) {}

    public static final Type<PylonTelemetryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(QuantumFlux.MODID, "pylon_telemetry"));
    public static final StreamCodec<FriendlyByteBuf, PylonTelemetryPayload> STREAM_CODEC =
            StreamCodec.of(PylonTelemetryPayload::write, PylonTelemetryPayload::read);

    private static void write(FriendlyByteBuf buf, PylonTelemetryPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeVarInt(Math.max(0, payload.energy));
        buf.writeVarInt(Math.max(0, payload.maxEnergy));
        EnergyRateCodec.write(buf, payload.throughput);
        EnergyRateCodec.write(buf, payload.peakThroughput);
        buf.writeVarLong(Math.max(0, payload.availableEnergy));
        buf.writeBoolean(payload.outputEnabled);
        int count = Math.min(payload.connections.size(), PylonSyncPayload.MAX_SYNCED_CONNECTIONS);
        buf.writeVarInt(count);
        for (int index = 0; index < count; index++) {
            ConnectionReading reading = payload.connections.get(index);
            EnergyRateCodec.write(buf, reading.transferred);
            buf.writeVarInt(reading.status.ordinal());
        }
    }

    private static PylonTelemetryPayload read(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int energy = nonNegative(buf.readVarInt());
        int maxEnergy = nonNegative(buf.readVarInt());
        double throughput = EnergyRateCodec.read(buf);
        double peakThroughput = EnergyRateCodec.read(buf);
        long availableEnergy = Math.max(0, buf.readVarLong());
        boolean outputEnabled = buf.readBoolean();
        int count = buf.readVarInt();
        if (count < 0 || count > PylonSyncPayload.MAX_SYNCED_CONNECTIONS) {
            throw new DecoderException("Invalid Flux Pylons telemetry connection count: " + count);
        }
        List<ConnectionReading> readings = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            double rate = EnergyRateCodec.read(buf);
            int ordinal = buf.readVarInt();
            ConnectionStatus status = ordinal >= 0 && ordinal < ConnectionStatus.values().length
                    ? ConnectionStatus.values()[ordinal] : ConnectionStatus.UNAVAILABLE;
            readings.add(new ConnectionReading(rate, status));
        }
        return new PylonTelemetryPayload(pos, energy, maxEnergy, throughput,
                peakThroughput, availableEnergy, outputEnabled, readings);
    }

    private static int nonNegative(int value) {
        return Math.max(0, value);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
