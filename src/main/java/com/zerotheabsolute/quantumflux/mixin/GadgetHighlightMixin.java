package com.zerotheabsolute.quantumflux.mixin;

import com.zerotheabsolute.quantumflux.item.QuantumGadgetItem;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Gui.class)
abstract class GadgetHighlightMixin {
    @Redirect(method = "renderSelectedItemName", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getHoverName()Lnet/minecraft/network/chat/Component;"))
    private Component quantumflux$gadgetTip(ItemStack stack) {
        return stack.getItem() instanceof QuantumGadgetItem gadget ? gadget.getHighlightTip(stack, stack.getHoverName()) : stack.getHoverName();
    }
}
