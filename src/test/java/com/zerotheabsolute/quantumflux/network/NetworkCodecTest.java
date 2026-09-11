package com.zerotheabsolute.quantumflux.network;

import com.zerotheabsolute.quantumflux.client.ClientDataCache;
import com.zerotheabsolute.quantumflux.client.PylonStatus;
import com.zerotheabsolute.quantumflux.client.PylonReadout;
import com.zerotheabsolute.quantumflux.client.ClientNetworkCache;
import com.zerotheabsolute.quantumflux.util.BeamStyle;
import com.zerotheabsolute.quantumflux.util.GadgetAction;
import com.zerotheabsolute.quantumflux.util.PriorityMode;
import com.zerotheabsolute.quantumflux.util.ConnectionStatus;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.UUID;

/** Dependency-free protocol bounds and identity regression checks. */
public final class NetworkCodecTest {

    public static void main(String[] args) {
        UUID networkId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        BlockPos pylon = new BlockPos(1, 2, 3);
        var original = new NetworkListSyncS2CPayload(List.of(
                new NetworkListSyncS2CPayload.NetworkSummary(
                        networkId, 7, "Test", 0x12ABEF, 3, 42,
                        2, true, true, 1, false,
                        List.of(new NetworkListSyncS2CPayload.MemberSummary(memberId, "Offline")))
        ));
        FriendlyByteBuf networkBuffer = new FriendlyByteBuf(Unpooled.buffer());
        NetworkListSyncS2CPayload.STREAM_CODEC.encode(networkBuffer, original);
        NetworkListSyncS2CPayload decoded = NetworkListSyncS2CPayload.STREAM_CODEC.decode(networkBuffer);
        var summary = decoded.networks().getFirst();
        assert summary.uuid().equals(networkId);
        assert summary.members().getFirst().uuid().equals(memberId);
        assert summary.beamStyle() == 1;
        assert !summary.beamsVisible();

        BlockPos target = new BlockPos(91, 64, -17);
        GadgetActionPayload gadget = new GadgetActionPayload(
                BlockPos.ZERO, GadgetAction.UNLINK_SINGLE, target, 0, 99);
        FriendlyByteBuf gadgetBuffer = new FriendlyByteBuf(Unpooled.buffer());
        GadgetActionPayload.STREAM_CODEC.encode(gadgetBuffer, gadget);
        GadgetActionPayload decodedGadget = GadgetActionPayload.STREAM_CODEC.decode(gadgetBuffer);
        assert decodedGadget.targetPos().equals(target);
        assert decodedGadget.requestId() == 99;

        FriendlyByteBuf malformed = new FriendlyByteBuf(Unpooled.buffer());
        malformed.writeVarInt(NetworkListSyncS2CPayload.MAX_SYNCED_NETWORKS + 1);
        boolean rejected = false;
        try {
            NetworkListSyncS2CPayload.STREAM_CODEC.decode(malformed);
        } catch (DecoderException expected) {
            rejected = true;
        }
        assert rejected : "Oversized network list was accepted";

        ClientDataCache.clear();

        ClientNetworkCache.clear();
        ClientNetworkCache.receive(original.networks());
        var networkReadings = new NetworkTelemetryPayload(networkId, 0, List.of(
                new NetworkTelemetryPayload.PylonSummary(pylon, NetworkTelemetryPayload.Availability.LOADED,
                        10, 100, 0.05, 1, 16, 20, 10_000, true),
                new NetworkTelemetryPayload.PylonSummary(new BlockPos(1_000, 64, 1_000),
                        NetworkTelemetryPayload.Availability.LOADED, 20, 200, 0.1, 2, 16, 20, 10_000, false),
                new NetworkTelemetryPayload.PylonSummary(new BlockPos(1_000_000, 64, 1_000_000),
                        NetworkTelemetryPayload.Availability.UNLOADED, 0, 0, 0, 0, 0, 0, 0, false)));
        FriendlyByteBuf readingsBuffer = new FriendlyByteBuf(Unpooled.buffer());
        NetworkTelemetryPayload.STREAM_CODEC.encode(readingsBuffer, networkReadings);
        var decodedReadings = NetworkTelemetryPayload.STREAM_CODEC.decode(readingsBuffer);
        assert decodedReadings.equals(networkReadings);
        assert decodedReadings.energy() == 30 && decodedReadings.capacity() == 300;
        assert decodedReadings.throughput() == 0.15;
        assert decodedReadings.loadedPylons() == 2 && decodedReadings.pylons().size() == 3;
        assert ClientDataCache.getAll().isEmpty() : "Remote totals must not depend on the nearby pylon cache";
        for (int sample = 0; sample <= 80; sample++) {
            ClientNetworkCache.receiveTelemetry(new NetworkTelemetryPayload(networkId, sample * 20L,
                    decodedReadings.pylons()), sample * 20L);
        }
        assert ClientNetworkCache.snapshot(networkId).energy() == 30;
        assert ClientNetworkCache.history(networkId).size() == 60 : "History must retain at most one minute";
        ClientNetworkCache.receiveTelemetry(new NetworkTelemetryPayload(networkId, 1_605,
                decodedReadings.pylons()), 1_605);
        assert ClientNetworkCache.history(networkId).size() == 60
                : "An extra refresh in the same second must not shorten the history window";
        ClientNetworkCache.tick(1_666);
        assert ClientNetworkCache.snapshot(networkId) == null : "Stale network readings must expire";
        assert ClientNetworkCache.history(networkId).isEmpty() : "Expired readings must not look live on the graph";
        FriendlyByteBuf oversizedReadings = new FriendlyByteBuf(Unpooled.buffer());
        oversizedReadings.writeUUID(networkId);
        oversizedReadings.writeVarLong(0);
        oversizedReadings.writeVarInt(com.zerotheabsolute.quantumflux.network.data.QuantumFluxNetworkManager.MAX_PYLONS_PER_NETWORK + 1);
        rejected = false;
        try {
            NetworkTelemetryPayload.STREAM_CODEC.decode(oversizedReadings);
        } catch (DecoderException expected) {
            rejected = true;
        }
        assert rejected : "Oversized pylon snapshots must be rejected before allocation";
        ClientNetworkCache.clear();
        BlockPos machine = new BlockPos(4, 5, 6);
        var pylonState = new PylonSyncPayload(
                pylon, 10, 100, 0.05, 2.15,
                List.of(new PylonSyncPayload.ConnectionEntry(machine, "block.test.machine", 0.05, ConnectionStatus.TRANSFERRING)),
                BeamStyle.SOLID, 0x00FFFF, 1.0F, true, 1.0F,
                PriorityMode.EQUAL, 16, 20, 10_000, 10, networkId,
                com.zerotheabsolute.quantumflux.util.RedstoneMode.WHEN_UNPOWERED, true);
        FriendlyByteBuf pylonBuffer = new FriendlyByteBuf(Unpooled.buffer());
        PylonSyncPayload.STREAM_CODEC.encode(pylonBuffer, pylonState);
        var decodedPylon = PylonSyncPayload.STREAM_CODEC.decode(pylonBuffer);
        assert decodedPylon.throughput() == 0.05 && decodedPylon.peakThroughput() == 2.15;
        assert decodedPylon.redstoneMode() == com.zerotheabsolute.quantumflux.util.RedstoneMode.WHEN_UNPOWERED;
        assert decodedPylon.connections().getFirst().lastTransferred() == 0.05;
        assert decodedPylon.connections().getFirst().status() == ConnectionStatus.TRANSFERRING;
        assert PylonReadout.formatRate(decodedPylon.throughput()).equals("0.05")
                : "A single FE in 20 ticks must display 0.05 FE/t";
        ClientDataCache.receive(decodedPylon, 1L);
        FriendlyByteBuf telemetryBuffer = new FriendlyByteBuf(Unpooled.buffer());
        PylonTelemetryPayload.STREAM_CODEC.encode(telemetryBuffer,
                new PylonTelemetryPayload(pylon, 0, 100, 0.05, 2.15, 0, true, List.of(reading(0.05))));
        ClientDataCache.receiveTelemetry(PylonTelemetryPayload.STREAM_CODEC.decode(telemetryBuffer), 2L);
        assert ClientDataCache.get(pylon).throughput == 0.05;
        assert ClientDataCache.get(pylon).isPowered() : "Exact trickles must keep effects active";
        ClientDataCache.receiveTelemetry(new PylonTelemetryPayload(
                pylon, 20, 100, 7, 9, 20, true, List.of(reading(77))), 2L);
        ClientDataCache.LinkedTargetData linked = ClientDataCache.getLinkedTarget(machine);
        assert linked != null;
        assert linked.connection().lastTransferred() == 77
                : "Reverse connection index retained stale telemetry";
        ClientDataCache.receiveTelemetry(new PylonTelemetryPayload(
                pylon, 0, 100, 7, 9, 0, true, List.of(reading(7))), 3L);
        assert ClientDataCache.get(pylon).isPowered()
                : "Pass-through delivery must power the renderer even with an empty buffer";
        assert PylonStatus.of(ClientDataCache.get(pylon)) == PylonStatus.TRANSFERRING;
        ClientDataCache.receiveTelemetry(new PylonTelemetryPayload(
                pylon, 0, 100, 0, 9, 0, true, List.of(reading(0))), 23L);
        assert !ClientDataCache.get(pylon).isPowered() : "Expired activity must stop the renderer";
        assert PylonStatus.of(ClientDataCache.get(pylon)) == PylonStatus.NO_POWER;
        assert PylonStatus.of(100, 0, 1) == PylonStatus.STANDBY;
        assert PylonStatus.of(100, 0, 0) == PylonStatus.UNLINKED;
        ClientDataCache.receiveTelemetry(new PylonTelemetryPayload(
                pylon, 0, 100, 0, 9, 123, true, List.of(reading(0))), 24L);
        assert PylonStatus.of(ClientDataCache.get(pylon)) == PylonStatus.STANDBY
                : "An empty local buffer must not imply no network power";
        ClientDataCache.receiveTelemetry(new PylonTelemetryPayload(
                pylon, 100, 100, 7, 9, 123, false,
                List.of(new PylonTelemetryPayload.ConnectionReading(7, ConnectionStatus.PAUSED))), 25L);
        assert PylonStatus.of(ClientDataCache.get(pylon)) == PylonStatus.PAUSED
                : "A redstone pause takes precedence over recent measured output";
        assert !ClientDataCache.get(pylon).outputEnabled;
        assert !ClientDataCache.getLinkedTarget(machine).connection().status().showsBeam();
        ClientDataCache.clear();
    }

    private static PylonTelemetryPayload.ConnectionReading reading(double rate) {
        return new PylonTelemetryPayload.ConnectionReading(rate,
                rate > 0 ? ConnectionStatus.TRANSFERRING : ConnectionStatus.WAITING);
    }

    private NetworkCodecTest() {}
}
