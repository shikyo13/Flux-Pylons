package com.zerotheabsolute.quantumflux.gametest;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import com.zerotheabsolute.quantumflux.QuantumFlux;
import com.zerotheabsolute.quantumflux.QFConfig;
import com.zerotheabsolute.quantumflux.block.QuantumPylonBlock;
import com.zerotheabsolute.quantumflux.blockentity.QuantumPylonBlockEntity;
import com.zerotheabsolute.quantumflux.init.QFBlocks;
import com.zerotheabsolute.quantumflux.init.QFDataComponents;
import com.zerotheabsolute.quantumflux.init.QFItems;
import com.zerotheabsolute.quantumflux.network.data.QFNetwork;
import com.zerotheabsolute.quantumflux.network.data.QuantumFluxNetworkManager;
import com.zerotheabsolute.quantumflux.util.EnergyHelper;
import com.zerotheabsolute.quantumflux.util.BeamStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.UUID;

public final class QuantumFluxGameTests {

    private static final String EMPTY_TEMPLATE = "quantumflux:empty";
    private static final BlockPos PYLON_A = new BlockPos(2, 1, 2);
    private static final BlockPos PYLON_B = new BlockPos(5, 1, 2);
    private static final BlockPos PYLON_C = new BlockPos(8, 1, 2);

    public QuantumFluxGameTests() {}

    @GameTest(template = EMPTY_TEMPLATE)
    public static void fabricTransactionsCommitAndRollback(GameTestHelper helper) {
        var pylon = placePylon(helper, PYLON_A);
        var level = helper.getLevel();
        var storage = team.reborn.energy.api.EnergyStorage.SIDED.find(level, pylon.getBlockPos(), Direction.UP);
        helper.assertTrue(storage != null, "Fabric sided energy lookup");
        var chunk = level.getChunkAt(pylon.getBlockPos());
        chunk.setUnsaved(false);
        try (var outer = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            helper.assertValueEqual(storage.insert(1000, outer), 1000L, "Provisional input");
            try (var nested = outer.openNested()) {
                storage.insert(2000, nested);
                nested.commit();
            }
            helper.assertValueEqual(storage.getAmount(), 3000L, "Nested provisional input");
        }
        helper.assertValueEqual(storage.getAmount(), 0L, "Outer abort restores nested commits");
        helper.assertTrue(!chunk.isUnsaved(), "Aborted input must not dirty the chunk");
        try (var outer = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            storage.insert(12345, outer);
            try (var nested = outer.openNested()) { storage.insert(2000, nested); }
            helper.assertValueEqual(storage.getAmount(), 12345L, "Nested abort preserves outer input");
            helper.assertValueEqual(storage.extract(10, outer), 0L, "External extraction stays disabled");
            outer.commit();
        }
        helper.assertTrue(chunk.isUnsaved(), "Committed transaction persists input");
        helper.assertValueEqual(pylon.saveWithoutMetadata(level.registryAccess()).getInt("Energy"), 12345,
                "Committed transaction NBT");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void redstoneAtEitherHalfPausesAndResumesWithoutLosingEnergy(GameTestHelper helper) {
        var pylon = placePylon(helper, PYLON_A);
        var receiver = EnergyReceiverFixture.place(helper, PYLON_A.offset(3, 0, 0));
        helper.assertTrue(pylon.tryLink(receiver.getBlockPos()), "Redstone receiver link");
        BlockPos pos = pylon.getBlockPos();
        var storage = pylon.getEnergyStorage();
        pylon.setRedstoneMode(com.zerotheabsolute.quantumflux.util.RedstoneMode.WHEN_POWERED);
        storage.receiveEnergy(1_000, false);
        QuantumPylonBlockEntity.serverTick(helper.getLevel(), pos, pylon.getBlockState(), pylon);
        helper.assertValueEqual(receiver.getEnergyStorage().getEnergyStored(), 0, "Required absent signal did not pause");
        helper.assertValueEqual(pylon.getConnectionStatus(receiver.getBlockPos()),
                com.zerotheabsolute.quantumflux.util.ConnectionStatus.PAUSED, "Receiver pause explanation");
        helper.assertValueEqual(storage.getEnergyStored(), 1_000, "Paused energy was lost");
        helper.setBlock(PYLON_A.west(), Blocks.REDSTONE_BLOCK);
        QuantumPylonBlockEntity.serverTick(helper.getLevel(), pos, pylon.getBlockState(), pylon);
        helper.assertValueEqual(receiver.getEnergyStorage().getEnergyStored(), 1_000, "Bottom signal did not resume");
        helper.setBlock(PYLON_A.west(), Blocks.AIR);
        storage.receiveEnergy(1_000, false);
        helper.setBlock(PYLON_A.above().west(), Blocks.REDSTONE_BLOCK);
        QuantumPylonBlockEntity.serverTick(helper.getLevel(), pos, pylon.getBlockState(), pylon);
        helper.assertValueEqual(receiver.getEnergyStorage().getEnergyStored(), 2_000, "Top signal did not resume");
        pylon.setRedstoneMode(com.zerotheabsolute.quantumflux.util.RedstoneMode.WHEN_UNPOWERED);
        storage.receiveEnergy(1_000, false);
        QuantumPylonBlockEntity.serverTick(helper.getLevel(), pos, pylon.getBlockState(), pylon);
        helper.assertValueEqual(receiver.getEnergyStorage().getEnergyStored(), 2_000, "Inverted signal did not pause");
        var saved = pylon.saveWithoutMetadata(helper.getLevel().registryAccess());
        pylon.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(pylon.getRedstoneMode(), com.zerotheabsolute.quantumflux.util.RedstoneMode.WHEN_UNPOWERED,
                "Redstone mode persistence");
        helper.assertValueEqual(pylon.getEnergyStorage().getEnergyStored(), 1_000, "Paused energy persistence");
        helper.setBlock(PYLON_A.above().west(), Blocks.AIR);
        QuantumPylonBlockEntity.serverTick(helper.getLevel(), pos, pylon.getBlockState(), pylon);
        helper.assertValueEqual(receiver.getEnergyStorage().getEnergyStored(), 3_000, "Removing the signal did not resume");
        saved.remove("RedstoneMode");
        pylon.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(pylon.getRedstoneMode(), com.zerotheabsolute.quantumflux.util.RedstoneMode.IGNORE,
                "Prototype saves must keep always-enabled output");
        helper.setBlock(PYLON_A.west(), Blocks.REDSTONE_BLOCK);
        QuantumPylonBlockEntity.serverTick(helper.getLevel(), pos, pylon.getBlockState(), pylon);
        helper.assertValueEqual(receiver.getEnergyStorage().getEnergyStored(), 4_000, "Ignored redstone blocked output");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void pausedNetworkOutputKeepsStorageAvailableToOtherPylons(GameTestHelper helper) {
        var paused = placePylon(helper, PYLON_A);
        var active = placePylon(helper, PYLON_B);
        var pausedReceiver = EnergyReceiverFixture.place(helper, PYLON_A.south(3));
        var activeReceiver = EnergyReceiverFixture.place(helper, PYLON_B.south(3));
        helper.assertTrue(paused.tryLink(pausedReceiver.getBlockPos()) && active.tryLink(activeReceiver.getBlockPos()),
                "Network redstone receiver links");
        var manager = QuantumFluxNetworkManager.get(helper.getLevel());
        var network = manager.createNetwork(UUID.randomUUID(), "Redstone pool", 0x00CFE8);
        helper.assertTrue(network != null, "Network redstone fixture");
        for (var pylon : java.util.List.of(paused, active)) {
            manager.addPylon(network.getUuid(), pylon.getBlockPos());
            pylon.setNetworkId(network.getUuid());
        }
        paused.setRedstoneMode(com.zerotheabsolute.quantumflux.util.RedstoneMode.WHEN_POWERED);
        helper.assertValueEqual(paused.getEnergyStorage().receiveEnergy(1_000, false), 1_000, "Paused pylon refused E input");
        QuantumPylonBlockEntity.serverTick(helper.getLevel(), paused.getBlockPos(), paused.getBlockState(), paused);
        QuantumPylonBlockEntity.serverTick(helper.getLevel(), active.getBlockPos(), active.getBlockState(), active);
        helper.assertValueEqual(pausedReceiver.getEnergyStorage().getEnergyStored(), 0, "Paused network pylon delivered E");
        helper.assertValueEqual(activeReceiver.getEnergyStorage().getEnergyStored(), 1_000, "Paused buffer blocked another pylon");
        helper.assertValueEqual(paused.getEnergyStorage().getEnergyStored() + active.getEnergyStorage().getEnergyStored(), 0,
                "Network redstone delivery duplicated E");
        var snapshot = com.zerotheabsolute.quantumflux.network.NetworkTelemetryPayload.capture(helper.getLevel(), network);
        helper.assertTrue(!(snapshot.pylons().stream().filter(row -> row.pos().equals(paused.getBlockPos())).findFirst().orElseThrow().outputEnabled()),
                "Remote snapshot hid a pylon's paused output");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void capabilityReceiveMarksChunkAndSerializes(GameTestHelper helper) {
        QuantumPylonBlockEntity pylon = placePylon(helper, PYLON_A);
        ServerLevel level = helper.getLevel();
        BlockPos absolutePos = helper.absolutePos(PYLON_A);
        com.zerotheabsolute.quantumflux.energy.EnergyReceiver capability = EnergyHelper.getEnergyCapability(level, absolutePos, Direction.UP);

        helper.assertTrue(capability != null, "Pylon energy capability was not exposed");
        helper.assertTrue(capability.canReceive(), "Pylon capability must accept external E");
        helper.assertTrue(!team.reborn.energy.api.EnergyStorage.SIDED.find(level, absolutePos, Direction.UP).supportsExtraction(), "Pylons must remain receive-only");

        level.getChunkAt(absolutePos).setUnsaved(false);
        int simulated = capability.receiveEnergy(12_345, true);
        helper.assertValueEqual(simulated, 12_345, "Simulated receive amount");
        helper.assertValueEqual(capability.getEnergyStored(), 0, "Simulation must not mutate energy");
        helper.assertTrue(!(level.getChunkAt(absolutePos).isUnsaved()),
                "Simulation must not dirty the containing chunk");

        int accepted = capability.receiveEnergy(12_345, false);
        helper.assertValueEqual(accepted, 12_345, "Committed receive amount");
        helper.assertValueEqual(capability.getEnergyStored(), 12_345, "Stored E after receive");
        helper.assertTrue(level.getChunkAt(absolutePos).isUnsaved(),
                "Committed receive must dirty the containing chunk");

        CompoundTag serialized = pylon.saveWithoutMetadata(level.registryAccess());
        helper.assertValueEqual(serialized.getInt("Energy"), 12_345,
                "Committed receive must be present in serialized block-entity state");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void nbtRoundTripRetainsEnergyAndConfiguredCapacity(GameTestHelper helper) {
        QuantumPylonBlockEntity original = placePylon(helper, PYLON_A);
        int configuredCapacity = original.getEnergyStorage().getMaxEnergyStored();
        int expectedEnergy = Math.max(1, configuredCapacity * 3 / 5);
        original.getEnergyStorage().setEnergy(expectedEnergy);

        CompoundTag serialized = original.saveWithoutMetadata(helper.getLevel().registryAccess());
        QuantumPylonBlockEntity loaded = new QuantumPylonBlockEntity(
                BlockPos.ZERO,
                pylonBottomState()
        );
        loaded.loadWithComponents(serialized, helper.getLevel().registryAccess());

        helper.assertValueEqual(loaded.getEnergyStorage().getEnergyStored(), expectedEnergy,
                "NBT round-trip stored E");
        helper.assertValueEqual(loaded.getEnergyStorage().getMaxEnergyStored(), configuredCapacity,
                "NBT round-trip configured capacity");
        helper.assertValueEqual(serialized.getInt("MaxEnergy"), configuredCapacity,
                "Serialized diagnostic capacity");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void chunkUnloadReloadRetainsEnergyAndNetworkMembership(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos remotePos = helper.absolutePos(PYLON_A).offset(1024, 0, 1024);
        int chunkX = remotePos.getX() >> 4;
        int chunkZ = remotePos.getZ() >> 4;
        level.setChunkForced(chunkX, chunkZ, true);
        level.getChunkAt(remotePos);
        level.setBlock(remotePos, pylonBottomState(), 3);
        QFBlocks.QUANTUM_PYLON.get().setPlacedBy(
                level, remotePos, level.getBlockState(remotePos), null, ItemStack.EMPTY);
        helper.assertTrue(level.getBlockEntity(remotePos) instanceof QuantumPylonBlockEntity,
                "Remote pylon block entity was not created");
        QuantumPylonBlockEntity pylon = (QuantumPylonBlockEntity) level.getBlockEntity(remotePos);

        int expectedEnergy = Math.min(54_321, pylon.getEnergyStorage().getMaxEnergyStored());
        pylon.getEnergyStorage().setEnergy(expectedEnergy);
        QuantumFluxNetworkManager manager = QuantumFluxNetworkManager.get(level);
        UUID owner = UUID.randomUUID();
        QFNetwork network = manager.createNetwork(owner, "Chunk Persistence", 0x00CFE8);
        helper.assertTrue(network != null && manager.addPylon(network.getUuid(), remotePos),
                "Remote pylon network fixture was not created");
        pylon.setNetworkId(network.getUuid());
        UUID expectedNetwork = network.getUuid();

        level.getChunkSource().save(false);
        level.setChunkForced(chunkX, chunkZ, false);
        boolean[] observedUnload = {false};
        helper.succeedWhen(() -> {
            if (!observedUnload[0]) {
                helper.assertTrue(!(level.hasChunkAt(remotePos)),
                        "Remote chunk did not unload after its force ticket was removed");
                observedUnload[0] = true;
                level.getChunkAt(remotePos);
            }

            helper.assertTrue(level.getBlockEntity(remotePos) instanceof QuantumPylonBlockEntity,
                    "Pylon block entity was absent after actual chunk reload");
            QuantumPylonBlockEntity reloaded =
                    (QuantumPylonBlockEntity) level.getBlockEntity(remotePos);
            helper.assertValueEqual(reloaded.getEnergyStorage().getEnergyStored(), expectedEnergy,
                    "Energy after actual chunk reload");
            helper.assertValueEqual(reloaded.getNetworkId(), expectedNetwork,
                    "Block-entity network identity after actual chunk reload");
            QFNetwork reloadedNetwork = manager.getNetworkForPylon(remotePos);
            helper.assertTrue(reloadedNetwork != null,
                    "SavedData network membership disappeared after actual chunk reload");
            helper.assertValueEqual(reloadedNetwork.getUuid(), expectedNetwork,
                    "SavedData network identity after actual chunk reload");
        });
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void networkManagersAreIsolatedAcrossDimensions(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "GameTest server did not create the Nether level");

        QuantumFluxNetworkManager overworldManager = QuantumFluxNetworkManager.get(overworld);
        QuantumFluxNetworkManager netherManager = QuantumFluxNetworkManager.get(nether);
        UUID owner = UUID.randomUUID();
        QFNetwork overworldNetwork = overworldManager.createNetwork(
                owner, "Overworld Isolation", 0x00CFE8);
        QFNetwork netherNetwork = netherManager.createNetwork(owner, "Nether Isolation", 0xFF9F43);
        helper.assertTrue(overworldNetwork != null && netherNetwork != null,
                "Dimension isolation fixtures were not created");
        helper.assertTrue(netherManager.getNetwork(overworldNetwork.getUuid()) == null,
                "Overworld network leaked into Nether SavedData");
        helper.assertTrue(overworldManager.getNetwork(netherNetwork.getUuid()) == null,
                "Nether network leaked into Overworld SavedData");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void legacyNetworkOverflowIsPreservedAndPrivatized(GameTestHelper helper) {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("nextNumericId", QuantumFluxNetworkManager.MAX_NETWORKS_PER_DIMENSION + 3);
        ListTag networks = new ListTag();
        UUID owner = UUID.randomUUID();
        int total = QuantumFluxNetworkManager.MAX_NETWORKS_PER_DIMENSION + 2;
        for (int numericId = 1; numericId <= total; numericId++) {
            QFNetwork network = new QFNetwork(
                    UUID.randomUUID(), numericId, "Legacy " + numericId, 0x00CFE8, owner);
            network.setAccessMode(QFNetwork.AccessMode.PUBLIC);
            networks.add(network.save());
        }
        legacy.put("networks", networks);

        QuantumFluxNetworkManager migrated = QuantumFluxNetworkManager.load(
                legacy, helper.getLevel().registryAccess());
        helper.assertValueEqual(migrated.getAllNetworks().size(), total,
                "Legacy overflow network count");
        java.util.List<QFNetwork> ordered = migrated.getAllNetworks().stream()
                .sorted(java.util.Comparator.comparingInt(QFNetwork::getNumericId))
                .toList();
        helper.assertValueEqual(
                ordered.get(QuantumFluxNetworkManager.MAX_NETWORKS_PER_DIMENSION - 1).getAccessMode(),
                QFNetwork.AccessMode.PUBLIC,
                "Last in-cap legacy network access mode");
        helper.assertValueEqual(
                ordered.get(QuantumFluxNetworkManager.MAX_NETWORKS_PER_DIMENSION).getAccessMode(),
                QFNetwork.AccessMode.PRIVATE,
                "First overflow legacy network access mode");
        CompoundTag resaved = migrated.save(new CompoundTag(), helper.getLevel().registryAccess());
        helper.assertValueEqual(resaved.getInt("overflowMigrationVersion"), 1,
                "Overflow migration version");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void comparatorOutputTracksEnergyTransitions(GameTestHelper helper) {
        QuantumPylonBlockEntity pylon = placePylon(helper, PYLON_A);
        ServerLevel level = helper.getLevel();
        BlockPos absolutePos = helper.absolutePos(PYLON_A);
        BlockState state = level.getBlockState(absolutePos);
        int capacity = pylon.getEnergyStorage().getMaxEnergyStored();

        assertComparator(helper, state, level, absolutePos, 0);
        pylon.getEnergyStorage().setEnergy(capacity / 2);
        assertComparator(helper, state, level, absolutePos, 7);
        pylon.getEnergyStorage().setEnergy(capacity);
        assertComparator(helper, state, level, absolutePos, 15);
        pylon.getEnergyStorage().setEnergy(0);
        assertComparator(helper, state, level, absolutePos, 0);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void networkAuthorityAndPasswordRules(GameTestHelper helper) {
        QuantumFluxNetworkManager manager = QuantumFluxNetworkManager.get(helper.getLevel());
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();
        UUID sharedMember = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();

        QFNetwork first = manager.createNetwork(firstOwner, "Authority A", 0x00CFE8);
        QFNetwork second = manager.createNetwork(secondOwner, "Authority B", 0xA66BFF);
        helper.assertTrue(first != null && second != null, "Failed to create authority test networks");

        helper.assertTrue(!(manager.updateNetwork(first.getUuid(), sharedMember,
                "Unauthorized", null, null, null)), "Non-owner renamed a network");
        helper.assertTrue(manager.updateNetwork(first.getUuid(), firstOwner,
                null, null, QFNetwork.AccessMode.PUBLIC, null), "Owner could not make network public");
        helper.assertTrue(first.canAccess(stranger), "Public network denied use access");
        helper.assertTrue(!(first.canConfigure(stranger)), "Public access granted administration");

        helper.assertTrue(manager.addMember(first.getUuid(), firstOwner, sharedMember),
                "Owner could not add member to first network");
        helper.assertTrue(manager.addMember(second.getUuid(), secondOwner, sharedMember),
                "Owner could not add member to second network");

        BlockPos claimedPylon = helper.absolutePos(new BlockPos(10, 1, 10));
        helper.assertTrue(manager.addPylon(first.getUuid(), claimedPylon),
                "Failed to establish pylon ownership fixture");
        helper.assertValueEqual(manager.assignPylon(second.getUuid(), claimedPylon, secondOwner),
                QuantumFluxNetworkManager.PylonMutationResult.ASSIGNED_TO_OTHER_NETWORK,
                "Destination owner stole a pylon without current-network access");
        helper.assertValueEqual(manager.assignPylon(second.getUuid(), claimedPylon, firstOwner),
                QuantumFluxNetworkManager.PylonMutationResult.FORBIDDEN,
                "Current owner transferred a pylon without destination access");
        helper.assertValueEqual(manager.assignPylon(second.getUuid(), claimedPylon, sharedMember),
                QuantumFluxNetworkManager.PylonMutationResult.SUCCESS,
                "Member of both networks could not transfer a pylon");
        helper.assertValueEqual(manager.unassignPylon(claimedPylon, firstOwner),
                QuantumFluxNetworkManager.PylonMutationResult.FORBIDDEN,
                "Former owner unassigned a transferred pylon");
        helper.assertValueEqual(manager.unassignPylon(claimedPylon, secondOwner),
                QuantumFluxNetworkManager.PylonMutationResult.SUCCESS,
                "Current owner could not unassign a pylon");
        helper.assertTrue(manager.updatePresentation(second.getUuid(), sharedMember,
                BeamStyle.PULSE, false), "Authorized member could not update network presentation");
        helper.assertValueEqual(second.getBeamStyle(), BeamStyle.PULSE,
                "Network beam style was not retained");
        helper.assertTrue(!(second.isBeamsVisible()), "Network beam visibility was not retained");

        helper.assertTrue(manager.addPylon(first.getUuid(), claimedPylon),
                "Failed to restore deletion fixture pylon");
        helper.assertTrue(manager.deleteNetwork(first.getUuid(), firstOwner),
                "Owner could not delete a network containing pylons");
        helper.assertTrue(manager.getNetworkForPylon(claimedPylon) == null,
                "Deleting a network left its reverse pylon assignment behind");

        QFNetwork passwordNetwork = manager.createNetwork(firstOwner, "Password Network", 0xFF9F43);
        helper.assertTrue(passwordNetwork != null, "Failed to create password test network");
        String password = "game-test-secret";
        helper.assertTrue(manager.updateNetwork(passwordNetwork.getUuid(), firstOwner,
                null, null, QFNetwork.AccessMode.PASSWORD, password),
                "Owner could not configure password network");
        helper.assertTrue(!(manager.joinWithPassword(passwordNetwork.getUuid(), stranger,
                "wrong-secret")), "Wrong password joined network");
        helper.assertTrue(manager.joinWithPassword(passwordNetwork.getUuid(), stranger, password),
                "Correct password could not join network");
        helper.assertTrue(passwordNetwork.canConfigure(stranger),
                "Successful password join did not grant membership");

        CompoundTag savedNetworks = manager.save(new CompoundTag(), helper.getLevel().registryAccess());
        helper.assertTrue(!(savedNetworks.toString().contains(password)),
                "World data stored the plaintext password");
        QuantumFluxNetworkManager reloaded = QuantumFluxNetworkManager.load(
                savedNetworks, helper.getLevel().registryAccess());
        QFNetwork reloadedSecond = reloaded.getNetwork(second.getUuid());
        helper.assertTrue(reloadedSecond != null, "Presentation network missing after SavedData round-trip");
        helper.assertValueEqual(reloadedSecond.getBeamStyle(), BeamStyle.PULSE,
                "Beam style failed SavedData round-trip");
        helper.assertTrue(!(reloadedSecond.isBeamsVisible()),
                "Beam visibility failed SavedData round-trip");
        UUID reloadedJoiner = UUID.randomUUID();
        helper.assertTrue(reloaded.joinWithPassword(passwordNetwork.getUuid(), reloadedJoiner, password),
                "Salted password verifier failed after SavedData round-trip");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 40)
    public static void threePylonBalanceConservesRemainder(GameTestHelper helper) {
        QuantumPylonBlockEntity first = placePylon(helper, PYLON_A);
        QuantumPylonBlockEntity second = placePylon(helper, PYLON_B);
        QuantumPylonBlockEntity third = placePylon(helper, PYLON_C);
        ServerLevel level = helper.getLevel();

        QuantumFluxNetworkManager manager = QuantumFluxNetworkManager.get(level);
        UUID owner = UUID.randomUUID();
        QFNetwork network = manager.createNetwork(owner, "GameTest Balance", 0x7C4DFF);
        helper.assertTrue(network != null, "Failed to create balance test network");

        assignPylon(helper, manager, network, first, PYLON_A);
        assignPylon(helper, manager, network, second, PYLON_B);
        assignPylon(helper, manager, network, third, PYLON_C);
        first.getEnergyStorage().setEnergy(2);

        helper.succeedWhen(() -> {
            int firstEnergy = first.getEnergyStorage().getEnergyStored();
            int secondEnergy = second.getEnergyStorage().getEnergyStored();
            int thirdEnergy = third.getEnergyStorage().getEnergyStored();
            helper.assertValueEqual(firstEnergy, 1, "First pylon balanced E");
            helper.assertValueEqual(secondEnergy, 1, "Second pylon balanced E");
            helper.assertValueEqual(thirdEnergy, 0, "Third pylon balanced E");
            helper.assertValueEqual(firstEnergy + secondEnergy + thirdEnergy, 2,
                    "Balancing must conserve every E unit");
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void wirelessTransferUsesRealCapabilityAndConservesEnergy(GameTestHelper helper) {
        QuantumPylonBlockEntity source = placePylon(helper, PYLON_A);
        EnergyReceiverFixture target = EnergyReceiverFixture.place(helper, PYLON_B);
        BlockPos absoluteTarget = helper.absolutePos(PYLON_B);
        int initialEnergy = Math.min(source.getEnergyStorage().getMaxEnergyStored(), 25_001);

        helper.assertTrue(source.tryLink(absoluteTarget),
                "Source pylon could not link to the receiver's registered E capability");
        source.getEnergyStorage().setEnergy(initialEnergy);

        helper.succeedWhen(() -> {
            int sourceEnergy = source.getEnergyStorage().getEnergyStored();
            int targetEnergy = target.getEnergyStorage().getEnergyStored();
            helper.assertTrue(targetEnergy > 0,
                    "Wireless distribution never delivered E through the target capability");
            helper.assertValueEqual(sourceEnergy + targetEnergy, initialEnergy,
                    "Wireless transfer created or destroyed E");
        });
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void breakingBottomDropsExactlyOnePylon(GameTestHelper helper) {
        placePylon(helper, PYLON_A);
        breakPylonHalfAsSurvivalPlayer(helper, PYLON_A);
        assertSinglePylonDropAndBothHalvesGone(helper);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60)
    public static void equalDistributionSharesSingleFeAndRemainders(GameTestHelper helper) {
        QuantumPylonBlockEntity source = placePylon(helper, PYLON_A);
        EnergyReceiverFixture first = EnergyReceiverFixture.place(helper, PYLON_B);
        EnergyReceiverFixture second = EnergyReceiverFixture.place(helper, PYLON_C);
        EnergyReceiverFixture third = EnergyReceiverFixture.place(helper, new BlockPos(5, 1, 5));
        for (EnergyReceiverFixture target : new EnergyReceiverFixture[]{first, second, third}) {
            helper.assertTrue(source.tryLink(target.getBlockPos()), "Could not link fairness fixture");
        }
        helper.succeedWhen(() -> {
            helper.assertValueEqual(helper.getLevel().getGameTime() % QFConfig.TICK_INTERVAL.get(),
                    0L, "Wait for a configured distribution tick");
            // Execute real distribution ticks without replenishing the source
            // until its preceding input has been accounted for.
            int supplied = 0;
            for (int amount : new int[]{1, 1, 1, 5, 5, 5}) {
                source.getEnergyStorage().receiveEnergy(amount, false);
                supplied += amount;
                tickPylon(helper, source);
                int a = first.getEnergyStorage().getEnergyStored();
                int b = second.getEnergyStorage().getEnergyStored();
                int c = third.getEnergyStorage().getEnergyStored();
                helper.assertValueEqual(a + b + c, supplied, "Every supplied E reaches a target");
                helper.assertTrue(Math.max(a, Math.max(b, c)) - Math.min(a, Math.min(b, c)) <= 1,
                        "Equal distribution must not favor the first links under scarce power");
            }
            helper.assertValueEqual(first.getEnergyStorage().getEnergyStored(), 6, "First consumer share");
            helper.assertValueEqual(second.getEnergyStorage().getEnergyStored(), 6, "Second consumer share");
            helper.assertValueEqual(third.getEnergyStorage().getEnergyStored(), 6, "Third consumer share");
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60)
    public static void equalDistributionRedistributesFullMachineShare(GameTestHelper helper) {
        QuantumPylonBlockEntity source = placePylon(helper, PYLON_A);
        EnergyReceiverFixture full = EnergyReceiverFixture.place(helper, PYLON_B);
        EnergyReceiverFixture accepting = EnergyReceiverFixture.place(helper, PYLON_C);
        int capacity = full.getEnergyStorage().getMaxEnergyStored();
        full.getEnergyStorage().setEnergy(capacity - 1);
        helper.assertTrue(source.tryLink(full.getBlockPos()), "Could not link near-full target");
        helper.assertTrue(source.tryLink(accepting.getBlockPos()), "Could not link accepting target");
        source.getEnergyStorage().receiveEnergy(101, false);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(source.getEnergyStorage().getEnergyStored(), 0, "Source must use its budget");
            helper.assertValueEqual(full.getEnergyStorage().getEnergyStored(), capacity, "Near-full receiver gets one E");
            helper.assertTrue(source.getConnectionStatus(full.getBlockPos())
                            == com.zerotheabsolute.quantumflux.util.ConnectionStatus.FULL,
                    "A full exposed input must have an actionable full status");
            helper.assertValueEqual(accepting.getEnergyStorage().getEnergyStored(), 100, "Unused share reaches accepting receiver");
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void temporarilyUnavailableInputRetainsLinkAndResumes(GameTestHelper helper) {
        QuantumPylonBlockEntity source = placePylon(helper, PYLON_A);
        EnergyReceiverFixture target = EnergyReceiverFixture.place(helper, PYLON_B);
        BlockPos targetPos = target.getBlockPos();
        helper.assertTrue(source.tryLink(targetPos), "Could not link input fixture");
        // Model a multiblock's E capability disappearing while the block
        // remains occupied. The normal 20-tick validation must keep its link.
        helper.setBlock(PYLON_B, Blocks.IRON_BLOCK);
        source.getEnergyStorage().receiveEnergy(73, false);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(source.isLinkedTo(targetPos), "A temporarily absent input deleted the link");
            helper.assertValueEqual(source.getEnergyStorage().getEnergyStored(), 73,
                    "Unavailable input must not consume source E");
            EnergyReceiverFixture restored = EnergyReceiverFixture.place(helper, PYLON_B);
            helper.succeedWhen(() -> {
                helper.assertValueEqual(restored.getEnergyStorage().getEnergyStored(), 73,
                        "Delivery did not resume through the restored real E capability");
                helper.assertValueEqual(source.getEnergyStorage().getEnergyStored(), 0,
                        "Resumed delivery must conserve E");
            });
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void internalEnergyAccessCannotBypassInputSides(GameTestHelper helper) {
        QuantumPylonBlockEntity source = placePylon(helper, PYLON_A);
        EnergyReceiverFixture target = EnergyReceiverFixture.place(helper, PYLON_B);
        helper.assertTrue(source.tryLink(target.getBlockPos()), "Input must initially link");
        target.getEnergyStorage().externalInputEnabled = false;
        helper.assertTrue(!com.zerotheabsolute.quantumflux.util.EnergyHelper.blockAcceptsEnergy(
                helper.getLevel(), target.getBlockPos()), "Internal-only capability is not a wireless input");
        source.getEnergyStorage().receiveEnergy(73, false);
        helper.runAfterDelay(25, () -> {
            helper.assertValueEqual(source.getEnergyStorage().getEnergyStored(), 73,
                    "Internal access must not bypass disabled faces");
            helper.assertTrue(source.getConnectionStatus(target.getBlockPos())
                            == com.zerotheabsolute.quantumflux.util.ConnectionStatus.INPUT_UNAVAILABLE,
                    "Disabled external faces must be reported as unavailable input");
            helper.assertTrue(source.isLinkedTo(target.getBlockPos()), "Side settings must retain links");
            target.getEnergyStorage().externalInputEnabled = true;
            target.getEnergyStorage().acceptingEnergy = false;
            helper.assertTrue(source.getConnectionStatus(target.getBlockPos())
                            == com.zerotheabsolute.quantumflux.util.ConnectionStatus.NOT_ACCEPTING,
                    "An empty rejecting machine must not be described as full");
            target.getEnergyStorage().acceptingEnergy = true;
            helper.succeedWhen(() -> helper.assertValueEqual(target.getEnergyStorage().getEnergyStored(), 73,
                    "The enabled external input must receive retained energy"));
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void emptyBufferDeliveryLightsBothPylonHalves(GameTestHelper helper) {
        QuantumPylonBlockEntity source = placePylon(helper, PYLON_A);
        EnergyReceiverFixture target = EnergyReceiverFixture.place(helper, PYLON_B);
        helper.assertTrue(source.tryLink(target.getBlockPos()), "Could not link activity fixture");
        source.getEnergyStorage().receiveEnergy(1, false);
        helper.runAfterDelay(20, () -> {
            helper.assertValueEqual(source.getEnergyStorage().getEnergyStored(), 0, "Delivery drains source buffer");
            helper.assertValueEqual(target.getEnergyStorage().getEnergyStored(), 1, "Delivered E");
            helper.assertTrue(source.getTotalThroughput() == 0.05,
                    "One E over 20 ticks must report its actual 0.05 E/t rate");
            helper.assertTrue(source.getConnections().getFirst().getLastTransferred() == 0.05,
                    "The machine reading must agree with the pylon");
            for (BlockPos pos : new BlockPos[]{source.getBlockPos(), source.getBlockPos().above()}) {
                helper.assertTrue(helper.getLevel().getBlockState(pos).getValue(QuantumPylonBlock.ACTIVE),
                        "An empty buffer must not switch off a pylon that is delivering power");
            }
            helper.runAfterDelay(45, () -> {
                helper.assertTrue(source.getTotalThroughput() == 0, "Expired output must return to zero");
                helper.assertTrue(!(source.getBlockState().getValue(QuantumPylonBlock.ACTIVE)),
                        "A drained idle pylon must eventually turn off");
                helper.succeed();
            });
        });
    }

    private static void tickPylon(GameTestHelper helper, QuantumPylonBlockEntity pylon) {
        QuantumPylonBlockEntity.serverTick(helper.getLevel(), pylon.getBlockPos(), pylon.getBlockState(), pylon);
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60)
    public static void fullFirstReceiverDoesNotBlockOrBiasTrickle(GameTestHelper helper) {
        QuantumPylonBlockEntity source = placePylon(helper, PYLON_A);
        EnergyReceiverFixture full = EnergyReceiverFixture.place(helper, PYLON_B);
        EnergyReceiverFixture first = EnergyReceiverFixture.place(helper, PYLON_C);
        EnergyReceiverFixture second = EnergyReceiverFixture.place(helper, new BlockPos(5, 1, 5));
        full.getEnergyStorage().setEnergy(full.getEnergyStorage().getMaxEnergyStored());
        for (EnergyReceiverFixture target : new EnergyReceiverFixture[]{full, first, second}) {
            helper.assertTrue(source.tryLink(target.getBlockPos()), "Could not link blocked-trickle fixture");
        }
        helper.succeedWhen(() -> {
            helper.assertValueEqual(helper.getLevel().getGameTime() % QFConfig.TICK_INTERVAL.get(),
                    0L, "Wait for a configured distribution tick");
            for (int supplied = 1; supplied <= 12; supplied++) {
                source.getEnergyStorage().receiveEnergy(1, false);
                tickPylon(helper, source);
                int a = first.getEnergyStorage().getEnergyStored();
                int b = second.getEnergyStorage().getEnergyStored();
                helper.assertValueEqual(a + b, supplied, "Full first target must not block a single E");
                helper.assertTrue(Math.abs(a - b) <= 1, "Trickle must rotate among accepting targets");
            }
        });
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void breakingTopDropsExactlyOnePylon(GameTestHelper helper) {
        placePylon(helper, PYLON_A);
        breakPylonHalfAsSurvivalPlayer(helper, PYLON_A.above());
        assertSinglePylonDropAndBothHalvesGone(helper);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 160)
    public static void sharedPoolFeedsRemotePylonsFairly(GameTestHelper helper) {
        QuantumPylonBlockEntity input = placePylon(helper, PYLON_A);
        QuantumPylonBlockEntity firstRelay = placePylon(helper, PYLON_B);
        QuantumPylonBlockEntity secondRelay = placePylon(helper, PYLON_C);
        EnergyReceiverFixture first = EnergyReceiverFixture.place(helper, new BlockPos(5, 1, 5));
        EnergyReceiverFixture second = EnergyReceiverFixture.place(helper, new BlockPos(8, 1, 5));
        var manager = QuantumFluxNetworkManager.get(helper.getLevel());
        var network = manager.createNetwork(UUID.randomUUID(), "Remote Trickle", 0x00FFFF);
        helper.assertTrue(network != null, "Failed to create shared-pool fixture");
        assignPylon(helper, manager, network, input, PYLON_A);
        assignPylon(helper, manager, network, firstRelay, PYLON_B);
        assignPylon(helper, manager, network, secondRelay, PYLON_C);
        helper.assertTrue(firstRelay.tryLink(first.getBlockPos()), "First remote link");
        helper.assertTrue(secondRelay.tryLink(second.getBlockPos()), "Second remote link");

        var sequence = helper.startSequence();
        for (int round = 1; round <= 10; round++) {
            int expected = round;
            sequence.thenExecute(() -> input.getEnergyStorage().receiveEnergy(1, false))
                    .thenWaitUntil(() -> {
                        int a = first.getEnergyStorage().getEnergyStored();
                        int b = second.getEnergyStorage().getEnergyStored();
                        helper.assertValueEqual(a + b, expected, "Every input E must reach remote machines");
                        helper.assertTrue(Math.abs(a - b) <= 1, "Scarce E must rotate between remote pylons");
                        helper.assertValueEqual(input.getEnergyStorage().getEnergyStored()
                                + firstRelay.getEnergyStorage().getEnergyStored()
                                + secondRelay.getEnergyStorage().getEnergyStored(), 0,
                                "Usable E must not remain stranded in an input-only pylon");
                    });
        }
        sequence.thenSucceed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void sharedPoolSkipsFullPylonAndRunsOncePerCycle(GameTestHelper helper) {
        QuantumPylonBlockEntity input = placePylon(helper, PYLON_A);
        QuantumPylonBlockEntity blockedRelay = placePylon(helper, PYLON_B);
        QuantumPylonBlockEntity activeRelay = placePylon(helper, PYLON_C);
        EnergyReceiverFixture full = EnergyReceiverFixture.place(helper, new BlockPos(5, 1, 5));
        EnergyReceiverFixture accepting = EnergyReceiverFixture.place(helper, new BlockPos(8, 1, 5));
        full.getEnergyStorage().setEnergy(full.getEnergyStorage().getMaxEnergyStored());
        var manager = QuantumFluxNetworkManager.get(helper.getLevel());
        var network = manager.createNetwork(UUID.randomUUID(), "Shared Limits", 0x00FFFF);
        helper.assertTrue(network != null, "Failed to create budget fixture");
        assignPylon(helper, manager, network, input, PYLON_A);
        assignPylon(helper, manager, network, blockedRelay, PYLON_B);
        assignPylon(helper, manager, network, activeRelay, PYLON_C);
        helper.assertTrue(blockedRelay.tryLink(full.getBlockPos()), "Full remote link");
        helper.assertTrue(activeRelay.tryLink(accepting.getBlockPos()), "Accepting remote link");
        int supplied = input.getEnergyStorage().receiveEnergy(30_001, false);
        helper.succeedWhen(() -> {
            helper.assertTrue(accepting.getEnergyStorage().getEnergyStored() > 0, "Remote delivery must start");
            tickPylon(helper, input);
            int afterCoordinator = accepting.getEnergyStorage().getEnergyStored();
            tickPylon(helper, blockedRelay);
            tickPylon(helper, activeRelay);
            helper.assertValueEqual(accepting.getEnergyStorage().getEnergyStored(), afterCoordinator,
                    "Later member ticks must not spend the network's cycle budget again");
            helper.assertValueEqual(accepting.getEnergyStorage().getEnergyStored()
                    + input.getEnergyStorage().getEnergyStored()
                    + blockedRelay.getEnergyStorage().getEnergyStored()
                    + activeRelay.getEnergyStorage().getEnergyStored(), supplied,
                    "Partial remote delivery must conserve the entire pool");
            helper.assertValueEqual(accepting.getEnergyStorage().getEnergyStored(), supplied,
                    "Full remote receivers must not strand usable E");
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 40)
    public static void wirelessPylonLinksCannotCirculateEnergy(GameTestHelper helper) {
        QuantumPylonBlockEntity first = placePylon(helper, PYLON_A);
        QuantumPylonBlockEntity second = placePylon(helper, PYLON_B);
        helper.assertTrue(!(first.tryLink(second.getBlockPos())), "Use shared networks to connect pylons");
        helper.assertTrue(!(first.tryLink(second.getBlockPos().above())), "Top halves cannot bypass receiver rules");
        // Preserve legacy link configuration, but never send E around its cycle.
        first.getConnections().add(new com.zerotheabsolute.quantumflux.blockentity.ConnectionData(
                second.getBlockPos(), "Legacy pylon link"));
        second.getConnections().add(new com.zerotheabsolute.quantumflux.blockentity.ConnectionData(
                first.getBlockPos(), "Legacy return link"));
        first.getEnergyStorage().receiveEnergy(73, false);
        helper.runAfterDelay(25, () -> {
            helper.assertValueEqual(first.getEnergyStorage().getEnergyStored(), 73, "Legacy loop retains source E");
            helper.assertValueEqual(second.getEnergyStorage().getEnergyStored(), 0, "Legacy loop never circulates E");
            helper.assertTrue(first.getTotalThroughput() + second.getTotalThroughput() == 0,
                    "Pylon circulation must not produce phantom throughput");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void creativeTopBreakDropsNothing(GameTestHelper helper) {
        placePylon(helper, PYLON_A);
        breakPylonHalfAsCreativePlayer(helper, PYLON_A.above());
        helper.assertBlockPresent(Blocks.AIR, PYLON_A);
        helper.assertBlockPresent(Blocks.AIR, PYLON_A.above());
        helper.assertItemEntityCountIs(QFItems.QUANTUM_PYLON.get(), PYLON_A, 4.0, 0);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void linkingDataCodecRejectsInvalidDimensions(GameTestHelper helper) {
        var valid = new com.google.gson.JsonObject();
        valid.addProperty("dimension", "minecraft:overworld");
        valid.add("pylon_pos", BlockPos.CODEC.encodeStart(JsonOps.INSTANCE, BlockPos.ZERO).getOrThrow());
        valid.addProperty("active", true);
        helper.assertTrue(QFDataComponents.LinkingData.CODEC.parse(JsonOps.INSTANCE, valid).isSuccess(),
                "Valid dimension identifier was rejected");

        var legacy = valid.deepCopy();
        legacy.addProperty("dimension", "");
        helper.assertTrue(QFDataComponents.LinkingData.CODEC.parse(JsonOps.INSTANCE, legacy).isSuccess(),
                "Legacy empty dimension identifier was rejected");

        var oversized = valid.deepCopy();
        oversized.add("dimension", new JsonPrimitive("a:" + "b".repeat(129)));
        helper.assertTrue(QFDataComponents.LinkingData.CODEC.parse(JsonOps.INSTANCE, oversized).isError(),
                "Oversized dimension identifier was accepted");

        var malformed = valid.deepCopy();
        malformed.addProperty("dimension", "Not A Resource Location");
        helper.assertTrue(QFDataComponents.LinkingData.CODEC.parse(JsonOps.INSTANCE, malformed).isError(),
                "Malformed dimension identifier was accepted");
        helper.succeed();
    }

    private static QuantumPylonBlockEntity placePylon(GameTestHelper helper, BlockPos relativeBottomPos) {
        helper.setBlock(relativeBottomPos, pylonBottomState());
        ServerLevel level = helper.getLevel();
        BlockPos absoluteBottomPos = helper.absolutePos(relativeBottomPos);
        QFBlocks.QUANTUM_PYLON.get().setPlacedBy(
                level,
                absoluteBottomPos,
                level.getBlockState(absoluteBottomPos),
                null,
                ItemStack.EMPTY
        );

        BlockEntity blockEntity = level.getBlockEntity(absoluteBottomPos);
        helper.assertTrue(blockEntity instanceof QuantumPylonBlockEntity,
                "Pylon placement did not create its bottom block entity");
        return (QuantumPylonBlockEntity) blockEntity;
    }

    private static BlockState pylonBottomState() {
        return QFBlocks.QUANTUM_PYLON.get().defaultBlockState()
                .setValue(QuantumPylonBlock.HALF, QuantumPylonBlock.PylonHalf.BOTTOM)
                .setValue(QuantumPylonBlock.ACTIVE, false);
    }

    private static void assertComparator(GameTestHelper helper, BlockState state, ServerLevel level,
                                         BlockPos absolutePos, int expected) {
        int actual = QFBlocks.QUANTUM_PYLON.get().getAnalogOutputSignal(state, level, absolutePos);
        helper.assertValueEqual(actual, expected, "Comparator output after E transition");
    }

    private static void assignPylon(GameTestHelper helper, QuantumFluxNetworkManager manager,
                                    QFNetwork network, QuantumPylonBlockEntity pylon,
                                    BlockPos relativePos) {
        BlockPos absolutePos = helper.absolutePos(relativePos);
        helper.assertTrue(manager.addPylon(network.getUuid(), absolutePos),
                "Failed to add pylon to balance test network");
        pylon.setNetworkId(network.getUuid());
    }

    private static void breakPylonHalfAsSurvivalPlayer(GameTestHelper helper, BlockPos relativePos) {
        var player = net.fabricmc.fabric.api.entity.FakePlayer.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "QFSurvival"));
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        player.gameMode.destroyBlock(helper.absolutePos(relativePos));
    }

    private static void breakPylonHalfAsCreativePlayer(GameTestHelper helper, BlockPos relativePos) {
        var player = net.fabricmc.fabric.api.entity.FakePlayer.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "QFCreative"));
        player.setGameMode(GameType.CREATIVE);
        player.gameMode.destroyBlock(helper.absolutePos(relativePos));
    }

    private static void assertSinglePylonDropAndBothHalvesGone(GameTestHelper helper) {
        helper.assertBlockPresent(Blocks.AIR, PYLON_A);
        helper.assertBlockPresent(Blocks.AIR, PYLON_A.above());
        helper.assertItemEntityCountIs(QFItems.QUANTUM_PYLON.get(), PYLON_A, 4.0, 1);
    }
}
