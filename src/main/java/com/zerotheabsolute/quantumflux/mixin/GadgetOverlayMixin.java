package com.zerotheabsolute.quantumflux.mixin;

import com.zerotheabsolute.quantumflux.client.ClientEventHandler;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Gui.class, remap = false)
abstract class GadgetOverlayMixin {
    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void quantumflux$renderReadout(GuiGraphics graphics, DeltaTracker ticks, CallbackInfo ci) {
        ClientEventHandler.GameBusEvents.onRenderOverlay(graphics);
    }
}
