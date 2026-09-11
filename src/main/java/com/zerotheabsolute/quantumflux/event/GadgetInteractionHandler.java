package com.zerotheabsolute.quantumflux.event;

import com.zerotheabsolute.quantumflux.QFConfig;
import com.zerotheabsolute.quantumflux.block.QuantumPylonBlock;
import com.zerotheabsolute.quantumflux.init.QFDataComponents;
import com.zerotheabsolute.quantumflux.item.QuantumGadgetItem;
import com.zerotheabsolute.quantumflux.network.QFNetworking;
import com.zerotheabsolute.quantumflux.network.data.QuantumFluxNetworkManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.TickEvent;

public class GadgetInteractionHandler {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        QFNetworking.flushNetworkListBroadcasts();
        QFNetworking.refreshActiveGadgetNetworkLists(event.getServer());
    }

    @SubscribeEvent
    public static void onChunkSent(ChunkWatchEvent.Watch event) {
        for (var blockEntity : event.getLevel().getChunk(event.getPos().x, event.getPos().z).getBlockEntities().values()) {
            if (blockEntity instanceof com.zerotheabsolute.quantumflux.blockentity.QuantumPylonBlockEntity pylon) {
                pylon.sendSyncTo(event.getPlayer());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel serverLevel) {
            clearInvalidGadgetDimensionState(sp, false);
            QuantumFluxNetworkManager manager = QuantumFluxNetworkManager.get(serverLevel);
            QFNetworking.sendNetworkListToPlayer(sp, manager);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel serverLevel) {
            clearInvalidGadgetDimensionState(sp, true);
            QFNetworking.sendNetworkListToPlayer(sp, QuantumFluxNetworkManager.get(serverLevel));
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof QuantumGadgetItem)) return;

        // Gadget must be active for linking interactions
        if (!Boolean.TRUE.equals(QFDataComponents.GADGET_ACTIVE.get(stack))) return;

        QFDataComponents.LinkingData linkData = QFDataComponents.LINKING_DATA.get(stack);
        if (linkData == null || !linkData.active()) return;

        // Don't intercept clicks on our own pylon — useOn handles those
        if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof QuantumPylonBlock) return;

        // Intercept before the target block opens its GUI
        if (QuantumGadgetItem.handleLinkInteraction(
                event.getEntity(), event.getLevel(), event.getPos(), stack)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        }
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
