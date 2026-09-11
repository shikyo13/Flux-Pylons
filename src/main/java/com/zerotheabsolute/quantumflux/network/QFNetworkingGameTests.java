package com.zerotheabsolute.quantumflux.network;

import com.zerotheabsolute.quantumflux.gametest.EnergyReceiverFixture;

import com.mojang.authlib.GameProfile;
import com.zerotheabsolute.quantumflux.QuantumFlux;
import com.zerotheabsolute.quantumflux.block.QuantumPylonBlock;
import com.zerotheabsolute.quantumflux.blockentity.QuantumPylonBlockEntity;
import com.zerotheabsolute.quantumflux.init.QFBlocks;
import com.zerotheabsolute.quantumflux.init.QFDataComponents;
import com.zerotheabsolute.quantumflux.init.QFItems;
import com.zerotheabsolute.quantumflux.network.data.QFNetwork;
import com.zerotheabsolute.quantumflux.network.data.QuantumFluxNetworkManager;
import com.zerotheabsolute.quantumflux.util.GadgetAction;
import com.zerotheabsolute.quantumflux.util.PriorityMode;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(QuantumFlux.MODID)
@PrefixGameTestTemplate(false)
public final class QFNetworkingGameTests {

    private static final String EMPTY_TEMPLATE = "empty";
    private static final BlockPos PYLON_POS = new BlockPos(2, 1, 2);

    private QFNetworkingGameTests() {}

    @GameTest(template = EMPTY_TEMPLATE)
    public static void gadgetPayloadEnforcesHeldItemRangeOwnershipAndClaims(GameTestHelper helper) {
        Fixture fixture = createFixture(helper, PYLON_POS);
        ServerPlayer owner = fixture.owner();
        QuantumPylonBlockEntity pylon = fixture.pylon();
        BlockPos absolutePos = helper.absolutePos(PYLON_POS);

        GadgetActionPayload valid = priorityPayload(absolutePos, PriorityMode.ROUND_ROBIN, 11);
        com.zerotheabsolute.quantumflux.gametest.FixtureAssertions.equal(helper, QFNetworking.processGadgetAction(valid, owner),
                ActionResultS2CPayload.Result.APPLIED,
                "Authorized payload result");
        com.zerotheabsolute.quantumflux.gametest.FixtureAssertions.equal(helper, pylon.getPriorityMode(), PriorityMode.ROUND_ROBIN,
                "Authorized payload mutation");
        com.zerotheabsolute.quantumflux.gametest.FixtureAssertions.equal(helper, QFNetworking.processGadgetAction(new GadgetActionPayload(
                        absolutePos, GadgetAction.SET_REDSTONE_MODE, BlockPos.ZERO,
                        com.zerotheabsolute.quantumflux.util.RedstoneMode.WHEN_POWERED.ordinal(), 14), owner),
                ActionResultS2CPayload.Result.APPLIED, "Authorized redstone control");
        com.zerotheabsolute.quantumflux.gametest.FixtureAssertions.equal(helper, QFNetworking.processGadgetAction(new GadgetActionPayload(
                        absolutePos, GadgetAction.SET_REDSTONE_MODE, BlockPos.ZERO, Integer.MAX_VALUE, 15), owner),
                ActionResultS2CPayload.Result.INVALID_REQUEST, "Invalid redstone mode");

        owner.setPos(absolutePos.getX() + 16.5D, absolutePos.getY() + 0.5D,
                absolutePos.getZ() + 0.5D);
        com.zerotheabsolute.quantumflux.gametest.FixtureAssertions.equal(helper, QFNetworking.processGadgetAction(valid, owner),
                ActionResultS2CPayload.Result.OUT_OF_RANGE,
                "Out-of-range payload result");

        owner.setPos(absolutePos.getX() + 0.5D, absolutePos.getY() + 0.5D,
                absolutePos.getZ() + 0.5D);
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        com.zerotheabsolute.quantumflux.gametest.FixtureAssertions.equal(helper, QFNetworking.processGadgetAction(valid, owner),
                ActionResultS2CPayload.Result.GADGET_REQUIRED,
                "Payload without held active gadget result");
        equipActiveGadget(owner);

        ServerPlayer stranger = makeFakePlayer(helper, "QFStranger");
        stranger.setPos(owner.position());
        equipActiveGadget(stranger);
        com.zerotheabsolute.quantumflux.gametest.FixtureAssertions.equal(helper, QFNetworking.processGadgetAction(valid, stranger),
                ActionResultS2CPayload.Result.NOT_ALLOWED,
                "Unauthorized network mutation result");

        DenyInteraction listener = new DenyInteraction(absolutePos);
        MinecraftForge.EVENT_BUS.register(listener);
        try {
            com.zerotheabsolute.quantumflux.gametest.FixtureAssertions.equal(helper, QFNetworking.processGadgetAction(valid, owner),
                    ActionResultS2CPayload.Result.NOT_ALLOWED,
                    "Claim-denied payload result");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }

        GadgetActionPayload invalidOrdinal = new GadgetActionPayload(
                absolutePos, GadgetAction.SET_PRIORITY_MODE, BlockPos.ZERO,
                Integer.MAX_VALUE, 12);
        com.zerotheabsolute.quantumflux.gametest.FixtureAssertions.equal(helper, QFNetworking.processGadgetAction(invalidOrdinal, owner),
                ActionResultS2CPayload.Result.INVALID_REQUEST,
                "Invalid enum ordinal result");

        BlockPos targetRelative = PYLON_POS.offset(3, 0, 0);
        EnergyReceiverFixture.place(helper, targetRelative);
        BlockPos targetPos = helper.absolutePos(targetRelative);
        helper.assertTrue(pylon.tryLink(targetPos),
                "Stable unlink target fixture could not be linked");
        GadgetActionPayload wrongTarget = new GadgetActionPayload(
                absolutePos, GadgetAction.UNLINK_SINGLE, targetPos.above(), 0, 13);
        com.zerotheabsolute.quantumflux.gametest.FixtureAssertions.equal(helper, QFNetworking.processGadgetAction(wrongTarget, owner),
                ActionResultS2CPayload.Result.NOT_FOUND,
                "Forged unlink target result");
        helper.assertTrue(pylon.isLinkedTo(targetPos),
                "Forged target removed a different connection");
        GadgetActionPayload exactTarget = new GadgetActionPayload(
                absolutePos, GadgetAction.UNLINK_SINGLE, targetPos, 0, 14);
        com.zerotheabsolute.quantumflux.gametest.FixtureAssertions.equal(helper, QFNetworking.processGadgetAction(exactTarget, owner),
                ActionResultS2CPayload.Result.APPLIED,
                "Stable unlink target result");
        helper.assertFalse(pylon.isLinkedTo(targetPos),
                "Exact stable target remained linked");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void gadgetPayloadRateLimitRejectsFortyFirstMutation(GameTestHelper helper) {
        Fixture fixture = createFixture(helper, PYLON_POS);
        ServerPlayer owner = fixture.owner();
        BlockPos absolutePos = helper.absolutePos(PYLON_POS);
        PriorityMode[] modes = PriorityMode.values();

        for (int index = 0; index < 40; index++) {
            ActionResultS2CPayload.Result result = QFNetworking.processGadgetAction(
                    priorityPayload(absolutePos, modes[index % modes.length], index + 1), owner);
            helper.assertFalse(result == ActionResultS2CPayload.Result.RATE_LIMITED,
                    "Payload was rate-limited before the configured limit");
        }
        com.zerotheabsolute.quantumflux.gametest.FixtureAssertions.equal(helper, QFNetworking.processGadgetAction(
                        priorityPayload(absolutePos, PriorityMode.EQUAL, 41), owner),
                ActionResultS2CPayload.Result.RATE_LIMITED,
                "Forty-first payload result");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void remoteNetworkReadingsRespectAccessAndDoNotLoadChunks(GameTestHelper helper) {
        Fixture fixture = createFixture(helper, PYLON_POS);
        var level = helper.getLevel();
        var manager = QuantumFluxNetworkManager.get(level);
        var network = manager.getNetwork(fixture.pylon().getNetworkId());
        fixture.pylon().getEnergyStorage().receiveEnergy(73, false);
        BlockPos unloaded = fixture.pylon().getBlockPos().offset(1_000_000, 0, 1_000_000);
        helper.assertFalse(level.hasChunkAt(unloaded), "Remote fixture chunk must start unloaded");
        helper.assertTrue(manager.addPylon(network.getUuid(), unloaded), "Persisted unloaded member must register");

        ServerPlayer owner = fixture.owner();
        ItemStack gadget = owner.getMainHandItem();
        QFDataComponents.SELECTED_NETWORK.set(gadget, network.getUuid());
        QFDataComponents.SELECTED_NETWORK_DIMENSION.set(gadget, level.dimension().location().toString());
        owner.setPos(fixture.pylon().getBlockPos().getX() + 512.5, 64, fixture.pylon().getBlockPos().getZ() + 512.5);
        var snapshots = new java.util.HashMap<java.util.UUID, NetworkTelemetryPayload>();
        var reading = QFNetworking.selectedNetworkTelemetry(owner, manager, snapshots);
        helper.assertTrue(reading != null, "Owner must receive network readings beyond local interaction distance");
        helper.assertTrue(reading.energy() == 73 && reading.loadedPylons() == 1 && reading.pylons().size() == 2,
                "Remote readings must distinguish spendable FE and loaded versus total membership");
        helper.assertFalse(level.hasChunkAt(unloaded), "Telemetry must not force-load a member chunk");

        ServerPlayer guest = makeFakePlayer(helper, "QFReadingsGuest");
        guest.setItemInHand(InteractionHand.MAIN_HAND, gadget.copy());
        helper.assertTrue(QFNetworking.selectedNetworkTelemetry(guest, manager, snapshots) == null,
                "Private readings must not be disclosed through another player's cached snapshot");
        network.addMember(guest.getUUID());
        helper.assertTrue(QFNetworking.selectedNetworkTelemetry(guest, manager, snapshots) != null,
                "An authorized member can monitor the network remotely");
        network.removeMember(guest.getUUID());
        helper.assertTrue(QFNetworking.selectedNetworkTelemetry(guest, manager, snapshots) == null,
                "Revocation must take effect before reusing a snapshot");
        network.setAccessMode(QFNetwork.AccessMode.PUBLIC);
        helper.assertTrue(QFNetworking.selectedNetworkTelemetry(guest, manager, snapshots) != null,
                "Public network readings must be available to a selected guest");
        QFDataComponents.SELECTED_NETWORK_DIMENSION.set(guest.getMainHandItem(), "minecraft:the_nether");
        helper.assertTrue(QFNetworking.selectedNetworkTelemetry(guest, manager, snapshots) == null,
                "A stale dimension selection must not expose matching UUID data");
        manager.removePylon(network.getUuid(), unloaded);
        helper.succeed();
    }

    private static Fixture createFixture(GameTestHelper helper, BlockPos relativePos) {
        QuantumPylonBlockEntity pylon = placePylon(helper, relativePos);
        BlockPos absolutePos = helper.absolutePos(relativePos);

        ServerPlayer owner = makeFakePlayer(helper, "QFOwner");
        owner.setPos(absolutePos.getX() + 0.5D, absolutePos.getY() + 0.5D,
                absolutePos.getZ() + 0.5D);
        equipActiveGadget(owner);

        QuantumFluxNetworkManager manager = QuantumFluxNetworkManager.get(helper.getLevel());
        QFNetwork network = manager.createNetwork(owner.getUUID(), "Packet Fixture", 0x00CFE8);
        helper.assertTrue(network != null, "Fixture network was not created");
        helper.assertTrue(manager.addPylon(network.getUuid(), absolutePos),
                "Fixture pylon was not assigned to its network");
        pylon.setNetworkId(network.getUuid());
        return new Fixture(owner, pylon);
    }

    private static QuantumPylonBlockEntity placePylon(
            GameTestHelper helper, BlockPos relativePos) {
        helper.setBlock(relativePos, QFBlocks.QUANTUM_PYLON.get().defaultBlockState()
                .setValue(QuantumPylonBlock.HALF, QuantumPylonBlock.PylonHalf.BOTTOM)
                .setValue(QuantumPylonBlock.ACTIVE, false));
        BlockPos absolutePos = helper.absolutePos(relativePos);
        QFBlocks.QUANTUM_PYLON.get().setPlacedBy(
                helper.getLevel(), absolutePos, helper.getLevel().getBlockState(absolutePos),
                null, ItemStack.EMPTY);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(absolutePos);
        helper.assertTrue(blockEntity instanceof QuantumPylonBlockEntity,
                "Fixture pylon block entity was not created");
        return (QuantumPylonBlockEntity) blockEntity;
    }

    private static void equipActiveGadget(ServerPlayer player) {
        ItemStack gadget = new ItemStack(QFItems.QUANTUM_GADGET.get());
        QFDataComponents.GADGET_ACTIVE.set(gadget, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, gadget);
    }

    private static ServerPlayer makeFakePlayer(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(
                helper.getLevel(), new GameProfile(java.util.UUID.randomUUID(), name));
    }

    private static GadgetActionPayload priorityPayload(
            BlockPos pos, PriorityMode mode, int requestId) {
        return new GadgetActionPayload(
                pos, GadgetAction.SET_PRIORITY_MODE, BlockPos.ZERO, mode.ordinal(), requestId);
    }

    private record Fixture(ServerPlayer owner, QuantumPylonBlockEntity pylon) {}

    private static final class DenyInteraction {
        private final BlockPos deniedPos;

        private DenyInteraction(BlockPos deniedPos) {
            this.deniedPos = deniedPos;
        }

        @SubscribeEvent
        public void deny(PlayerInteractEvent.RightClickBlock event) {
            if (deniedPos.equals(event.getPos())) event.setCanceled(true);
        }
    }
}
