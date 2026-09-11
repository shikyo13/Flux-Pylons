package com.zerotheabsolute.quantumflux.network;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import com.zerotheabsolute.quantumflux.util.BeamStyle;
import com.zerotheabsolute.quantumflux.util.PriorityMode;
import com.zerotheabsolute.quantumflux.util.ConnectionStatus;
import com.zerotheabsolute.quantumflux.util.RedstoneMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.zerotheabsolute.quantumflux.network.PacketCodec;
import com.zerotheabsolute.quantumflux.network.QFPayload;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import io.netty.handler.codec.DecoderException;

/**
 * Server → Client: full pylon state sync for GUI and rendering.
 */
public record PylonSyncPayload(
        BlockPos pos,
        int energy,
        int maxEnergy,
        double throughput,
        double peakThroughput,
        List<ConnectionEntry> connections,
        BeamStyle beamStyle,
        int beamColor,
        float glowIntensity,
        boolean beamsVisible,
        float pulseSpeed,
        PriorityMode priorityMode,
        int effectiveRange,
        int maxConnections,
        int transferLimit,
        long availableEnergy,
        @Nullable UUID networkId,
        RedstoneMode redstoneMode,
        boolean outputEnabled
) implements QFPayload {

    public static final int MAX_SYNCED_CONNECTIONS = 128;
    // 96 Unicode code points can occupy up to 192 UTF-16 code units.
    private static final int MAX_DISPLAY_NAME_LENGTH = 192;

    public static final Type<PylonSyncPayload> TYPE =
            new Type<>(new ResourceLocation(QuantumFlux.MODID, "pylon_sync"));

    public record ConnectionEntry(BlockPos pos, String displayName, double lastTransferred,
                                  ConnectionStatus status) {}

    public static final PacketCodec<FriendlyByteBuf, PylonSyncPayload> STREAM_CODEC = PacketCodec.of(
            PylonSyncPayload::write,
            PylonSyncPayload::read
    );

    private static void write(FriendlyByteBuf buf, PylonSyncPayload p) {
        buf.writeBlockPos(p.pos);
        buf.writeInt(p.energy);
        buf.writeInt(p.maxEnergy);
        EnergyRateCodec.write(buf, p.throughput);
        EnergyRateCodec.write(buf, p.peakThroughput);

        int connectionCount = Math.min(p.connections.size(), MAX_SYNCED_CONNECTIONS);
        buf.writeVarInt(connectionCount);
        for (int i = 0; i < connectionCount; i++) {
            ConnectionEntry c = p.connections.get(i);
            buf.writeBlockPos(c.pos);
            buf.writeUtf(c.displayName, MAX_DISPLAY_NAME_LENGTH);
            EnergyRateCodec.write(buf, c.lastTransferred);
            buf.writeVarInt(c.status.ordinal());
        }

        buf.writeVarInt((p.beamStyle == null ? BeamStyle.SOLID : p.beamStyle).ordinal());
        buf.writeInt(p.beamColor);
        buf.writeFloat(p.glowIntensity);
        buf.writeBoolean(p.beamsVisible);
        buf.writeFloat(p.pulseSpeed);
        buf.writeVarInt((p.priorityMode == null ? PriorityMode.EQUAL : p.priorityMode).ordinal());
        buf.writeVarInt(p.effectiveRange);
        buf.writeVarInt(p.maxConnections);
        buf.writeVarInt(Math.max(0, p.transferLimit));
        buf.writeVarLong(Math.max(0, p.availableEnergy));

        buf.writeBoolean(p.networkId != null);
        if (p.networkId != null) buf.writeUUID(p.networkId);
        buf.writeEnum(p.redstoneMode);
        buf.writeBoolean(p.outputEnabled);
    }

    private static PylonSyncPayload read(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int energy = Math.max(0, buf.readInt());
        int maxEnergy = Math.max(0, buf.readInt());
        energy = Math.min(energy, maxEnergy);
        double throughput = EnergyRateCodec.read(buf);
        double peak = EnergyRateCodec.read(buf);

        int connCount = buf.readVarInt();
        if (connCount < 0 || connCount > MAX_SYNCED_CONNECTIONS) {
            throw new DecoderException("Invalid Flux Pylons connection count: " + connCount);
        }
        List<ConnectionEntry> conns = new ArrayList<>(connCount);
        for (int i = 0; i < connCount; i++) {
            conns.add(new ConnectionEntry(buf.readBlockPos(), buf.readUtf(MAX_DISPLAY_NAME_LENGTH),
                    EnergyRateCodec.read(buf), readEnum(buf, ConnectionStatus.values(), ConnectionStatus.UNAVAILABLE)));
        }

        BeamStyle style = readEnum(buf, BeamStyle.values(), BeamStyle.SOLID);
        int color = buf.readInt() & 0xFFFFFF;
        float glow = finiteClamped(buf.readFloat(), 0.2f, 2.0f, 1.0f);
        boolean visible = buf.readBoolean();
        float pulse = finiteClamped(buf.readFloat(), 0.1f, 4.0f, 1.0f);
        PriorityMode priority = readEnum(buf, PriorityMode.values(), PriorityMode.EQUAL);
        int range = com.zerotheabsolute.quantumflux.util.Numbers.clamp(buf.readVarInt(), 1, 256);
        int maxConns = com.zerotheabsolute.quantumflux.util.Numbers.clamp(buf.readVarInt(), 1, MAX_SYNCED_CONNECTIONS);
        int transferLimit = Math.max(0, buf.readVarInt());
        long availableEnergy = Math.max(0, buf.readVarLong());

        UUID netId = buf.readBoolean() ? buf.readUUID() : null;
        if (new UUID(0L, 0L).equals(netId)) netId = null;
        RedstoneMode redstone = readEnum(buf, RedstoneMode.values(), RedstoneMode.IGNORE);
        boolean outputEnabled = buf.readBoolean();

        return new PylonSyncPayload(pos, energy, maxEnergy, throughput, peak, conns,
                style, color, glow, visible, pulse, priority, range, maxConns, transferLimit, availableEnergy,
                netId, redstone, outputEnabled);
    }

    private static <E> E readEnum(FriendlyByteBuf buf, E[] values, E fallback) {
        int ordinal = buf.readVarInt();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }

    private static float finiteClamped(float value, float minimum, float maximum, float fallback) {
        return Float.isFinite(value) ? com.zerotheabsolute.quantumflux.util.Numbers.clamp(value, minimum, maximum) : fallback;
    }

    @Override
    public Type<? extends QFPayload> type() {
        return TYPE;
    }
}
