package com.zerotheabsolute.quantumflux.network.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import com.zerotheabsolute.quantumflux.util.QFPasswordVerifier;
import com.zerotheabsolute.quantumflux.util.BeamStyle;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class QFNetwork {

    public enum AccessMode { PRIVATE, PUBLIC, PASSWORD }

    private final UUID uuid;
    private final int numericId;
    private String name;
    private int color;
    private final UUID owner;
    private AccessMode accessMode;
    private String passwordVerifier;
    private BeamStyle beamStyle;
    private boolean beamsVisible;
    private boolean presentationInitialized;
    private final Set<UUID> members = new HashSet<>();
    private final Set<BlockPos> pylonPositions = new HashSet<>();

    public QFNetwork(UUID uuid, int numericId, String name, int color, UUID owner) {
        this.uuid = uuid;
        this.numericId = numericId;
        this.name = name;
        this.color = color;
        this.owner = owner;
        this.accessMode = AccessMode.PRIVATE;
        this.passwordVerifier = "";
        this.beamStyle = BeamStyle.SOLID;
        this.beamsVisible = true;
        this.presentationInitialized = true;
    }

    // ── Getters ──

    public UUID getUuid() { return uuid; }
    public int getNumericId() { return numericId; }
    public String getName() { return name; }
    public int getColor() { return color; }
    public UUID getOwner() { return owner; }
    public AccessMode getAccessMode() { return accessMode; }
    public String getPasswordVerifier() { return passwordVerifier; }
    public BeamStyle getBeamStyle() { return beamStyle; }
    public boolean isBeamsVisible() { return beamsVisible; }
    public boolean isPresentationInitialized() { return presentationInitialized; }
    public Set<UUID> getMembers() { return members; }
    public Set<BlockPos> getPylonPositions() { return pylonPositions; }

    // ── Setters ──

    public void setName(String name) { this.name = name; }
    public void setColor(int color) { this.color = color; }
    public void setAccessMode(AccessMode mode) { this.accessMode = mode; }
    public void setPasswordVerifier(String verifier) { this.passwordVerifier = verifier; }
    public void setBeamStyle(BeamStyle beamStyle) { this.beamStyle = beamStyle; }
    public void setBeamsVisible(boolean beamsVisible) { this.beamsVisible = beamsVisible; }
    public void setPresentationInitialized(boolean initialized) { this.presentationInitialized = initialized; }

    // ── Pylon management ──

    public void addPylon(BlockPos pos) { pylonPositions.add(pos); }
    public void removePylon(BlockPos pos) { pylonPositions.remove(pos); }
    public boolean hasPylon(BlockPos pos) { return pylonPositions.contains(pos); }

    // ── Member management ──

    public void addMember(UUID playerId) { members.add(playerId); }
    public void removeMember(UUID playerId) { members.remove(playerId); }

    // ── Access control ──

    public boolean canAccess(UUID playerId) {
        if (playerId.equals(owner)) return true;
        if (members.contains(playerId)) return true;
        return accessMode == AccessMode.PUBLIC;
    }

    /**
     * Whether a player may mutate pylons and presentation settings belonging to
     * this network. Public access permits use, not administration.
     */
    public boolean canConfigure(UUID playerId) {
        return playerId.equals(owner) || members.contains(playerId);
    }

    public boolean isOwner(UUID playerId) {
        return playerId.equals(owner);
    }

    /** Whether this network should appear in a player's network list. */
    public boolean isListable(UUID playerId) {
        if (playerId.equals(owner)) return true;
        if (members.contains(playerId)) return true;
        return accessMode == AccessMode.PUBLIC || accessMode == AccessMode.PASSWORD;
    }

    // ── NBT serialization ──

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("uuid", uuid);
        tag.putInt("numericId", numericId);
        tag.putString("name", name);
        tag.putInt("color", color);
        tag.putUUID("owner", owner);
        tag.putString("accessMode", accessMode.name());
        tag.putString("passwordVerifier", passwordVerifier);
        if (presentationInitialized) {
            tag.putString("beamStyle", beamStyle.name());
            tag.putBoolean("beamsVisible", beamsVisible);
        }

        ListTag memberList = new ListTag();
        for (UUID member : members.stream().sorted().toList()) {
            CompoundTag memberTag = new CompoundTag();
            memberTag.putUUID("id", member);
            memberList.add(memberTag);
        }
        tag.put("members", memberList);

        long[] pylonArray = pylonPositions.stream().mapToLong(BlockPos::asLong).sorted().toArray();
        tag.putLongArray("pylons", pylonArray);

        return tag;
    }

    public static QFNetwork load(CompoundTag tag) {
        int numericId = tag.getInt("numericId");
        String name = QuantumFluxNetworkManager.normalizeNetworkName(tag.getString("name"))
                .orElse("Network " + numericId);
        int loadedColor = tag.getInt("color");
        int color = QuantumFluxNetworkManager.isValidColor(loadedColor) ? loadedColor : 0x00FFFF;
        if (!tag.hasUUID("uuid") || !tag.hasUUID("owner") || numericId < 1) {
            throw new IllegalArgumentException("Invalid persisted network identity");
        }
        UUID networkUuid = tag.getUUID("uuid");
        UUID ownerUuid = tag.getUUID("owner");
        if (networkUuid.equals(new UUID(0L, 0L)) || ownerUuid.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("Zero UUID is not a valid network identity");
        }
        QFNetwork network = new QFNetwork(
                networkUuid,
                numericId,
                name,
                color,
                ownerUuid
        );

        try {
            network.accessMode = AccessMode.valueOf(tag.getString("accessMode"));
        } catch (IllegalArgumentException e) {
            network.accessMode = AccessMode.PRIVATE;
        }
        String verifier = tag.contains("passwordVerifier", Tag.TAG_STRING)
                ? tag.getString("passwordVerifier")
                : tag.getString("passwordHash");
        network.passwordVerifier = QFPasswordVerifier.isValidStoredVerifier(verifier) ? verifier : "";
        if (tag.contains("beamStyle", Tag.TAG_STRING) && tag.contains("beamsVisible", Tag.TAG_BYTE)) {
            try {
                network.beamStyle = BeamStyle.valueOf(tag.getString("beamStyle"));
            } catch (IllegalArgumentException ignored) {
                network.beamStyle = BeamStyle.SOLID;
            }
            network.beamsVisible = tag.getBoolean("beamsVisible");
        } else {
            // First loaded member pylon supplies presentation values for legacy worlds.
            network.presentationInitialized = false;
        }

        ListTag memberList = tag.getList("members", Tag.TAG_COMPOUND);
        for (int i = 0; i < memberList.size()
                && network.members.size() < QuantumFluxNetworkManager.MAX_MEMBERS_PER_NETWORK; i++) {
            CompoundTag memberTag = memberList.getCompound(i);
            if (!memberTag.hasUUID("id")) continue;
            UUID memberId = memberTag.getUUID("id");
            if (!memberId.equals(network.owner) && !memberId.equals(new UUID(0L, 0L))) {
                network.members.add(memberId);
            }
        }

        long[] pylonArray = tag.getLongArray("pylons");
        for (int i = 0; i < pylonArray.length
                && network.pylonPositions.size() < QuantumFluxNetworkManager.MAX_PYLONS_PER_NETWORK; i++) {
            long l = pylonArray[i];
            network.pylonPositions.add(BlockPos.of(l));
        }

        return network;
    }
}
