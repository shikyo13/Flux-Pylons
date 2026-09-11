package com.zerotheabsolute.quantumflux.event;

import com.zerotheabsolute.quantumflux.QFConfig;
import com.zerotheabsolute.quantumflux.block.QuantumPylonBlock;
import com.zerotheabsolute.quantumflux.init.QFDataComponents;
import com.zerotheabsolute.quantumflux.item.QuantumGadgetItem;
import com.zerotheabsolute.quantumflux.network.QFNetworking;
import com.zerotheabsolute.quantumflux.network.data.QuantumFluxNetworkManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

public final class GadgetInteractionHandler {
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            QFNetworking.flushNetworkListBroadcasts();
            QFNetworking.refreshActiveGadgetNetworkLists(server);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            clearInvalidGadgetDimensionState(player, false);
            QFNetworking.sendNetworkListToPlayer(player, QuantumFluxNetworkManager.get(player.serverLevel()));
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            clearInvalidGadgetDimensionState(player, true);
            QFNetworking.sendNetworkListToPlayer(player, QuantumFluxNetworkManager.get(destination));
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (player.isSpectator() || !(stack.getItem() instanceof QuantumGadgetItem)
                    || !Boolean.TRUE.equals(QFDataComponents.GADGET_ACTIVE.get(stack))) return InteractionResult.PASS;
            var linking = QFDataComponents.LINKING_DATA.get(stack);
            if (linking == null || !linking.active() || level.getBlockState(hit.getBlockPos()).getBlock() instanceof QuantumPylonBlock) return InteractionResult.PASS;
            return QuantumGadgetItem.handleLinkInteraction(player, level, hit.getBlockPos(), stack)
                    ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
        });
    }

    private static void clearInvalidGadgetDimensionState(ServerPlayer player, boolean clearSelection) {
        String currentDimension = player.serverLevel().dimension().location().toString();
        boolean changed = false;

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof QuantumGadgetItem)) continue;

            QFDataComponents.LinkingData linking = QFDataComponents.LINKING_DATA.get(stack);
            if (linking != null && !currentDimension.equals(linking.dimension())) {
                QFDataComponents.LINKING_DATA.remove(stack);
                changed = true;
            }

            String selectedDimension = QFDataComponents.SELECTED_NETWORK_DIMENSION.get(stack);
            boolean hasSelection = QFDataComponents.SELECTED_NETWORK.get(stack) != null;
            if ((clearSelection && hasSelection)
                    || (hasSelection && !currentDimension.equals(selectedDimension))) {
                QFDataComponents.SELECTED_NETWORK.remove(stack);
                QFDataComponents.SELECTED_NETWORK_DIMENSION.remove(stack);
                QFDataComponents.GADGET_COLOR.set(stack, QFConfig.DEFAULT_BEAM_COLOR.get() & 0xFFFFFF);
                changed = true;
            }
        }

        if (changed) player.inventoryMenu.broadcastChanges();
    }
}
