package com.zerotheabsolute.quantumflux.network;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client -> Server: all network management actions in one packet.
 * Action enum determines which fields are used.
 */
public record NetworkActionC2SPayload(Action action, UUID networkId,
                                       String name, int color, BlockPos pylonPos,
                                       int requestId) implements CustomPacketPayload {

    public enum Action {
        CREATE,                 // name + color (networkId ignored)
        DELETE,                 // networkId
        RENAME,                 // networkId + name
        RECOLOR,                // networkId + color
        ASSIGN_PYLON,           // networkId + pylonPos
        UNASSIGN_PYLON,         // pylonPos (networkId ignored)
        SELECT,                 // networkId (sets gadget's SELECTED_NETWORK)
        SET_BEAM_STYLE,         // networkId + color (as beam style ordinal)
        TOGGLE_BEAMS,           // networkId
        SET_ACCESS_MODE,        // networkId + color (as AccessMode ordinal)
        SET_PASSWORD,           // networkId + name (plaintext over the authenticated game session)
        ADD_MEMBER,             // networkId + name (player name to add)
        REMOVE_MEMBER,          // networkId + name (member UUID as string)
        JOIN_PASSWORD_NETWORK,  // networkId + name (password verifier)
        INVALID
    }

    public static final Type<NetworkActionC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(QuantumFlux.MODID, "network_action"));

    public static final StreamCodec<FriendlyByteBuf, NetworkActionC2SPayload> STREAM_CODEC =
            StreamCodec.of(NetworkActionC2SPayload::write, NetworkActionC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // Convenience constructors
    public static NetworkActionC2SPayload create(String name, int color) {
        return new NetworkActionC2SPayload(Action.CREATE, new UUID(0, 0), name, color, BlockPos.ZERO, 0);
    }

    public static NetworkActionC2SPayload delete(UUID networkId) {
        return new NetworkActionC2SPayload(Action.DELETE, networkId, "", 0, BlockPos.ZERO, 0);
    }

    public static NetworkActionC2SPayload rename(UUID networkId, String name) {
        return new NetworkActionC2SPayload(Action.RENAME, networkId, name, 0, BlockPos.ZERO, 0);
    }

    public static NetworkActionC2SPayload recolor(UUID networkId, int color) {
        return new NetworkActionC2SPayload(Action.RECOLOR, networkId, "", color, BlockPos.ZERO, 0);
    }

    public static NetworkActionC2SPayload assignPylon(UUID networkId, BlockPos pylonPos) {
        return new NetworkActionC2SPayload(Action.ASSIGN_PYLON, networkId, "", 0, pylonPos, 0);
    }

    public static NetworkActionC2SPayload unassignPylon(BlockPos pylonPos) {
        return new NetworkActionC2SPayload(Action.UNASSIGN_PYLON, new UUID(0, 0), "", 0, pylonPos, 0);
    }

    public static NetworkActionC2SPayload select(UUID networkId) {
        return new NetworkActionC2SPayload(Action.SELECT, networkId, "", 0, BlockPos.ZERO, 0);
    }

    public static NetworkActionC2SPayload setBeamStyle(UUID networkId, int styleOrdinal) {
        return new NetworkActionC2SPayload(Action.SET_BEAM_STYLE, networkId, "", styleOrdinal, BlockPos.ZERO, 0);
    }

    public static NetworkActionC2SPayload toggleBeams(UUID networkId) {
        return new NetworkActionC2SPayload(Action.TOGGLE_BEAMS, networkId, "", 0, BlockPos.ZERO, 0);
    }

    public static NetworkActionC2SPayload setAccessMode(UUID networkId, int modeOrdinal) {
        return new NetworkActionC2SPayload(Action.SET_ACCESS_MODE, networkId, "", modeOrdinal, BlockPos.ZERO, 0);
    }

    public static NetworkActionC2SPayload setPassword(UUID networkId, String plainPassword) {
        return new NetworkActionC2SPayload(Action.SET_PASSWORD, networkId, plainPassword, 0, BlockPos.ZERO, 0);
    }

    public static NetworkActionC2SPayload addMember(UUID networkId, String playerName) {
        return new NetworkActionC2SPayload(Action.ADD_MEMBER, networkId, playerName, 0, BlockPos.ZERO, 0);
    }

    public static NetworkActionC2SPayload removeMember(UUID networkId, String memberUuidStr) {
        return new NetworkActionC2SPayload(Action.REMOVE_MEMBER, networkId, memberUuidStr, 0, BlockPos.ZERO, 0);
    }

    public static NetworkActionC2SPayload joinPasswordNetwork(UUID networkId, String plainPassword) {
        return new NetworkActionC2SPayload(Action.JOIN_PASSWORD_NETWORK, networkId, plainPassword, 0, BlockPos.ZERO, 0);
    }

    public NetworkActionC2SPayload withRequestId(int id) {
        return new NetworkActionC2SPayload(action, networkId, name, color, pylonPos, id);
    }

    private static void write(FriendlyByteBuf buf, NetworkActionC2SPayload p) {
        buf.writeVarInt(p.action.ordinal());
        buf.writeUUID(p.networkId);
        buf.writeUtf(p.name, 64);
        buf.writeInt(p.color);
        buf.writeBlockPos(p.pylonPos);
        buf.writeVarInt(p.requestId);
    }

    private static NetworkActionC2SPayload read(FriendlyByteBuf buf) {
        return new NetworkActionC2SPayload(
                readAction(buf), buf.readUUID(),
                buf.readUtf(64), buf.readInt(), buf.readBlockPos(), buf.readVarInt());
    }

    private static Action readAction(FriendlyByteBuf buf) {
        int ordinal = buf.readVarInt();
        Action[] values = Action.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Action.INVALID;
    }
}
