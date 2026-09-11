package com.zerotheabsolute.quantumflux.blockentity;

import com.zerotheabsolute.quantumflux.QFConfig;
import com.zerotheabsolute.quantumflux.block.QuantumPylonBlock;
import com.zerotheabsolute.quantumflux.energy.PylonEnergyStorage;
import com.zerotheabsolute.quantumflux.energy.FairEnergyDistributor;
import com.zerotheabsolute.quantumflux.energy.ProportionalEnergyAllocator;
import com.zerotheabsolute.quantumflux.init.QFBlockEntities;
import com.zerotheabsolute.quantumflux.item.UpgradeType;
import com.zerotheabsolute.quantumflux.util.RedstoneMode;
import com.zerotheabsolute.quantumflux.network.PylonSyncPayload;
import com.zerotheabsolute.quantumflux.network.PylonTelemetryPayload;
import com.zerotheabsolute.quantumflux.network.data.QFNetwork;
import com.zerotheabsolute.quantumflux.network.data.QuantumFluxNetworkManager;
import com.zerotheabsolute.quantumflux.util.BeamStyle;
import com.zerotheabsolute.quantumflux.util.EnergyHelper;
import com.zerotheabsolute.quantumflux.util.ConnectionStatus;
import com.zerotheabsolute.quantumflux.util.PriorityMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class QuantumPylonBlockEntity extends BlockEntity {

    // ── Energy ──
    private PylonEnergyStorage energyStorage;
    private final PylonUpgradeInventory upgrades = new PylonUpgradeInventory(this);

    // ── Connections ──
    private final List<ConnectionData> connections = new ArrayList<>();

    // ── Beam cosmetics (synced to client) ──
    private BeamStyle beamStyle = BeamStyle.SOLID;
    private int beamColor = 0x00FFFF;
    private float glowIntensity = 1.0f;
    private boolean beamsVisible = true;
    private float pulseSpeed = 1.0f;

    // ── Network ──
    @Nullable private UUID networkId = null;

    // ── Distribution ──
    private PriorityMode priorityMode = PriorityMode.EQUAL;
    private RedstoneMode redstoneMode = RedstoneMode.IGNORE;
    private boolean lastOutputEnabled = true;
    private double totalThroughput = 0;
    private long accumulatedThroughput = 0;
    private double peakThroughput = 0;
    private int rotatingIndex = 0;
    private boolean syncQueued = true;

    public QuantumPylonBlockEntity(BlockPos pos, BlockState state) {
        super(QFBlockEntities.QUANTUM_PYLON_BE.get(), pos, state);
        this.energyStorage = createEnergyStorage(QFConfig.PYLON_BUFFER_SIZE.get(), 0);
        this.priorityMode = configuredDefaultPriority();
        this.beamStyle = configuredDefaultBeamStyle();
        this.beamColor = QFConfig.DEFAULT_BEAM_COLOR.get() & 0xFFFFFF;
    }

    // ══════════════════════════════════════════
    //  Server Tick — 4-phase logic per spec 4.3
    // ══════════════════════════════════════════

    public static void serverTick(Level level, BlockPos pos, BlockState state, QuantumPylonBlockEntity self) {
        long gameTime = level.getGameTime();
        boolean outputEnabled = self.isOutputEnabled();
        if (outputEnabled != self.lastOutputEnabled) {
            self.lastOutputEnabled = outputEnabled;
            self.queueSync();
        }
        if (self.energyStorage.getNominalCapacity() != self.getEffectiveBufferSize()) self.onUpgradesChanged();

        if (gameTime % 60 == 0 && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            self.reconcileNetworkMembership(QuantumFluxNetworkManager.get(serverLevel));
        }

        // Phase 1: validate connections every 20 ticks
        if (gameTime % 20 == 0) {
            self.validateConnections();
        }

        int tickInterval = QFConfig.TICK_INTERVAL.get();
        boolean distributionTick = gameTime % tickInterval == 0;
        QuantumFluxNetworkManager manager = level instanceof net.minecraft.server.level.ServerLevel serverLevel
                ? QuantumFluxNetworkManager.get(serverLevel) : null;
        QFNetwork network = manager == null || self.networkId == null
                ? null : manager.getNetwork(self.networkId);
        if (network != null && network.hasPylon(pos)) {
            // One coordinator spends the loaded pool for every member. Later
            // member ticks must not spend their transfer budgets a second time.
            if ((distributionTick || gameTime % 5 == 0)
                    && manager.claimNetworkWork(self.networkId, pos, gameTime)) {
                List<QuantumPylonBlockEntity> siblings = self.loadedNetworkPylons(network);
                if (distributionTick) self.distributeNetworkEnergy(manager, siblings);
                if (gameTime % 5 == 0) balanceNetworkEnergy(siblings);
                if (gameTime % 60 == 0 && manager.claimValidation(gameTime)) {
                    manager.validatePylons((net.minecraft.server.level.ServerLevel) level);
                }
            }
        } else if (distributionTick) {
            self.distributeEnergy();
        }

        // Phase 3: update block state (both halves)
        // A fully utilized pylon can deliver every incoming FE and finish the
        // tick empty. Keep its field lit while current/recent output is flowing.
        boolean shouldBeActive = self.energyStorage.getEnergyStored() > 0
                || self.accumulatedThroughput > 0 || self.totalThroughput > 0;
        if (state.getValue(QuantumPylonBlock.ACTIVE) != shouldBeActive) {
            level.setBlock(pos, state.setValue(QuantumPylonBlock.ACTIVE, shouldBeActive), 3);
            // Sync ACTIVE to TOP half
            BlockPos topPos = pos.above();
            BlockState topState = level.getBlockState(topPos);
            if (topState.getBlock() instanceof QuantumPylonBlock
                    && topState.getValue(QuantumPylonBlock.HALF) == QuantumPylonBlock.PylonHalf.TOP) {
                level.setBlock(topPos, topState.setValue(QuantumPylonBlock.ACTIVE, shouldBeActive), 3);
            }
        }

        // Phase 4: one telemetry/full-state update per second to players tracking this chunk.
        boolean telemetryTick = gameTime % 20 == 0;
        if (telemetryTick) {
            self.totalThroughput = Math.min(Integer.MAX_VALUE, self.accumulatedThroughput / 20.0);
            if (self.totalThroughput > self.peakThroughput) {
                self.peakThroughput = self.totalThroughput;
                self.setChanged();
            }
            self.accumulatedThroughput = 0;
            for (ConnectionData connection : self.connections) {
                connection.rollTelemetryWindow(20);
            }
        }
        if (telemetryTick) self.syncTelemetryToTrackingClients();
        if (self.syncQueued) {
            self.syncQueued = false;
            self.syncToTrackingClients();
        }
    }

    // ══════════════════════════════════════════
    //  Connection Validation (spec 4.4)
    // ══════════════════════════════════════════

    private void validateConnections() {
        if (level == null) return;
        boolean changed = false;

        Iterator<ConnectionData> it = connections.iterator();
        while (it.hasNext()) {
            ConnectionData conn = it.next();

            if (!level.isLoaded(conn.getPos())) {
                continue; // skip unloaded chunks, don't remove the connection
            }
            if (level.getBlockState(conn.getPos()).isAir()) {
                it.remove(); changed = true; continue;
            }
            // Keep links outside a reduced range as inactive configuration.
            // Restoring range resumes them; the controller explains the limit.
            // A machine may temporarily disable its FE input (side settings,
            // redstone control, or multiblock rebuilding). Retain the link so
            // delivery resumes when the capability becomes available again.

            changed |= conn.setDisplayName(getBlockDisplayName(conn.getPos()));
        }

        if (changed) {
            setChanged();
            queueSync();
        }
    }

    // ══════════════════════════════════════════
    //  Energy Distribution (spec 4.5)
    // ══════════════════════════════════════════

    private List<QuantumPylonBlockEntity> loadedNetworkPylons(QFNetwork network) {
        List<QuantumPylonBlockEntity> siblings = new ArrayList<>();
        for (BlockPos pylonPos : network.getPylonPositions()) {
            if (level.isLoaded(pylonPos)
                    && level.getBlockEntity(pylonPos) instanceof QuantumPylonBlockEntity pylon
                    && !pylon.isRemoved() && Objects.equals(networkId, pylon.networkId)) {
                siblings.add(pylon);
            }
        }
        siblings.sort(Comparator.comparing(QuantumPylonBlockEntity::getBlockPos,
                Comparator.<BlockPos>comparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getZ)));
        return siblings;
    }

    private void distributeNetworkEnergy(QuantumFluxNetworkManager manager,
                                         List<QuantumPylonBlockEntity> siblings) {
        long available = siblings.stream().mapToLong(pylon -> pylon.energyStorage.getEnergyStored()).sum();
        int[] limits = siblings.stream().mapToInt(pylon -> pylon.connections.isEmpty() || !pylon.isOutputEnabled()
                ? 0 : pylon.getCycleTransferLimit()).toArray();
        FairEnergyDistributor.Result result = FairEnergyDistributor.distribute(available, limits,
                manager.getDistributionCursor(networkId), (index, offer) -> {
                    int sent = siblings.get(index).distributeEnergy(offer);
                    int remaining = sent;
                    for (QuantumPylonBlockEntity donor : siblings) {
                        remaining -= donor.energyStorage.consumeEnergy(remaining);
                        if (remaining == 0) break;
                    }
                    return sent;
                });
        if (result.transferred() > 0) manager.setDistributionCursor(networkId, result.nextIndex());
    }

    private static void balanceNetworkEnergy(List<QuantumPylonBlockEntity> siblings) {
        if (siblings.size() <= 1) return;
        long totalEnergy = siblings.stream().mapToLong(pylon -> pylon.energyStorage.getEnergyStored()).sum();
        int[] capacities = siblings.stream()
                .mapToInt(pylon -> pylon.energyStorage.getMaxEnergyStored())
                .toArray();
        int[] allocation = ProportionalEnergyAllocator.allocate(totalEnergy, capacities);
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).energyStorage.setEnergy(allocation[i]);
        }
    }

    private void distributeEnergy() {
        int available = Math.min(energyStorage.getEnergyStored(), getCycleTransferLimit());
        energyStorage.consumeEnergy(distributeEnergy(available));
    }

    public int getEffectiveTransferLimit() {
        return UpgradeType.THROUGHPUT.effectiveValue(QFConfig.MAX_TRANSFER_PER_TICK.get(), upgrades.level(UpgradeType.THROUGHPUT));
    }

    private int getCycleTransferLimit() {
        return (int) Math.min(Integer.MAX_VALUE,
                (long) getEffectiveTransferLimit() * QFConfig.TICK_INTERVAL.get());
    }

    private int distributeEnergy(int availableEnergy) {
        if (level == null || availableEnergy <= 0 || connections.isEmpty() || !isOutputEnabled()) return 0;
        int totalTransferred = 0;

        switch (priorityMode) {
            case EQUAL -> totalTransferred = distributeEqual(availableEnergy);
            case ROUND_ROBIN -> totalTransferred = distributeRoundRobin(availableEnergy);
            case NEAREST_FIRST -> totalTransferred = distributeNearestFirst(availableEnergy);
        }

        this.accumulatedThroughput += totalTransferred;
        return totalTransferred;
    }

    private int distributeEqual(int availableEnergy) {
        int count = Math.min(connections.size(), getEffectiveMaxConnections());
        int start = Math.floorMod(rotatingIndex, count);
        List<ConnectionData> accepting = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            accepting.add(connections.get((start + i) % count));
        }
        int remaining = availableEnergy;
        int total = 0;
        int nextStart = (start + 1) % count;

        // Water-fill in bounded passes. Targets that cannot accept their fair
        // share drop out, and their unused allocation is offered to the rest.
        while (remaining > 0 && !accepting.isEmpty()) {
            int share = remaining / accepting.size();
            int remainder = remaining % accepting.size();
            int transferredThisPass = 0;
            List<ConnectionData> stillAccepting = new ArrayList<>(accepting.size());

            for (int i = 0; i < accepting.size(); i++) {
                ConnectionData connection = accepting.get(i);
                int offered = share + (i < remainder ? 1 : 0);
                int sent = pushEnergyToBlock(connection.getPos(), offered);
                connection.recordTransfer(sent);
                remaining -= sent;
                total += sent;
                transferredThisPass += sent;
                if (sent > 0 && i < remainder) {
                    nextStart = (connections.indexOf(connection) + 1) % count;
                }
                if (sent == offered) stillAccepting.add(connection);
            }

            // With a one-FE budget, a full first target can reject the only
            // nonzero offer. Continue after dropping it so later targets get
            // a chance, even though this pass delivered nothing.
            if (transferredThisPass == 0 && stillAccepting.size() == accepting.size()) break;
            accepting = stillAccepting;
        }

        // Some FE adapters accept only whole native-energy increments. A fair
        // share can be too small even when the pooled remainder is usable.
        // Offer that remainder once per target; keep anything still rejected
        // in the source buffer until more energy arrives.
        for (int i = 0; remaining > 0 && i < count; i++) {
            int index = (start + i) % count;
            ConnectionData connection = connections.get(index);
            int sent = pushEnergyToBlock(connection.getPos(), remaining);
            connection.recordTransfer(sent);
            remaining -= sent;
            total += sent;
            if (sent > 0) nextStart = (index + 1) % count;
        }

        if (total > 0) {
            // Rotate indivisible remainders, including trickles smaller than
            // the number of consumers, instead of starving the last links.
            rotatingIndex = nextStart;
        }
        return total;
    }

    private int distributeRoundRobin(int availableEnergy) {
        int count = Math.min(connections.size(), getEffectiveMaxConnections());
        if (count == 0) return 0;

        int idx = Math.floorMod(rotatingIndex, count);
        int remaining = availableEnergy;
        int total = 0;
        int checked = 0;

        while (remaining > 0 && checked < count) {
            ConnectionData conn = connections.get(idx);
            int sent = pushEnergyToBlock(conn.getPos(), remaining);
            conn.recordTransfer(sent);
            total += sent;
            remaining -= sent;
            idx = (idx + 1) % count;
            checked++;
        }

        rotatingIndex = idx;
        return total;
    }

    private int distributeNearestFirst(int availableEnergy) {
        List<ConnectionData> sorted = new ArrayList<>(connections.subList(0,
                Math.min(connections.size(), getEffectiveMaxConnections())));
        BlockPos pylonPos = getBlockPos();
        sorted.sort(Comparator.comparingDouble(c -> c.getPos().distSqr(pylonPos)));

        int remaining = availableEnergy;
        int total = 0;

        for (ConnectionData conn : sorted) {
            int sent = pushEnergyToBlock(conn.getPos(), remaining);
            conn.recordTransfer(sent);
            total += sent;
            remaining -= sent;
            if (remaining <= 0) break;
        }
        return total;
    }

    private int pushEnergyToBlock(BlockPos target, int maxAmount) {
        if (level == null || maxAmount <= 0 || distanceTo(target) > getEffectiveRange()
                || !EnergyHelper.isWirelessReceiver(level, target)) return 0;

        // Internal (null-side) access may bypass a machine's input settings.
        for (Direction dir : Direction.values()) {
            IEnergyStorage storage = EnergyHelper.getEnergyCapability(level, target, dir);
            if (storage != null && storage.canReceive()) {
                int accepted = Math.max(0, Math.min(maxAmount, storage.receiveEnergy(maxAmount, false)));
                if (accepted > 0) return accepted;
            }
        }
        return 0;
    }

    // ══════════════════════════════════════════
    //  Connection Management
    // ══════════════════════════════════════════

    public boolean tryLink(BlockPos target) {
        if (level == null) return false;
        if (target.equals(getBlockPos()) || target.equals(getBlockPos().above())) return false;
        if (connections.size() >= getEffectiveMaxConnections()) return false;
        if (distanceTo(target) > getEffectiveRange()) return false;
        if (!EnergyHelper.isWirelessReceiver(level, target)
                || !EnergyHelper.blockAcceptsEnergy(level, target)) return false;

        // Check for duplicate
        for (ConnectionData conn : connections) {
            if (conn.getPos().equals(target)) return false;
        }

        connections.add(new ConnectionData(target, getBlockDisplayName(target)));
        setChanged();
        queueSync();
        return true;
    }

    public boolean unlink(BlockPos target) {
        boolean removed = connections.removeIf(c -> c.getPos().equals(target));
        if (removed) {
            setChanged();
            queueSync();
        }
        return removed;
    }

    public void unlinkAll() {
        if (!connections.isEmpty()) {
            connections.clear();
            setChanged();
            queueSync();
        }
    }

    public boolean isLinkedTo(BlockPos target) {
        for (ConnectionData conn : connections) {
            if (conn.getPos().equals(target)) return true;
        }
        return false;
    }

    /** Send a sync payload to a specific player (used when opening GUI). */
    public void sendSyncTo(net.minecraft.server.level.ServerPlayer player) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, buildSyncPayload());
    }

    // ══════════════════════════════════════════
    //  Sync & Serialization
    // ══════════════════════════════════════════

    private PylonSyncPayload buildSyncPayload() {
        long availableEnergy = getAvailableEnergy();
        List<PylonSyncPayload.ConnectionEntry> connEntries = new ArrayList<>();
        for (ConnectionData conn : connections) {
            connEntries.add(new PylonSyncPayload.ConnectionEntry(
                    conn.getPos(), conn.getDisplayName(), conn.getLastTransferred(),
                    describeConnection(conn, availableEnergy)));
        }
        return new PylonSyncPayload(
                getBlockPos(),
                energyStorage.getEnergyStored(),
                energyStorage.getMaxEnergyStored(),
                totalThroughput,
                peakThroughput,
                connEntries,
                beamStyle,
                beamColor,
                glowIntensity,
                beamsVisible,
                pulseSpeed,
                priorityMode,
                getEffectiveRange(),
                getEffectiveMaxConnections(),
                getEffectiveTransferLimit(),
                availableEnergy,
                networkId, redstoneMode, isOutputEnabled()
        );
    }

    private void syncToTrackingClients() {
        if (level == null || level.isClientSide) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingChunk(
                (net.minecraft.server.level.ServerLevel) level,
                new ChunkPos(getBlockPos()),
                buildSyncPayload());
    }

    private void syncTelemetryToTrackingClients() {
        if (level == null || level.isClientSide) return;
        long availableEnergy = getAvailableEnergy();
        List<PylonTelemetryPayload.ConnectionReading> readings = connections.stream()
                .limit(PylonSyncPayload.MAX_SYNCED_CONNECTIONS)
                .map(connection -> new PylonTelemetryPayload.ConnectionReading(
                        connection.getLastTransferred(), describeConnection(connection, availableEnergy)))
                .toList();
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingChunk(
                (net.minecraft.server.level.ServerLevel) level,
                new ChunkPos(getBlockPos()),
                new PylonTelemetryPayload(getBlockPos(), energyStorage.getEnergyStored(),
                        energyStorage.getMaxEnergyStored(), totalThroughput, peakThroughput,
                        availableEnergy, isOutputEnabled(), readings));
    }

    /** Spendable FE only; this never loads a sibling chunk. */
    public long getAvailableEnergy() {
        if (networkId != null && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            QFNetwork network = QuantumFluxNetworkManager.get(serverLevel).getNetwork(networkId);
            if (network != null && network.hasPylon(getBlockPos())) {
                return loadedNetworkPylons(network).stream()
                        .mapToLong(pylon -> pylon.energyStorage.getEnergyStored()).sum();
            }
        }
        return energyStorage.getEnergyStored();
    }

    public ConnectionStatus getConnectionStatus(BlockPos target) {
        for (ConnectionData connection : connections) {
            if (connection.getPos().equals(target)) return describeConnection(connection, getAvailableEnergy());
        }
        return ConnectionStatus.UNAVAILABLE;
    }

    private ConnectionStatus describeConnection(ConnectionData connection, long availableEnergy) {
        if (!isOutputEnabled()) return ConnectionStatus.PAUSED;
        BlockPos target = connection.getPos();
        if (connections.indexOf(connection) >= getEffectiveMaxConnections()) return ConnectionStatus.CONNECTION_LIMIT;
        if (level == null || level.isOutsideBuildHeight(target)
                || !level.getWorldBorder().isWithinBounds(target)) return ConnectionStatus.UNAVAILABLE;
        if (distanceTo(target) > getEffectiveRange()) return ConnectionStatus.OUT_OF_RANGE;
        if (!level.hasChunkAt(target)) return ConnectionStatus.UNLOADED;
        if (level.getBlockState(target).isAir()) return ConnectionStatus.UNAVAILABLE;
        if (level.getBlockState(target).getBlock() instanceof QuantumPylonBlock) return ConnectionStatus.PYLON_NETWORK;
        boolean hasInput = false;
        boolean allKnownFull = true;
        boolean accepts = false;
        for (Direction face : Direction.values()) {
            IEnergyStorage input = EnergyHelper.getEnergyCapability(level, target, face);
            if (input == null || !input.canReceive()) continue;
            hasInput = true;
            int capacity = input.getMaxEnergyStored();
            if (capacity <= 0 || input.getEnergyStored() < capacity) allKnownFull = false;
            // Simulation diagnoses current acceptance without consuming FE.
            if (input.receiveEnergy(getCycleTransferLimit(), true) > 0) accepts = true;
        }
        if (!hasInput) return ConnectionStatus.INPUT_UNAVAILABLE;
        if (!accepts) return allKnownFull ? ConnectionStatus.FULL : ConnectionStatus.NOT_ACCEPTING;
        if (connection.getLastTransferred() > 0) return ConnectionStatus.TRANSFERRING;
        return availableEnergy == 0 ? ConnectionStatus.NO_POWER : ConnectionStatus.WAITING;
    }

    private void queueSync() {
        if (level != null && !level.isClientSide) syncQueued = true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putInt("MaxEnergy", energyStorage.getMaxEnergyStored());
        tag.put("Upgrades", upgrades.serializeNBT(registries));
        tag.putInt("BeamColor", beamColor);
        tag.putString("BeamStyle", beamStyle.name());
        tag.putFloat("GlowIntensity", glowIntensity);
        tag.putBoolean("BeamsVisible", beamsVisible);
        tag.putFloat("PulseSpeed", pulseSpeed);
        tag.putString("PriorityMode", priorityMode.name());
        tag.putString("RedstoneMode", redstoneMode.name());
        tag.putDouble("PeakThroughput", peakThroughput);
        tag.putInt("RotatingIndex", rotatingIndex);
        if (networkId != null) {
            tag.putUUID("NetworkId", networkId);
        }

        ListTag connList = new ListTag();
        for (ConnectionData conn : connections) {
            connList.add(conn.save());
        }
        tag.put("Connections", connList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        int storedEnergy = tag.getInt("Energy");
        upgrades.deserializeNBT(registries, tag.getCompound("Upgrades"));
        this.energyStorage = createEnergyStorage(getEffectiveBufferSize(), storedEnergy);

        this.beamColor = tag.contains("BeamColor")
                ? tag.getInt("BeamColor") & 0xFFFFFF
                : QFConfig.DEFAULT_BEAM_COLOR.get() & 0xFFFFFF;
        this.beamStyle = safeEnum(BeamStyle.class, tag.getString("BeamStyle"), configuredDefaultBeamStyle());
        this.glowIntensity = finiteClamped(tag.contains("GlowIntensity")
                ? tag.getFloat("GlowIntensity") : 1.0f, 0.2f, 2.0f, 1.0f);
        this.beamsVisible = !tag.contains("BeamsVisible") || tag.getBoolean("BeamsVisible");
        this.pulseSpeed = finiteClamped(tag.contains("PulseSpeed")
                ? tag.getFloat("PulseSpeed") : 1.0f, 0.1f, 4.0f, 1.0f);
        this.priorityMode = safeEnum(PriorityMode.class, tag.getString("PriorityMode"), configuredDefaultPriority());
        this.redstoneMode = safeEnum(RedstoneMode.class, tag.getString("RedstoneMode"), RedstoneMode.IGNORE);
        // Numeric NBT conversion also reads the prototype's integer peak.
        double savedPeak = tag.getDouble("PeakThroughput");
        this.peakThroughput = Double.isFinite(savedPeak) ? Math.clamp(savedPeak, 0, Integer.MAX_VALUE) : 0;
        this.rotatingIndex = Math.max(0, tag.getInt("RotatingIndex"));
        // Throughput describes this live sampling window, not saved activity.
        this.totalThroughput = 0;
        this.accumulatedThroughput = 0;
        this.networkId = tag.hasUUID("NetworkId") ? tag.getUUID("NetworkId") : null;
        if (new UUID(0L, 0L).equals(this.networkId)) this.networkId = null;

        connections.clear();
        ListTag connList = tag.getList("Connections", Tag.TAG_COMPOUND);
        Set<BlockPos> seenPositions = new HashSet<>();
        int maximumConnections = PylonSyncPayload.MAX_SYNCED_CONNECTIONS;
        for (int i = 0; i < connList.size() && connections.size() < maximumConnections; i++) {
            ConnectionData connection = ConnectionData.load(connList.getCompound(i));
            if (connection.getPos().equals(getBlockPos())
                    || connection.getPos().equals(getBlockPos().above())
                    || !seenPositions.add(connection.getPos())) {
                continue;
            }
            connections.add(connection);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        // Vanilla chunk sync only needs the tint source. Rich structure and
        // telemetry use bounded custom payloads after the chunk is known client-side.
        tag.putInt("BeamColor", beamColor);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ══════════════════════════════════════════
    //  Accessors
    // ══════════════════════════════════════════

    public PylonEnergyStorage getEnergyStorage() { return energyStorage; }
    public List<ConnectionData> getConnections() { return connections; }
    public BeamStyle getBeamStyle() { return beamStyle; }
    public void setBeamStyle(BeamStyle style) {
        if (style == null || this.beamStyle == style) return;
        this.beamStyle = style;
        setChanged();
        queueSync();
    }
    public int getBeamColor() { return beamColor; }
    public void setBeamColor(int color) {
        color &= 0xFFFFFF;
        if (this.beamColor == color) return;
        this.beamColor = color;
        setChanged();
        queueSync();
    }
    public float getGlowIntensity() { return glowIntensity; }
    public void setGlowIntensity(float intensity) {
        if (!Float.isFinite(intensity)) return;
        float clamped = Math.clamp(intensity, 0.2f, 2.0f);
        if (Float.compare(this.glowIntensity, clamped) == 0) return;
        this.glowIntensity = clamped;
        setChanged();
        queueSync();
    }
    public boolean isBeamsVisible() { return beamsVisible; }
    public void setBeamsVisible(boolean visible) {
        if (this.beamsVisible == visible) return;
        this.beamsVisible = visible;
        setChanged();
        queueSync();
    }
    public float getPulseSpeed() { return pulseSpeed; }
    public void setPulseSpeed(float speed) {
        if (!Float.isFinite(speed)) return;
        float clamped = Math.clamp(speed, 0.1f, 4.0f);
        if (Float.compare(this.pulseSpeed, clamped) == 0) return;
        this.pulseSpeed = clamped;
        setChanged();
        queueSync();
    }
    public PriorityMode getPriorityMode() { return priorityMode; }
    public void setPriorityMode(PriorityMode mode) {
        if (mode == null || this.priorityMode == mode) return;
        this.priorityMode = mode;
        setChanged();
        queueSync();
    }
    public double getTotalThroughput() { return totalThroughput; }
    public double getPeakThroughput() { return peakThroughput; }

    @Nullable public UUID getNetworkId() { return networkId; }

    public void setNetworkId(@Nullable UUID networkId) {
        if (Objects.equals(this.networkId, networkId)) return;
        this.networkId = networkId;
        setChanged();
        queueSync();
    }

    public int getEffectiveRange() {
        return UpgradeType.RANGE.effectiveValue(QFConfig.DEFAULT_RANGE.get(), upgrades.level(UpgradeType.RANGE));
    }

    public RedstoneMode getRedstoneMode() { return redstoneMode; }

    public void setRedstoneMode(RedstoneMode mode) {
        if (mode == null || mode == redstoneMode) return;
        redstoneMode = mode;
        setChanged();
        queueSync();
    }

    public boolean isOutputEnabled() {
        if (redstoneMode == RedstoneMode.IGNORE) return true;
        boolean signal = level != null && (level.hasNeighborSignal(getBlockPos())
                || level.hasNeighborSignal(getBlockPos().above()));
        return redstoneMode.allows(signal);
    }

    public int getEffectiveMaxConnections() {
        return UpgradeType.CAPACITY.effectiveValue(QFConfig.MAX_CONNECTIONS.get(), upgrades.level(UpgradeType.CAPACITY));
    }

    public int getEffectiveBufferSize() {
        return UpgradeType.BUFFER.effectiveValue(QFConfig.PYLON_BUFFER_SIZE.get(), upgrades.level(UpgradeType.BUFFER));
    }

    public int getBaseBufferSize() { return QFConfig.PYLON_BUFFER_SIZE.get(); }
    public PylonUpgradeInventory getUpgrades() { return upgrades; }
    public boolean canRemoveBufferUpgrades() {
        return energyStorage == null || energyStorage.getEnergyStored() <= getBaseBufferSize();
    }

    public void onUpgradesChanged() {
        if (energyStorage == null) return;
        energyStorage.setNominalCapacity(getEffectiveBufferSize());
        setChanged();
        queueSync();
        if (level != null && !level.isClientSide) level.updateNeighbourForOutputSignal(getBlockPos(), getBlockState().getBlock());
    }

    public int getAnalogOutputSignal() {
        return getAnalogOutputSignal(energyStorage.getEnergyStored());
    }

    // ══════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════

    private double distanceTo(BlockPos target) {
        return Math.sqrt(getBlockPos().distSqr(target));
    }

    private PylonEnergyStorage createEnergyStorage(int capacity, int energy) {
        return new PylonEnergyStorage(capacity, energy, this::onEnergyChanged);
    }

    private void onEnergyChanged(int previousEnergy) {
        setChanged();
        if (level == null || level.isClientSide) return;

        if (getAnalogOutputSignal(previousEnergy) != getAnalogOutputSignal()) {
            level.updateNeighbourForOutputSignal(getBlockPos(), getBlockState().getBlock());
        }
    }

    private int getAnalogOutputSignal(int energy) {
        int capacity = energyStorage.getMaxEnergyStored();
        if (capacity <= 0) return 0;
        return (int) ((long) Math.max(0, Math.min(energy, capacity)) * 15 / capacity);
    }

    private String getBlockDisplayName(BlockPos target) {
        if (level == null) return "screen.quantumflux.pylons.unknown_machine";
        BlockState state = level.getBlockState(target);
        return state.getBlock().getDescriptionId();
    }

    private void reconcileNetworkMembership(QuantumFluxNetworkManager manager) {
        QFNetwork indexedNetwork = manager.getNetworkForPylon(getBlockPos());
        UUID indexedId = indexedNetwork == null ? null : indexedNetwork.getUuid();
        if (!Objects.equals(networkId, indexedId)) setNetworkId(indexedId);
        if (indexedNetwork == null) return;

        if (!indexedNetwork.isPresentationInitialized()) {
            manager.initializeLegacyPresentation(indexedId, beamStyle, beamsVisible);
        }
        setBeamColor(indexedNetwork.getColor());
        setBeamStyle(indexedNetwork.getBeamStyle());
        setBeamsVisible(indexedNetwork.isBeamsVisible());
    }

    private static float finiteClamped(float value, float minimum, float maximum, float fallback) {
        return Float.isFinite(value) ? Math.clamp(value, minimum, maximum) : fallback;
    }

    private static <E extends Enum<E>> E safeEnum(Class<E> clazz, String name, E fallback) {
        try {
            return Enum.valueOf(clazz, name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
    }

    private static PriorityMode configuredDefaultPriority() {
        return safeEnum(PriorityMode.class, QFConfig.DEFAULT_PRIORITY_MODE.get(), PriorityMode.EQUAL);
    }

    private static BeamStyle configuredDefaultBeamStyle() {
        return safeEnum(BeamStyle.class, QFConfig.DEFAULT_BEAM_STYLE.get(), BeamStyle.SOLID);
    }
}
