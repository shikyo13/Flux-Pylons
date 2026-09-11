package com.zerotheabsolute.quantumflux.network.data;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import com.zerotheabsolute.quantumflux.util.QFPasswordUtil;
import com.zerotheabsolute.quantumflux.util.QFPasswordVerifier;
import com.zerotheabsolute.quantumflux.QFConfig;
import com.zerotheabsolute.quantumflux.util.BeamStyle;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Dimension-level persistent storage for Flux Pylons networks.
 * One instance per ServerLevel, accessed via {@link #get(ServerLevel)}.
 */
public class QuantumFluxNetworkManager extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "quantumflux_networks";
    private static final int OVERFLOW_MIGRATION_VERSION = 1;
    public static final int MAX_NETWORK_NAME_LENGTH = 32;
    public static final int MAX_NETWORKS_PER_OWNER = 32;
    public static final int MAX_NETWORKS_PER_DIMENSION = 256;
    public static final int MAX_MEMBERS_PER_NETWORK = 64;
    public static final int MAX_PYLONS_PER_NETWORK = 256;

    public enum PylonMutationResult {
        SUCCESS,
        NETWORK_NOT_FOUND,
        FORBIDDEN,
        ALREADY_ASSIGNED,
        ASSIGNED_TO_OTHER_NETWORK,
        NOT_ASSIGNED,
        NETWORK_FULL
    }

    private final Map<UUID, QFNetwork> networks = new HashMap<>();
    private final AtomicInteger nextNumericId = new AtomicInteger(1);

    // Reverse lookup: pylon position -> network UUID
    private final Map<BlockPos, UUID> pylonToNetwork = new HashMap<>();
    // Transient server-thread coordination. These values are scheduling state, not world data.
    private final Map<UUID, Long> lastNetworkWorkTick = new HashMap<>();
    private final Map<UUID, Integer> distributionCursors = new HashMap<>();
    private long lastValidationTick = Long.MIN_VALUE;

    // ── Factory ──

    public static QuantumFluxNetworkManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                QuantumFluxNetworkManager::load, QuantumFluxNetworkManager::new,
                DATA_NAME
        );
    }

    public QuantumFluxNetworkManager() {}

    // ── Network CRUD ──

    public QFNetwork createNetwork(UUID owner, String name, int color) {
        Optional<String> normalizedName = normalizeNetworkName(name);
        if (owner == null || normalizedName.isEmpty() || !isValidColor(color)
                || getPlayerNetworks(owner).size() >= MAX_NETWORKS_PER_OWNER
                || networks.size() >= MAX_NETWORKS_PER_DIMENSION) {
            return null;
        }

        int numId = nextNumericId.get();
        if (numId < 1 || numId == Integer.MAX_VALUE) return null;
        nextNumericId.incrementAndGet();
        UUID networkId = UUID.randomUUID();
        QFNetwork network = new QFNetwork(networkId, numId, normalizedName.get(), color, owner);
        network.setBeamStyle(configuredDefaultBeamStyle());
        networks.put(networkId, network);
        setDirty();
        return network;
    }

    public boolean deleteNetwork(UUID networkId, UUID requestor) {
        QFNetwork network = networks.get(networkId);
        if (network == null) return false;
        if (!network.getOwner().equals(requestor)) return false;

        for (BlockPos pylonPos : network.getPylonPositions()) {
            pylonToNetwork.remove(pylonPos, networkId);
        }

        networks.remove(networkId);
        lastNetworkWorkTick.remove(networkId);
        distributionCursors.remove(networkId);
        setDirty();
        return true;
    }

    public QFNetwork getNetwork(UUID networkId) {
        return networks.get(networkId);
    }

    public boolean updatePresentation(UUID networkId, UUID requestor,
                                      BeamStyle beamStyle, Boolean beamsVisible) {
        QFNetwork network = networks.get(networkId);
        if (network == null || !network.canConfigure(requestor)) return false;
        boolean changed = false;
        if (beamStyle != null && beamStyle != network.getBeamStyle()) {
            network.setBeamStyle(beamStyle);
            changed = true;
        }
        if (beamsVisible != null && beamsVisible != network.isBeamsVisible()) {
            network.setBeamsVisible(beamsVisible);
            changed = true;
        }
        if (!network.isPresentationInitialized()) {
            network.setPresentationInitialized(true);
            changed = true;
        }
        if (!changed) return false;
        setDirty();
        return true;
    }

    /** Captures a legacy pylon's settings once, before network-wide presentation existed. */
    public void initializeLegacyPresentation(UUID networkId, BeamStyle beamStyle, boolean beamsVisible) {
        QFNetwork network = networks.get(networkId);
        if (network == null || network.isPresentationInitialized()) return;
        network.setBeamStyle(beamStyle == null ? configuredDefaultBeamStyle() : beamStyle);
        network.setBeamsVisible(beamsVisible);
        network.setPresentationInitialized(true);
        setDirty();
    }

    public boolean updateNetwork(UUID networkId, UUID requestor,
                                 String name, Integer color,
                                 QFNetwork.AccessMode accessMode, String password) {
        QFNetwork network = networks.get(networkId);
        if (network == null || !network.isOwner(requestor)) return false;

        Optional<String> normalizedName = name == null
                ? Optional.empty()
                : normalizeNetworkName(name);
        if (name != null && normalizedName.isEmpty()) return false;
        if (color != null && !isValidColor(color)) return false;
        if (password != null && !password.isEmpty() && !QFPasswordUtil.isValidPassword(password)) {
            return false;
        }

        boolean changed = false;
        if (name != null && !normalizedName.orElseThrow().equals(network.getName())) {
            network.setName(normalizedName.orElseThrow());
            changed = true;
        }
        if (color != null && color.intValue() != network.getColor()) {
            network.setColor(color);
            changed = true;
        }
        if (accessMode != null && accessMode != network.getAccessMode()) {
            network.setAccessMode(accessMode);
            changed = true;
        }
        if (password != null) {
            if (password.isEmpty()) {
                if (!network.getPasswordVerifier().isEmpty()) {
                    network.setPasswordVerifier("");
                    changed = true;
                }
            } else if (!QFPasswordVerifier.verify(password, network.getPasswordVerifier())) {
                network.setPasswordVerifier(QFPasswordVerifier.create(password));
                changed = true;
            }
        }
        if (!changed) return false;
        setDirty();
        return true;
    }

    // ── Pylon management ──

    public boolean addPylon(UUID networkId, BlockPos pos) {
        QFNetwork network = networks.get(networkId);
        if (network == null) return false;
        if (network.getPylonPositions().size() >= MAX_PYLONS_PER_NETWORK) return false;

        UUID existingNetwork = pylonToNetwork.get(pos);
        if (existingNetwork != null && !existingNetwork.equals(networkId)) return false;

        network.addPylon(pos);
        pylonToNetwork.put(pos, networkId);
        setDirty();
        return true;
    }

    /**
     * Assigns or transfers a pylon after checking both the destination and the
     * current network. This is the only API player-driven assignment should use.
     */
    public PylonMutationResult assignPylon(UUID networkId, BlockPos pos, UUID requestor) {
        QFNetwork destination = networks.get(networkId);
        if (destination == null) return PylonMutationResult.NETWORK_NOT_FOUND;
        if (!destination.canConfigure(requestor)) return PylonMutationResult.FORBIDDEN;

        UUID currentId = pylonToNetwork.get(pos);
        if (networkId.equals(currentId)) return PylonMutationResult.ALREADY_ASSIGNED;

        QFNetwork current = currentId == null ? null : networks.get(currentId);
        if (current != null && !current.canConfigure(requestor)) {
            return PylonMutationResult.ASSIGNED_TO_OTHER_NETWORK;
        }
        if (destination.getPylonPositions().size() >= MAX_PYLONS_PER_NETWORK) {
            return PylonMutationResult.NETWORK_FULL;
        }

        if (current != null) current.removePylon(pos);
        destination.addPylon(pos);
        pylonToNetwork.put(pos, networkId);
        setDirty();
        return PylonMutationResult.SUCCESS;
    }

    /** Removes a pylon only when the requester can configure its current network. */
    public PylonMutationResult unassignPylon(BlockPos pos, UUID requestor) {
        UUID currentId = pylonToNetwork.get(pos);
        if (currentId == null) return PylonMutationResult.NOT_ASSIGNED;

        QFNetwork current = networks.get(currentId);
        if (current != null && !current.canConfigure(requestor)) {
            return PylonMutationResult.FORBIDDEN;
        }

        pylonToNetwork.remove(pos);
        if (current != null) current.removePylon(pos);
        setDirty();
        return PylonMutationResult.SUCCESS;
    }

    public boolean removePylon(UUID networkId, BlockPos pos) {
        boolean changed = false;
        QFNetwork network = networks.get(networkId);
        if (network != null && network.hasPylon(pos)) {
            network.removePylon(pos);
            changed = true;
        }
        changed |= pylonToNetwork.remove(pos, networkId);
        if (changed) setDirty();
        return changed;
    }

    public boolean removePylonFromAny(BlockPos pos) {
        UUID networkId = pylonToNetwork.remove(pos);
        if (networkId != null) {
            QFNetwork network = networks.get(networkId);
            if (network != null) network.removePylon(pos);
            setDirty();
            return true;
        }
        return false;
    }

    public QFNetwork getNetworkForPylon(BlockPos pos) {
        UUID networkId = pylonToNetwork.get(pos);
        return networkId != null ? networks.get(networkId) : null;
    }

    /**
     * Allows exactly one loaded member pylon to perform shared work for a network
     * on a given game tick. This avoids O(n^2) coordinator election scans.
     */
    public boolean claimNetworkWork(UUID networkId, BlockPos pylonPos, long gameTick) {
        QFNetwork network = networks.get(networkId);
        if (network == null || !network.hasPylon(pylonPos)) return false;
        Long previous = lastNetworkWorkTick.put(networkId, gameTick);
        return previous == null || previous.longValue() != gameTick;
    }

    public int getDistributionCursor(UUID networkId) {
        return distributionCursors.getOrDefault(networkId, 0);
    }

    public void setDistributionCursor(UUID networkId, int cursor) {
        if (networks.containsKey(networkId)) distributionCursors.put(networkId, cursor);
    }

    /** Allows one global stale-pylon validation pass for this dimension and tick. */
    public boolean claimValidation(long gameTick) {
        if (lastValidationTick == gameTick) return false;
        lastValidationTick = gameTick;
        return true;
    }

    /**
     * Verifies that block-entity state and SavedData agree, then applies the
     * network's configuration permission. Unassigned pylons are configurable by
     * a nearby player; claim/permissions mods remain responsible for world access.
     */
    public boolean canConfigurePylon(BlockPos pos, UUID blockEntityNetworkId, UUID requestor) {
        UUID indexedNetworkId = pylonToNetwork.get(pos);
        if (!Objects.equals(indexedNetworkId, blockEntityNetworkId)) return false;
        if (indexedNetworkId == null) return true;
        QFNetwork network = networks.get(indexedNetworkId);
        return network != null && network.canConfigure(requestor);
    }

    /**
     * Removes pylon positions where no pylon block entity exists.
     * Call periodically to clean up stale entries from broken pylons.
     */
    public void validatePylons(net.minecraft.server.level.ServerLevel level) {
        boolean changed = false;
        for (QFNetwork network : networks.values()) {
            var iter = network.getPylonPositions().iterator();
            while (iter.hasNext()) {
                BlockPos pos = iter.next();
                if (level.isLoaded(pos)) {
                    net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
                    if (!(be instanceof com.zerotheabsolute.quantumflux.blockentity.QuantumPylonBlockEntity)) {
                        iter.remove();
                        pylonToNetwork.remove(pos, network.getUuid());
                        changed = true;
                    }
                }
            }
        }
        if (changed) setDirty();
    }

    // ── Member management ──

    public boolean addMember(UUID networkId, UUID requestor, UUID member) {
        QFNetwork network = networks.get(networkId);
        if (network == null || !network.isOwner(requestor) || member == null
                || member.equals(network.getOwner())) return false;
        if (network.getMembers().contains(member)) return false;
        if (network.getMembers().size() >= MAX_MEMBERS_PER_NETWORK) return false;
        network.addMember(member);
        setDirty();
        return true;
    }

    public boolean removeMember(UUID networkId, UUID requestor, UUID member) {
        QFNetwork network = networks.get(networkId);
        if (network == null || !network.isOwner(requestor) || member == null
                || member.equals(network.getOwner())) return false;
        if (!network.getMembers().contains(member)) return false;
        network.removeMember(member);
        setDirty();
        return true;
    }

    /**
     * Attempts to join a PASSWORD-mode network and adds the requester as a member.
     */
    public boolean joinWithPassword(UUID networkId, UUID requester, String password) {
        QFNetwork network = networks.get(networkId);
        if (network == null) return false;
        if (network.getAccessMode() != QFNetwork.AccessMode.PASSWORD) return false;
        if (!QFPasswordVerifier.verify(password, network.getPasswordVerifier())) return false;
        if (QFPasswordVerifier.isLegacy(network.getPasswordVerifier())) {
            network.setPasswordVerifier(QFPasswordVerifier.create(password));
            setDirty();
        }
        if (network.canConfigure(requester)) return true;
        if (network.getMembers().size() >= MAX_MEMBERS_PER_NETWORK) return false;

        network.addMember(requester);
        setDirty();
        return true;
    }

    // ── Queries ──

    public List<QFNetwork> getPlayerNetworks(UUID playerId) {
        return networks.values().stream()
                .filter(n -> n.getOwner().equals(playerId))
                .collect(Collectors.toList());
    }

    public List<QFNetwork> getAccessibleNetworks(UUID playerId) {
        return networks.values().stream()
                .filter(n -> n.canAccess(playerId))
                .collect(Collectors.toList());
    }

    /** Networks visible in a player's list (includes PASSWORD networks they haven't joined). */
    public List<QFNetwork> getListableNetworks(UUID playerId) {
        return networks.values().stream()
                .filter(n -> n.isListable(playerId))
                .collect(Collectors.toList());
    }

    public QFNetwork getNetworkByNumericId(int numericId) {
        for (QFNetwork network : networks.values()) {
            if (network.getNumericId() == numericId) return network;
        }
        return null;
    }

    public Collection<QFNetwork> getAllNetworks() {
        return Collections.unmodifiableCollection(networks.values());
    }

    public static Optional<String> normalizeNetworkName(String name) {
        if (name == null) return Optional.empty();
        String normalized = name.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > MAX_NETWORK_NAME_LENGTH) return Optional.empty();
        if (normalized.indexOf('\u00a7') >= 0) return Optional.empty();
        if (normalized.codePoints().anyMatch(codePoint ->
                Character.isISOControl(codePoint)
                        || Character.getType(codePoint) == Character.FORMAT)) return Optional.empty();
        return Optional.of(normalized);
    }

    public static boolean isValidColor(int color) {
        return color >= 0 && color <= 0xFFFFFF;
    }

    private static BeamStyle configuredDefaultBeamStyle() {
        try {
            return BeamStyle.valueOf(QFConfig.DEFAULT_BEAM_STYLE.get());
        } catch (IllegalArgumentException ignored) {
            return BeamStyle.SOLID;
        }
    }

    // ── Serialization ──

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        tag.putInt("nextNumericId", nextNumericId.get());
        tag.putInt("overflowMigrationVersion", OVERFLOW_MIGRATION_VERSION);

        ListTag networkList = new ListTag();
        for (QFNetwork network : networks.values()) {
            networkList.add(network.save());
        }
        tag.put("networks", networkList);

        return tag;
    }

    public static QuantumFluxNetworkManager load(CompoundTag tag) {
        QuantumFluxNetworkManager manager = new QuantumFluxNetworkManager();
        manager.nextNumericId.set(tag.getInt("nextNumericId"));
        if (manager.nextNumericId.get() < 1) manager.nextNumericId.set(1);

        ListTag networkList = tag.getList("networks", Tag.TAG_COMPOUND);
        int highestNumericId = 0;
        Set<Integer> numericIds = new HashSet<>();
        for (int i = 0; i < networkList.size(); i++) {
            QFNetwork network;
            try {
                network = QFNetwork.load(networkList.getCompound(i));
            } catch (RuntimeException ignored) {
                continue;
            }
            if (manager.networks.containsKey(network.getUuid())
                    || !numericIds.add(network.getNumericId())) continue;
            manager.networks.put(network.getUuid(), network);
            highestNumericId = Math.max(highestNumericId, network.getNumericId());

            // Rebuild reverse lookup. If old/corrupt data assigns one position
            // to multiple networks, the first persisted owner wins.
            Iterator<BlockPos> positions = network.getPylonPositions().iterator();
            while (positions.hasNext()) {
                BlockPos pos = positions.next();
                if (manager.pylonToNetwork.putIfAbsent(pos, network.getUuid()) != null) {
                    positions.remove();
                }
            }
        }
        if (highestNumericId < Integer.MAX_VALUE) {
            manager.nextNumericId.set(Math.max(manager.nextNumericId.get(), highestNumericId + 1));
        }

        if (tag.getInt("overflowMigrationVersion") < OVERFLOW_MIGRATION_VERSION
                && manager.networks.size() > MAX_NETWORKS_PER_DIMENSION) {
            List<QFNetwork> ordered = manager.networks.values().stream()
                    .sorted(Comparator.comparingInt(QFNetwork::getNumericId))
                    .toList();
            int privatized = 0;
            for (int index = MAX_NETWORKS_PER_DIMENSION; index < ordered.size(); index++) {
                QFNetwork overflow = ordered.get(index);
                if (overflow.getAccessMode() != QFNetwork.AccessMode.PRIVATE) {
                    overflow.setAccessMode(QFNetwork.AccessMode.PRIVATE);
                    privatized++;
                }
            }
            manager.setDirty();
            LOGGER.warn("Loaded {} legacy Flux Pylons networks above the {}-network dimension cap. "
                            + "All networks were preserved; {} overflow networks were migrated to PRIVATE. "
                            + "Their owners and existing members retain access.",
                    manager.networks.size(), MAX_NETWORKS_PER_DIMENSION, privatized);
        }

        return manager;
    }
}
