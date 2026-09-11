package com.zerotheabsolute.quantumflux.item;

import com.zerotheabsolute.quantumflux.block.QuantumPylonBlock;
import com.zerotheabsolute.quantumflux.blockentity.QuantumPylonBlockEntity;
import com.zerotheabsolute.quantumflux.client.ClientScreenBridge;
import com.zerotheabsolute.quantumflux.init.QFDataComponents;
import com.zerotheabsolute.quantumflux.network.QFNetworking;
import com.zerotheabsolute.quantumflux.network.data.QFNetwork;
import com.zerotheabsolute.quantumflux.network.data.QuantumFluxNetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.UUID;

public class QuantumGadgetItem extends Item {

    public QuantumGadgetItem(Properties properties) {
        super(properties);
    }

    // ══════════════════════════════════════════
    //  Right-click on a block
    // ══════════════════════════════════════════

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos clickedPos = ctx.getClickedPos();
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        if (player == null) return InteractionResult.PASS;

        boolean isActive = Boolean.TRUE.equals(QFDataComponents.GADGET_ACTIVE.get(stack));

        // ── Sneak + right-click → toggle or pylon actions ──
        if (player.isShiftKeyDown()) {
            if (!isActive) {
                return doToggle(level, player, stack);
            }

            // Resolve pylon position (TOP half → BOTTOM)
            BlockPos pylonPos = resolvePylonPos(level, clickedPos);

            if (pylonPos != null) {
                // Shift+RC pylon (active) → assign to network + enter linking mode
                return handlePylonShiftClick(level, player, stack, pylonPos);
            }

            // In linking mode, shift+click on non-pylon is an unlink attempt - don't toggle
            QFDataComponents.LinkingData linkData = QFDataComponents.LINKING_DATA.get(stack);
            if (linkData != null && linkData.active()) {
                if (handleLinkInteraction(player, level, clickedPos, stack)) {
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            }

            // Shift+RC non-pylon block (active, not linking) → toggle off
            return doToggle(level, player, stack);
        }

        // ── Gadget must be active ──
        if (!isActive) {
            return InteractionResult.FAIL;
        }

        // ── Linking mode: target is a machine ──
        if (handleLinkInteraction(player, level, clickedPos, stack)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // ── RC pylon (not linking): assign to selected network ──
        BlockPos pylonPos = resolvePylonPos(level, clickedPos);
        if (pylonPos != null) {
            return handlePylonAssign(level, player, stack, pylonPos);
        }

        // ── Not linking, not pylon: open management UI ──
        if (level.isClientSide) {
            openGadgetScreen();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ══════════════════════════════════════════
    //  Right-click in air
    // ══════════════════════════════════════════

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean isActive = Boolean.TRUE.equals(QFDataComponents.GADGET_ACTIVE.get(stack));

        // ── Sneak + right-click → toggle on/off ──
        if (player.isShiftKeyDown()) {
            InteractionResult result = doToggle(level, player, stack);
            return result.consumesAction()
                    ? InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
                    : InteractionResultHolder.pass(stack);
        }

        if (!isActive) return InteractionResultHolder.pass(stack);

        // ── Linking mode: RC air → cancel ──
        QFDataComponents.LinkingData linkData = QFDataComponents.LINKING_DATA.get(stack);
        if (linkData != null && linkData.active()) {
            if (!level.isClientSide) {
                QFDataComponents.LINKING_DATA.remove(stack);
                player.displayClientMessage(
                        Component.translatable("message.quantumflux.linking.cancelled")
                                .withStyle(ChatFormatting.GRAY), true);
                level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(),
                        SoundSource.PLAYERS, 0.5f, 1.0f);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // ── Not linking: open management UI ──
        if (level.isClientSide) {
            openGadgetScreen();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // ══════════════════════════════════════════
    //  RC pylon: assign to selected network
    // ══════════════════════════════════════════

    private InteractionResult handlePylonAssign(Level level, Player player, ItemStack stack, BlockPos pylonPos) {
        if (!level.isClientSide) {
            UUID selectedNetwork = QFDataComponents.SELECTED_NETWORK.get(stack);
            String selectedDimension = QFDataComponents.SELECTED_NETWORK_DIMENSION.get(stack);

            if (selectedNetwork == null || !dimensionId(level).equals(selectedDimension)) {
                player.displayClientMessage(
                        Component.translatable("message.quantumflux.select_network_dimension")
                                .withStyle(ChatFormatting.RED), true);
                return InteractionResult.sidedSuccess(false);
            }

            if (!canReach(player, pylonPos) || !level.hasChunkAt(pylonPos)) {
                return InteractionResult.FAIL;
            }

            BlockEntity be = level.getBlockEntity(pylonPos);
            if (be instanceof QuantumPylonBlockEntity pylonBE) {
                if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    QuantumFluxNetworkManager manager = QuantumFluxNetworkManager.get(serverLevel);
                    QFNetwork network = manager.getNetwork(selectedNetwork);

                    if (!manager.canConfigurePylon(pylonPos, pylonBE.getNetworkId(), player.getUUID())) {
                        player.displayClientMessage(
                                Component.translatable("message.quantumflux.pylon.no_permission")
                                        .withStyle(ChatFormatting.RED), true);
                    } else if (network == null || !network.canConfigure(player.getUUID())) {
                        player.displayClientMessage(
                                Component.translatable("message.quantumflux.network.no_access")
                                        .withStyle(ChatFormatting.RED), true);
                    } else if (selectedNetwork.equals(pylonBE.getNetworkId())) {
                        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                            QFNetworking.openPylonController(sp, pylonBE);
                        }
                    } else {
                        QuantumFluxNetworkManager.PylonMutationResult result =
                                manager.assignPylon(selectedNetwork, pylonPos, player.getUUID());
                        if (result == QuantumFluxNetworkManager.PylonMutationResult.SUCCESS) {
                            pylonBE.setNetworkId(selectedNetwork);
                            pylonBE.setBeamColor(network.getColor());
                            pylonBE.setBeamStyle(network.getBeamStyle());
                            pylonBE.setBeamsVisible(network.isBeamsVisible());
                            player.displayClientMessage(
                                    Component.translatable("message.quantumflux.pylon.assigned", network.getName())
                                            .withStyle(ChatFormatting.GREEN), true);
                            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                                    SoundSource.PLAYERS, 0.5f, 1.5f);

                            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                                QFNetworking.broadcastNetworkLists(serverLevel, manager);
                                QFNetworking.openPylonController(sp, pylonBE);
                            }
                        } else {
                            player.displayClientMessage(
                                    Component.translatable("message.quantumflux.pylon.assignment_rejected",
                                            Component.translatable("message.quantumflux.pylon.result."
                                                    + result.name().toLowerCase(java.util.Locale.ROOT)))
                                            .withStyle(ChatFormatting.RED), true);
                        }
                    }
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ══════════════════════════════════════════
    //  Shift+RC pylon: enter linking mode
    // ══════════════════════════════════════════

    private InteractionResult handlePylonShiftClick(Level level, Player player, ItemStack stack, BlockPos pylonPos) {
        if (!level.isClientSide) {
            if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)
                    || !canReach(player, pylonPos)
                    || !level.hasChunkAt(pylonPos)
                    || !(level.getBlockEntity(pylonPos) instanceof QuantumPylonBlockEntity pylonBE)
                    || !QuantumFluxNetworkManager.get(serverLevel).canConfigurePylon(
                            pylonPos, pylonBE.getNetworkId(), player.getUUID())) {
                player.displayClientMessage(
                        Component.translatable("message.quantumflux.pylon.no_permission")
                                .withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }

            // Enter linking mode
            QFDataComponents.LINKING_DATA.set(stack,
                    new QFDataComponents.LinkingData(dimensionId(level), pylonPos, true));
            player.displayClientMessage(
                    Component.translatable("message.quantumflux.linking.started")
                            .withStyle(ChatFormatting.YELLOW), true);
            level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RETURN,
                    SoundSource.PLAYERS, 0.5f, 2.0f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ══════════════════════════════════════════
    //  Toggle helper
    // ══════════════════════════════════════════

    private InteractionResult doToggle(Level level, Player player, ItemStack stack) {
        Boolean wasActive = QFDataComponents.GADGET_ACTIVE.get(stack);
        boolean newActive = !Boolean.TRUE.equals(wasActive);
        if (!level.isClientSide) {
            QFDataComponents.GADGET_ACTIVE.set(stack, newActive);
            if (!newActive) {
                QFDataComponents.LINKING_DATA.remove(stack);
            }
            level.playSound(null, player.blockPosition(),
                    newActive ? com.zerotheabsolute.quantumflux.init.QFSounds.GADGET_ON.get()
                              : com.zerotheabsolute.quantumflux.init.QFSounds.GADGET_OFF.get(),
                    SoundSource.PLAYERS, 0.8f, 1.0f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ══════════════════════════════════════════
    //  Hotbar popup
    // ══════════════════════════════════════════

    public Component getHighlightTip(ItemStack stack, Component displayName) {
        boolean active = Boolean.TRUE.equals(QFDataComponents.GADGET_ACTIVE.get(stack));
        return Component.empty()
                .append(displayName)
                .append(Component.translatable(active
                                ? "tooltip.quantumflux.highlight.on"
                                : "tooltip.quantumflux.highlight.off")
                        .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                .append(Component.translatable("tooltip.quantumflux.highlight.toggle")
                        .withStyle(ChatFormatting.DARK_AQUA));
    }

    // ══════════════════════════════════════════
    //  Inventory tooltip
    // ══════════════════════════════════════════

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level ctx, List<Component> tooltip, TooltipFlag flag) {
        boolean active = Boolean.TRUE.equals(QFDataComponents.GADGET_ACTIVE.get(stack));
        tooltip.add(Component.translatable(active
                        ? "tooltip.quantumflux.status.on"
                        : "tooltip.quantumflux.status.off")
                .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.quantumflux.toggle")
                .withStyle(ChatFormatting.YELLOW));

        QFDataComponents.LinkingData linkData = QFDataComponents.LINKING_DATA.get(stack);

        if (linkData != null && linkData.active()) {
            tooltip.add(Component.translatable("tooltip.quantumflux.linking_position",
                            linkData.pylonPos().getX(), linkData.pylonPos().getY(), linkData.pylonPos().getZ())
                    .withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.translatable("tooltip.quantumflux.linking_connect")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.quantumflux.linking_cancel")
                    .withStyle(ChatFormatting.GRAY));
        } else if (active) {
            tooltip.add(Component.translatable("tooltip.quantumflux.open_manager")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.quantumflux.start_linking")
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.quantumflux.flavor")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    // ══════════════════════════════════════════
    //  Link/Unlink interaction (linking mode)
    // ══════════════════════════════════════════

    /**
     * Handles link/unlink interaction when in linking mode.
     * Called from both useOn() and GadgetInteractionHandler (for blocks with GUIs).
     * Returns true if the interaction was handled.
     */
    public static boolean handleLinkInteraction(Player player, Level level, BlockPos clickedPos, ItemStack stack) {
        QFDataComponents.LinkingData linkData = QFDataComponents.LINKING_DATA.get(stack);
        if (linkData == null || !linkData.active()) return false;

        if (!dimensionId(level).equals(linkData.dimension())) {
            if (!level.isClientSide) {
                QFDataComponents.LINKING_DATA.remove(stack);
                player.displayClientMessage(
                        Component.translatable("message.quantumflux.linking.dimension_cancelled")
                                .withStyle(ChatFormatting.RED), true);
            }
            return true;
        }

        // Don't intercept clicks on pylons - those are handled separately
        if (level.getBlockState(clickedPos).getBlock() instanceof QuantumPylonBlock) return false;

        if (!level.isClientSide) {
            BlockPos pylonPos = linkData.pylonPos();
            if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)
                    || !level.hasChunkAt(pylonPos)
                    || !level.hasChunkAt(clickedPos)
                    || !canReach(player, clickedPos)) {
                QFDataComponents.LINKING_DATA.remove(stack);
                return true;
            }

            BlockEntity pylonEntity = level.getBlockEntity(pylonPos);

            if (!(pylonEntity instanceof QuantumPylonBlockEntity pylonBE)) {
                player.displayClientMessage(
                        Component.translatable("message.quantumflux.linking.pylon_missing")
                                .withStyle(ChatFormatting.RED), true);
                QFDataComponents.LINKING_DATA.remove(stack);
                return true;
            }

            QuantumFluxNetworkManager manager = QuantumFluxNetworkManager.get(serverLevel);
            if (!manager.canConfigurePylon(pylonPos, pylonBE.getNetworkId(), player.getUUID())) {
                player.displayClientMessage(
                        Component.translatable("message.quantumflux.pylon.no_permission")
                                .withStyle(ChatFormatting.RED), true);
                QFDataComponents.LINKING_DATA.remove(stack);
                return true;
            }

            if (player.isShiftKeyDown()) {
                if (pylonBE.unlink(clickedPos)) {
                    player.displayClientMessage(
                            Component.translatable("message.quantumflux.machine.unlinked",
                                    clickedPos.getX(), clickedPos.getY(), clickedPos.getZ())
                                    .withStyle(ChatFormatting.GOLD), true);
                    level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE,
                            SoundSource.PLAYERS, 0.5f, 1.0f);
                } else {
                    player.displayClientMessage(
                            Component.translatable("message.quantumflux.machine.not_linked")
                                    .withStyle(ChatFormatting.RED), true);
                }
            } else {
                if (pylonBE.tryLink(clickedPos)) {
                    player.displayClientMessage(
                            Component.translatable("message.quantumflux.machine.linked",
                                    clickedPos.getX(), clickedPos.getY(), clickedPos.getZ())
                                    .withStyle(ChatFormatting.GREEN), true);
                    level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                            SoundSource.PLAYERS, 0.5f, 1.5f);
                } else {
                    double dist = Math.sqrt(pylonPos.distSqr(clickedPos));
                    int range = pylonBE.getEffectiveRange();
                    if (level.getBlockState(clickedPos).getBlock() instanceof QuantumPylonBlock) {
                        player.displayClientMessage(
                                Component.translatable("message.quantumflux.machine.pylon_network")
                                        .withStyle(ChatFormatting.YELLOW), true);
                    } else if (dist > range) {
                        player.displayClientMessage(
                                Component.translatable("message.quantumflux.machine.out_of_range",
                                        Math.round(dist), range)
                                        .withStyle(ChatFormatting.RED), true);
                    } else if (pylonBE.isLinkedTo(clickedPos)) {
                        player.displayClientMessage(
                                Component.translatable("message.quantumflux.machine.already_linked")
                                        .withStyle(ChatFormatting.YELLOW), true);
                    } else if (pylonBE.getConnections().size() >= pylonBE.getEffectiveMaxConnections()) {
                        player.displayClientMessage(
                                Component.translatable("message.quantumflux.machine.maximum_connections")
                                        .withStyle(ChatFormatting.RED), true);
                    } else {
                        player.displayClientMessage(
                                Component.translatable("message.quantumflux.machine.no_energy_capability")
                                        .withStyle(ChatFormatting.RED), true);
                    }
                    level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE,
                            SoundSource.PLAYERS, 0.5f, 0.8f);
                }
            }
        }
        return true;
    }

    // ══════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════

    /** Resolves a clicked position to a pylon BOTTOM position, or null if not a pylon. */
    private static BlockPos resolvePylonPos(Level level, BlockPos clickedPos) {
        var clickedState = level.getBlockState(clickedPos);
        if (!(clickedState.getBlock() instanceof QuantumPylonBlock)) return null;

        if (clickedState.getValue(QuantumPylonBlock.HALF) == QuantumPylonBlock.PylonHalf.TOP) {
            return clickedPos.below();
        }
        return clickedPos;
    }

    private static String dimensionId(Level level) {
        return level.dimension().location().toString();
    }

    private static boolean canReach(Player player, BlockPos pos) {
        return player.isAlive() && !player.isSpectator()
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    private static void openGadgetScreen() {
        ClientScreenBridge.openGadgetScreen();
    }
}
