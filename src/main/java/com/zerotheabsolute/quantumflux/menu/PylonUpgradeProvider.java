package com.zerotheabsolute.quantumflux.menu;

import com.zerotheabsolute.quantumflux.blockentity.QuantumPylonBlockEntity;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

public record PylonUpgradeProvider(QuantumPylonBlockEntity pylon) implements ExtendedScreenHandlerFactory {
    @Override public void writeScreenOpeningData(ServerPlayer player, net.minecraft.network.FriendlyByteBuf buffer) { buffer.writeBlockPos(pylon.getBlockPos()); }
    @Override public Component getDisplayName() { return Component.translatable("screen.quantumflux.upgrades.title"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new PylonUpgradeMenu(id, inventory, pylon); }
}
