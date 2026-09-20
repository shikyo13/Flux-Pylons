package com.zerotheabsolute.quantumflux.network.data;

import com.zeromods.core.network.ManagedNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import com.zerotheabsolute.quantumflux.util.QFPasswordVerifier;
import com.zerotheabsolute.quantumflux.util.BeamStyle;

import java.util.Set;
import java.util.UUID;

public class QFNetwork {

    public enum AccessMode { PRIVATE, PUBLIC, PASSWORD }

    private final ManagedNetwork<BlockPos> core;
    private final int numericId;
    private int color;
    private AccessMode accessMode;
    private String passwordVerifier;
    private BeamStyle beamStyle;
    private boolean beamsVisible;
    private boolean presentationInitialized;

    public QFNetwork(UUID uuid, int numericId, String name, int color, UUID owner) {
        this.core = new ManagedNetwork<>(uuid, "quantumflux:energy", name, owner);
        this.numericId = numericId;
        this.color = color;
        this.accessMode = AccessMode.PRIVATE;
        this.passwordVerifier = "";
        this.beamStyle = BeamStyle.SOLID;
        this.beamsVisible = true;
        this.presentationInitialized = true;
    }

    // ── Getters ──

    public UUID getUuid() { return core.id(); }
    public int getNumericId() { return numericId; }
    public String getName() { return core.name(); }
    public int getColor() { return color; }
    public UUID getOwner() { return core.owner(); }
    public AccessMode getAccessMode() { return accessMode; }
    public String getPasswordVerifier() { return passwordVerifier; }
    public BeamStyle getBeamStyle() { return beamStyle; }
    public boolean isBeamsVisible() { return beamsVisible; }
    public boolean isPresentationInitialized() { return presentationInitialized; }
    public Set<UUID> getMembers() { return core.memberIds(); }
    public Set<BlockPos> getPylonPositions() { return core.nodes(); }

    // ── Setters ──

    public void setName(String name) { core.rename(name); }
    public void setColor(int color) { this.color = color; }
    public void setAccessMode(AccessMode mode) {
        this.accessMode = mode;
        core.access(mode == AccessMode.PUBLIC, mode == AccessMode.PASSWORD);
    }
    public void setPasswordVerifier(String verifier) { this.passwordVerifier = verifier; }
    public void setBeamStyle(BeamStyle beamStyle) { this.beamStyle = beamStyle; }
    public void setBeamsVisible(boolean beamsVisible) { this.beamsVisible = beamsVisible; }
    public void setPresentationInitialized(boolean initialized) { this.presentationInitialized = initialized; }

    // ── Pylon management ──

    public void addPylon(BlockPos pos) { core.addNode(pos); }
    public void removePylon(BlockPos pos) { core.removeNode(pos); }
    public boolean hasPylon(BlockPos pos) { return core.nodes().contains(pos); }

    // ── Member management ──

    public void addMember(UUID playerId) { core.addMember(playerId); }
    public void removeMember(UUID playerId) { core.removeMember(playerId); }

    // ── Access control ──

    public boolean canAccess(UUID playerId) { return core.canUse(playerId); }
    public boolean canConfigure(UUID playerId) { return core.canConfigure(playerId); }
    public boolean isOwner(UUID playerId) { return core.isOwner(playerId); }
    public boolean isListable(UUID playerId) { return core.canDiscover(playerId); }
    public ManagedNetwork.Snapshot<BlockPos> coreSnapshot() {
        return core.snapshot();
    }

    // ── NBT serialization ──

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("uuid", core.id());
        tag.putInt("numericId", numericId);
        tag.putString("name", core.name());
        tag.putInt("color", color);
        tag.putUUID("owner", core.owner());
        tag.putString("accessMode", accessMode.name());
        tag.putString("passwordVerifier", passwordVerifier);
        if (presentationInitialized) {
            tag.putString("beamStyle", beamStyle.name());
            tag.putBoolean("beamsVisible", beamsVisible);
        }

        ListTag memberList = new ListTag();
        for (UUID member : core.memberIds().stream().sorted().toList()) {
            CompoundTag memberTag = new CompoundTag();
            memberTag.putUUID("id", member);
            memberList.add(memberTag);
        }
        tag.put("members", memberList);

        long[] pylonArray = core.nodes().stream().mapToLong(BlockPos::asLong).sorted().toArray();
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
            network.setAccessMode(AccessMode.valueOf(tag.getString("accessMode")));
        } catch (IllegalArgumentException e) {
            network.setAccessMode(AccessMode.PRIVATE);
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
                && network.core.memberIds().size() < QuantumFluxNetworkManager.MAX_MEMBERS_PER_NETWORK; i++) {
            CompoundTag memberTag = memberList.getCompound(i);
            if (!memberTag.hasUUID("id")) continue;
            UUID memberId = memberTag.getUUID("id");
            if (!memberId.equals(network.core.owner()) && !memberId.equals(new UUID(0L, 0L))) {
                network.core.addMember(memberId);
            }
        }

        long[] pylonArray = tag.getLongArray("pylons");
        for (int i = 0; i < pylonArray.length
                && network.core.nodes().size() < QuantumFluxNetworkManager.MAX_PYLONS_PER_NETWORK; i++) {
            long l = pylonArray[i];
            network.core.addNode(BlockPos.of(l));
        }

        return network;
    }
}
