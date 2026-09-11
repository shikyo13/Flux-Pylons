package com.zerotheabsolute.quantumflux.gametest;

import com.mojang.authlib.GameProfile;
import com.zerotheabsolute.quantumflux.QuantumFlux;
import com.zerotheabsolute.quantumflux.block.QuantumPylonBlock;
import com.zerotheabsolute.quantumflux.blockentity.QuantumPylonBlockEntity;
import com.zerotheabsolute.quantumflux.init.QFBlocks;
import com.zerotheabsolute.quantumflux.init.QFDataComponents;
import com.zerotheabsolute.quantumflux.init.QFItems;
import com.zerotheabsolute.quantumflux.item.UpgradeType;
import com.zerotheabsolute.quantumflux.menu.PylonUpgradeMenu;
import com.zerotheabsolute.quantumflux.network.data.QuantumFluxNetworkManager;
import com.zerotheabsolute.quantumflux.util.ConnectionStatus;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import com.zerotheabsolute.quantumflux.gametest.FixturePlayers;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(value = QuantumFlux.MODID, namespace = QuantumFlux.MODID)
public final class UpgradeGameTests {
    private static final BlockPos PYLON = new BlockPos(2, 1, 2);

    private UpgradeGameTests() {}

    @GameTest(template = "quantumflux:empty")
    public static void upgradeRecipesHaveUniqueCraftingResults(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        for (Item upgrade : upgradeItems()) {
            var id = BuiltInRegistries.ITEM.getKey(upgrade);
            var recipe = (ShapedRecipe) recipes.byKey(id).orElseThrow().value();
            var ingredients = recipe.getIngredients().stream()
                    .map(ingredient -> ingredient.isEmpty() ? ItemStack.EMPTY : ingredient.getItems()[0].copy())
                    .toList();
            var input = CraftingInput.of(recipe.getWidth(), recipe.getHeight(), ingredients);
            // Query every loaded crafting recipe: a recipe-book hint can conceal a
            // collision that produces a vanilla compass/clock after JEI transfer.
            var matches = recipes.getRecipesFor(RecipeType.CRAFTING, input, helper.getLevel());
            helper.assertValueEqual(matches.size(), 1,
                    "Upgrade grid must have one result for " + id + ": "
                            + matches.stream().map(match -> match.id().toString()).toList());
            helper.assertTrue(matches.getFirst().value().assemble(input, helper.getLevel().registryAccess()).is(upgrade),
                    "Crafting grid produced the wrong item for " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "quantumflux:empty")
    public static void inventoryUpgradesChangeActualDeliveryAndSurviveReload(GameTestHelper helper) {
        var pylon = placePylon(helper, PYLON);
        var player = player(helper, pylon);
        var menu = menu(player, pylon);
        Item[] items = upgradeItems();
        for (int i = 0; i < items.length; i++) {
            // Exercise both empty-slot insertion and vanilla merging into a live stack.
            player.getInventory().setItem(9, new ItemStack(items[i], 1));
            menu.clicked(4, 0, ClickType.QUICK_MOVE, player);
            helper.getLevel().getChunkAt(pylon.getBlockPos()).setUnsaved(false);
            player.getInventory().setItem(9, new ItemStack(items[i], 3));
            menu.clicked(4, 0, ClickType.QUICK_MOVE, player);
            helper.assertTrue(helper.getLevel().getChunkAt(pylon.getBlockPos()).isUnsaved(),
                    "Merged upgrades must mark the chunk for saving");
            helper.assertValueEqual(pylon.getUpgrades().getStackInSlot(i).getCount(), 4, "Installed upgrade count");
            helper.assertTrue(player.getInventory().getItem(9).isEmpty(), "Upgrade stack remained in player inventory");
        }
        helper.assertValueEqual(menu.range(), 32, "Four range upgrades");
        helper.assertValueEqual(menu.maxConnections(), 28, "Four connection upgrades");
        helper.assertValueEqual(menu.transferLimit(), 30_000, "Four throughput upgrades");
        helper.assertValueEqual(pylon.getEnergyStorage().getMaxEnergyStored(), 1_600_000,
                "Buffer capacity must change immediately after merging");
        var receiver = EnergyReceiverFixture.place(helper, PYLON.offset(3, 0, 0));
        helper.assertTrue(pylon.tryLink(receiver.getBlockPos()), "Receiver link");
        pylon.getEnergyStorage().receiveEnergy(100_000, false);
        tick(helper, pylon);
        helper.assertValueEqual(receiver.getEnergyStorage().getEnergyStored(), 30_000, "Actual upgraded FE delivery");
        helper.assertValueEqual(pylon.getEnergyStorage().getEnergyStored(), 70_000, "Only delivered FE was spent");

        var saved = pylon.saveWithoutMetadata(helper.getLevel().registryAccess());
        var restored = new QuantumPylonBlockEntity(pylon.getBlockPos(), pylon.getBlockState());
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());
        for (UpgradeType type : UpgradeType.values()) {
            helper.assertValueEqual(restored.getUpgrades().level(type), 4, "Saved upgrade level " + type);
        }
        helper.assertValueEqual(restored.getEffectiveBufferSize(), 1_600_000, "Reloaded upgrade capacity");
        helper.assertValueEqual(restored.getEnergyStorage().getEnergyStored(), 70_000, "Reloaded stored FE");
        helper.assertValueEqual(restored.getConnections().size(), 1, "Reloaded links");
        helper.succeed();
    }

    @GameTest(template = "quantumflux:empty")
    public static void chargedBuffersCannotBeRemovedByInventoryActions(GameTestHelper helper) {
        var pylon = placePylon(helper, PYLON);
        var player = player(helper, pylon);
        var menu = menu(player, pylon);
        pylon.getUpgrades().insertItem(3, new ItemStack(QFItems.BUFFER_UPGRADE.get(), 4), false);
        pylon.getEnergyStorage().receiveEnergy(1_500_000, false);
        for (ClickType click : new ClickType[]{ClickType.PICKUP, ClickType.QUICK_MOVE, ClickType.SWAP, ClickType.THROW}) {
            menu.clicked(3, click == ClickType.SWAP ? 1 : 0, click, player);
            helper.assertValueEqual(pylon.getUpgrades().level(UpgradeType.BUFFER), 4, "Charged buffer removal via " + click);
            helper.assertTrue(menu.getCarried().isEmpty(), "Rejected removal placed an item on the cursor");
            helper.assertTrue(player.getInventory().getItem(1).isEmpty(), "Rejected removal moved an item to the hotbar");
        }
        menu.setCarried(new ItemStack(QFItems.BUFFER_UPGRADE.get()));
        menu.clicked(3, 0, ClickType.PICKUP_ALL, player);
        helper.assertValueEqual(menu.getCarried().getCount(), 1, "Double click removed a locked buffer");
        menu.setCarried(ItemStack.EMPTY);
        helper.assertValueEqual(pylon.getEnergyStorage().getEnergyStored(), 1_500_000, "Rejected removal lost stored FE");
        helper.assertTrue(pylon.getUpgrades().extractItem(3, 4, false).isEmpty(), "Direct handler extraction bypassed the lock");

        pylon.getEnergyStorage().consumeEnergy(1_400_000);
        menu.clicked(3, 0, ClickType.QUICK_MOVE, player);
        helper.assertValueEqual(pylon.getUpgrades().level(UpgradeType.BUFFER), 0, "Drained buffer removal");
        helper.assertValueEqual(countInventory(player, QFItems.BUFFER_UPGRADE.get()), 4, "Recovered buffer items");
        helper.assertValueEqual(pylon.getEnergyStorage().getEnergyStored(), 100_000, "Removal retained stored FE");
        helper.assertValueEqual(pylon.getEnergyStorage().getMaxEnergyStored(), 100_000, "Capacity after removal");

        var reducedConfiguration = pylon.saveWithoutMetadata(helper.getLevel().registryAccess());
        reducedConfiguration.putInt("Energy", 900_000);
        pylon.loadWithComponents(reducedConfiguration, helper.getLevel().registryAccess());
        helper.assertValueEqual(pylon.getEnergyStorage().getEnergyStored(), 900_000, "Old larger buffer must not lose FE on load");
        helper.assertValueEqual(pylon.getEnergyStorage().receiveEnergy(1, false), 0, "Excess storage accepted new FE");
        pylon.getEnergyStorage().consumeEnergy(800_001);
        helper.assertValueEqual(pylon.getEnergyStorage().getMaxEnergyStored(), 100_000, "Drained excess capacity");
        helper.assertValueEqual(pylon.getEnergyStorage().receiveEnergy(2, false), 1, "Input resumes at the new capacity");
        helper.succeed();
    }

    @GameTest(template = "quantumflux:empty")
    public static void upgradeReadingsSurviveVanillaShortTransport(GameTestHelper helper) {
        var pylon = placePylon(helper, PYLON);
        var player = player(helper, pylon);
        var server = menu(player, pylon);
        var extra = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        extra.writeBlockPos(pylon.getBlockPos());
        var client = new PylonUpgradeMenu(1, new Inventory(player), extra);
        extra.release();
        server.setSynchronizer(new ContainerSynchronizer() {
            @Override public void sendInitialData(AbstractContainerMenu menu, NonNullList<ItemStack> stacks,
                                                  ItemStack carried, int[] data) {
                for (int i = 0; i < stacks.size(); i++) client.getSlot(i).set(stacks.get(i).copy());
                for (int i = 0; i < data.length; i++) sendDataChange(menu, i, data[i]);
            }
            @Override public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) {
                client.getSlot(slot).set(stack.copy());
            }
            @Override public void sendCarriedChange(AbstractContainerMenu menu, ItemStack stack) {}
            @Override public void sendDataChange(AbstractContainerMenu menu, int id, int value) {
                // ClientboundContainerSetDataPacket transmits signed 16-bit values.
                client.setData(id, (short) value);
            }
        });
        pylon.getUpgrades().insertItem(3, new ItemStack(QFItems.BUFFER_UPGRADE.get(), 4), false);
        pylon.getEnergyStorage().receiveEnergy(1_567_890, false);
        server.broadcastChanges();
        helper.assertValueEqual(client.bufferLimit(), 1_600_000, "Client capacity above 16 bits");
        helper.assertValueEqual(client.storedEnergy(), 1_567_890, "Client energy with signed low word");
        helper.assertValueEqual(client.baseBufferLimit(), 100_000, "Server-configured base capacity");
        helper.assertFalse(client.canRemoveBuffers(), "Client buffer lock");
        helper.assertValueEqual(client.previewValue(UpgradeType.RANGE, 1), 20, "Client next-upgrade preview");
        helper.succeed();
    }

    @GameTest(template = "quantumflux:empty")
    public static void downgradeRetainsInactiveLinksAndRestoringUpgradesResumesDelivery(GameTestHelper helper) {
        var pylon = placePylon(helper, PYLON);
        pylon.getUpgrades().insertItem(0, new ItemStack(QFItems.RANGE_UPGRADE.get(), 4), false);
        pylon.getUpgrades().insertItem(1, new ItemStack(QFItems.CAPACITY_UPGRADE.get(), 4), false);
        BlockPos farRelative = PYLON.above(25);
        var far = EnergyReceiverFixture.place(helper, farRelative);
        helper.assertTrue(pylon.tryLink(far.getBlockPos()), "Upgraded range link");
        var nearby = new ArrayList<EnergyReceiverFixture>();
        for (int i = 0; i < 27; i++) {
            var receiver = EnergyReceiverFixture.place(helper, new BlockPos(4 + i % 7, 1, 4 + i / 7));
            nearby.add(receiver);
            helper.assertTrue(pylon.tryLink(receiver.getBlockPos()), "Upgraded capacity link " + i);
        }
        pylon.getUpgrades().extractItem(0, 4, false);
        pylon.getUpgrades().extractItem(1, 4, false);
        pylon.getEnergyStorage().receiveEnergy(100_000, false);
        tick(helper, pylon);
        helper.assertValueEqual(pylon.getConnections().size(), 28, "Downgrade discarded configuration");
        helper.assertValueEqual(pylon.getConnectionStatus(far.getBlockPos()), ConnectionStatus.OUT_OF_RANGE, "Range explanation");
        helper.assertValueEqual(pylon.getConnectionStatus(nearby.get(19).getBlockPos()), ConnectionStatus.CONNECTION_LIMIT,
                "Connection limit explanation");
        helper.assertValueEqual(far.getEnergyStorage().getEnergyStored(), 0, "Delivery outside downgraded range");
        helper.assertValueEqual(nearby.get(19).getEnergyStorage().getEnergyStored(), 0, "Delivery beyond downgraded connection limit");
        helper.assertTrue(nearby.getFirst().getEnergyStorage().getEnergyStored() > 0, "Enabled receiver was starved by inactive links");
        var saved = pylon.saveWithoutMetadata(helper.getLevel().registryAccess());
        pylon.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(pylon.getConnections().size(), 28, "Inactive links lost on reload");
        pylon.getUpgrades().insertItem(0, new ItemStack(QFItems.RANGE_UPGRADE.get(), 4), false);
        pylon.getUpgrades().insertItem(1, new ItemStack(QFItems.CAPACITY_UPGRADE.get(), 4), false);
        tick(helper, pylon);
        helper.assertTrue(far.getEnergyStorage().getEnergyStored() > 0, "Restored range did not resume delivery");
        helper.assertTrue(nearby.getLast().getEnergyStorage().getEnergyStored() > 0, "Restored capacity did not resume delivery");
        helper.setBlock(farRelative, Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = "quantumflux:empty")
    public static void breakingEitherHalfDropsEachInstalledUpgradeOnce(GameTestHelper helper) {
        for (boolean top : new boolean[]{false, true}) {
            BlockPos relative = top ? PYLON.offset(6, 0, 0) : PYLON;
            var pylon = placePylon(helper, relative);
            for (int i = 0; i < 4; i++) pylon.getUpgrades().insertItem(i, new ItemStack(upgradeItems()[i], 4), false);
            pylon.getEnergyStorage().receiveEnergy(1_000_000, false);
            BlockPos broken = top ? pylon.getBlockPos().above() : pylon.getBlockPos();
            helper.getLevel().destroyBlock(broken, true);
            helper.assertTrue(helper.getLevel().getBlockState(pylon.getBlockPos()).isAir(), "Bottom survived removal");
            helper.assertTrue(helper.getLevel().getBlockState(pylon.getBlockPos().above()).isAir(), "Top survived removal");
            // A stale second removal must not drop inventory again.
            helper.getLevel().destroyBlock(pylon.getBlockPos(), true);
            var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pylon.getBlockPos()).inflate(1.5));
            for (Item item : upgradeItems()) {
                int count = drops.stream().filter(entity -> entity.getItem().is(item))
                        .mapToInt(entity -> entity.getItem().getCount()).sum();
                helper.assertValueEqual(count, 4, "Upgrade drops after breaking " + (top ? "top" : "bottom"));
            }
            int pylons = drops.stream().filter(entity -> entity.getItem().is(QFItems.QUANTUM_PYLON.get()))
                    .mapToInt(entity -> entity.getItem().getCount()).sum();
            helper.assertValueEqual(pylons, 1, "Pylon drop count");
        }
        helper.succeed();
    }

    @GameTest(template = "quantumflux:empty")
    public static void explosionAtEitherHalfDropsUpgradesOnceAndUnregistersPylon(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = QuantumFluxNetworkManager.get(level);
        var network = manager.createNetwork(UUID.randomUUID(), "Explosion lifecycle", 0x00CFE8);
        helper.assertTrue(network != null, "Explosion network must exist");
        for (boolean top : new boolean[]{false, true}) {
            var pylon = placePylon(helper, top ? PYLON.offset(6, 0, 0) : PYLON);
            helper.assertTrue(manager.addPylon(network.getUuid(), pylon.getBlockPos()), "Pylon must join before detonation");
            pylon.setNetworkId(network.getUuid());
            for (int index = 0; index < 4; index++) {
                pylon.getUpgrades().insertItem(index, new ItemStack(upgradeItems()[index], 4), false);
            }
            BlockPos hit = top ? pylon.getBlockPos().above() : pylon.getBlockPos();
            // Apply vanilla's explosion block/loot phase to a one-half hit.
            // Restrict the blast list so adjacent parallel tests are untouched.
            var blast = new Explosion(level, null, hit.getX() + .5, hit.getY() + .5, hit.getZ() + .5,
                    2.0F, false, Explosion.BlockInteraction.DESTROY, List.of(hit));
            blast.finalizeExplosion(false);
            helper.assertTrue(level.getBlockState(pylon.getBlockPos()).isAir(), "Bottom survived explosion");
            helper.assertTrue(level.getBlockState(pylon.getBlockPos().above()).isAir(), "Top survived explosion");
            helper.assertFalse(network.hasPylon(pylon.getBlockPos()), "Destroyed pylon remains registered");
            // A repeated blast resolution must not recover a second inventory.
            blast.finalizeExplosion(false);
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pylon.getBlockPos()).inflate(1.5));
            for (Item item : upgradeItems()) {
                int count = drops.stream().filter(entity -> entity.getItem().is(item))
                        .mapToInt(entity -> entity.getItem().getCount()).sum();
                helper.assertValueEqual(count, 4, "Explosion duplicated or lost an installed upgrade");
            }
            int count = drops.stream().filter(entity -> entity.getItem().is(QFItems.QUANTUM_PYLON.get()))
                    .mapToInt(entity -> entity.getItem().getCount()).sum();
            helper.assertValueEqual(count, 1, "Explosion pylon drop count");
        }
        helper.succeed();
    }

    @GameTest(template = "quantumflux:empty")
    public static void openUpgradeMenuRechecksMembershipRangeAndClaims(GameTestHelper helper) {
        var pylon = placePylon(helper, PYLON);
        var player = player(helper, pylon);
        var manager = QuantumFluxNetworkManager.get(helper.getLevel());
        UUID owner = UUID.randomUUID();
        var network = manager.createNetwork(owner, "Upgrade access", 0x00CFE8);
        helper.assertTrue(network != null && manager.addPylon(network.getUuid(), pylon.getBlockPos()), "Network fixture");
        manager.addMember(network.getUuid(), owner, player.getUUID());
        pylon.setNetworkId(network.getUuid());
        var menu = menu(player, pylon);
        player.getInventory().setItem(9, new ItemStack(QFItems.RANGE_UPGRADE.get(), 4));
        helper.assertTrue(menu.stillValid(player), "Member menu access");
        manager.removeMember(network.getUuid(), owner, player.getUUID());
        menu.clicked(4, 0, ClickType.QUICK_MOVE, player);
        helper.assertValueEqual(pylon.getUpgrades().level(UpgradeType.RANGE), 0, "Revoked member installed an upgrade");
        manager.addMember(network.getUuid(), owner, player.getUUID());
        player.setPos(pylon.getBlockPos().getCenter().add(16, 0, 0));
        menu.clicked(4, 0, ClickType.QUICK_MOVE, player);
        helper.assertValueEqual(pylon.getUpgrades().level(UpgradeType.RANGE), 0, "Distant player installed an upgrade");
        player.setPos(pylon.getBlockPos().getCenter());
        var denied = new DenyInteraction(pylon.getBlockPos());
        MinecraftForge.EVENT_BUS.register(denied);
        try {
            menu.clicked(4, 0, ClickType.QUICK_MOVE, player);
            helper.assertValueEqual(pylon.getUpgrades().level(UpgradeType.RANGE), 0, "Claim denial was bypassed");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(denied);
        }
        helper.assertValueEqual(player.getInventory().getItem(9).getCount(), 4, "Denied clicks lost player items");
        menu.clicked(4, 0, ClickType.QUICK_MOVE, player);
        helper.assertValueEqual(pylon.getUpgrades().level(UpgradeType.RANGE), 4, "Restored permission did not allow installation");
        helper.succeed();
    }

    private static Item[] upgradeItems() {
        return new Item[]{QFItems.RANGE_UPGRADE.get(), QFItems.CAPACITY_UPGRADE.get(),
                QFItems.THROUGHPUT_UPGRADE.get(), QFItems.BUFFER_UPGRADE.get()};
    }

    private static int countInventory(ServerPlayer player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static PylonUpgradeMenu menu(ServerPlayer player, QuantumPylonBlockEntity pylon) {
        var menu = new PylonUpgradeMenu(1, player.getInventory(), pylon);
        player.containerMenu = menu;
        return menu;
    }

    private static ServerPlayer player(GameTestHelper helper, QuantumPylonBlockEntity pylon) {
        var player = FixturePlayers.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "QFUpgrades"));
        player.setPos(pylon.getBlockPos().getCenter());
        var gadget = new ItemStack(QFItems.QUANTUM_GADGET.get());
        gadget.set(QFDataComponents.GADGET_ACTIVE.get(), true);
        player.setItemInHand(InteractionHand.MAIN_HAND, gadget);
        return player;
    }

    private static QuantumPylonBlockEntity placePylon(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, QFBlocks.QUANTUM_PYLON.get().defaultBlockState()
                .setValue(QuantumPylonBlock.HALF, QuantumPylonBlock.PylonHalf.BOTTOM));
        BlockPos pos = helper.absolutePos(relative);
        QFBlocks.QUANTUM_PYLON.get().setPlacedBy(helper.getLevel(), pos,
                helper.getLevel().getBlockState(pos), null, ItemStack.EMPTY);
        return (QuantumPylonBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static void tick(GameTestHelper helper, QuantumPylonBlockEntity pylon) {
        QuantumPylonBlockEntity.serverTick(helper.getLevel(), pylon.getBlockPos(), pylon.getBlockState(), pylon);
    }

    private record DenyInteraction(BlockPos pos) {
        @SubscribeEvent public void deny(PlayerInteractEvent.RightClickBlock event) {
            if (event.getPos().equals(pos)) event.setCanceled(true);
        }
    }
}
