package com.zerotheabsolute.quantumflux.network;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import com.zerotheabsolute.quantumflux.network.data.QuantumFluxNetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import com.zerotheabsolute.quantumflux.network.PacketCodec;
import com.zerotheabsolute.quantumflux.network.QFPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import io.netty.handler.codec.DecoderException;

/**
 * Server -> Client: list of all networks the player can access.
 * Sent on login and after any network mutation.
 */
public record NetworkListSyncS2CPayload(List<NetworkSummary> networks) implements QFPayload {

    public static final int MAX_SYNCED_NETWORKS = QuantumFluxNetworkManager.MAX_NETWORKS_PER_DIMENSION;
    public static final int MAX_SYNCED_MEMBERS = 64;
    // Server validation permits 32 Unicode code points.
    private static final int MAX_NETWORK_NAME_LENGTH = 64;
    private static final int MAX_MEMBER_NAME_LENGTH = 64;

    public record MemberSummary(UUID uuid, String displayName) {}

    public record NetworkSummary(UUID uuid, int numericId, String name, int color,
                                 int pylonCount, int energyPercent,
                                 int accessMode, boolean isOwner, boolean isMember,
                                 int beamStyle, boolean beamsVisible,
                                 List<MemberSummary> members) {}

    public static final Type<NetworkListSyncS2CPayload> TYPE =
            new Type<>(new ResourceLocation(QuantumFlux.MODID, "network_list_sync"));

    public static final PacketCodec<FriendlyByteBuf, NetworkListSyncS2CPayload> STREAM_CODEC =
            PacketCodec.of(NetworkListSyncS2CPayload::write, NetworkListSyncS2CPayload::read);

    @Override
    public Type<? extends QFPayload> type() { return TYPE; }

    private static void write(FriendlyByteBuf buf, NetworkListSyncS2CPayload payload) {
        int networkCount = Math.min(payload.networks.size(), MAX_SYNCED_NETWORKS);
        buf.writeVarInt(networkCount);
        for (int i = 0; i < networkCount; i++) {
            NetworkSummary s = payload.networks.get(i);
            buf.writeUUID(s.uuid);
            buf.writeVarInt(s.numericId);
            buf.writeUtf(s.name, MAX_NETWORK_NAME_LENGTH);
            buf.writeInt(s.color);
            buf.writeVarInt(s.pylonCount);
            buf.writeVarInt(s.energyPercent);
            buf.writeByte(s.accessMode);
            buf.writeBoolean(s.isOwner);
            buf.writeBoolean(s.isMember);
            buf.writeByte(s.beamStyle);
            buf.writeBoolean(s.beamsVisible);
            int memberCount = Math.min(s.members.size(), MAX_SYNCED_MEMBERS);
            buf.writeVarInt(memberCount);
            for (int memberIndex = 0; memberIndex < memberCount; memberIndex++) {
                MemberSummary member = s.members.get(memberIndex);
                buf.writeUUID(member.uuid());
                buf.writeUtf(member.displayName(), MAX_MEMBER_NAME_LENGTH);
            }
        }
    }

    private static NetworkListSyncS2CPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_SYNCED_NETWORKS) {
            throw new DecoderException("Invalid Flux Pylons network count: " + count);
        }
        List<NetworkSummary> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID uuid = buf.readUUID();
            int numericId = buf.readVarInt();
            String name = buf.readUtf(MAX_NETWORK_NAME_LENGTH);
            int color = buf.readInt() & 0xFFFFFF;
            int pylonCount = com.zerotheabsolute.quantumflux.util.Numbers.clamp(buf.readVarInt(), 0, 256);
            int energyPct = com.zerotheabsolute.quantumflux.util.Numbers.clamp(buf.readVarInt(), 0, 100);
            int accessMode = com.zerotheabsolute.quantumflux.util.Numbers.clamp(buf.readByte(), 0, 2);
            boolean isOwner = buf.readBoolean();
            boolean isMember = buf.readBoolean();
            int beamStyle = com.zerotheabsolute.quantumflux.util.Numbers.clamp(buf.readByte(), 0, 2);
            boolean beamsVisible = buf.readBoolean();
            int memberCount = buf.readVarInt();
            if (memberCount < 0 || memberCount > MAX_SYNCED_MEMBERS) {
                throw new DecoderException("Invalid Flux Pylons member count: " + memberCount);
            }
            List<MemberSummary> members = new ArrayList<>(memberCount);
            for (int m = 0; m < memberCount; m++) {
                members.add(new MemberSummary(buf.readUUID(), buf.readUtf(MAX_MEMBER_NAME_LENGTH)));
            }
            list.add(new NetworkSummary(uuid, numericId, name, color,
                    pylonCount, energyPct, accessMode, isOwner, isMember,
                    beamStyle, beamsVisible, members));
        }
        return new NetworkListSyncS2CPayload(list);
    }
}
