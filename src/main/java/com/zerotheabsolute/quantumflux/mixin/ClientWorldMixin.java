package com.zerotheabsolute.quantumflux.mixin;

import com.zerotheabsolute.quantumflux.client.ClientEventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class ClientWorldMixin {
    @Inject(method = "setLevel", at = @At("HEAD"))
    private void quantumflux$clearOldWorld(ClientLevel level, CallbackInfo ci) {
        ClientEventHandler.GameBusEvents.onLevelUnload();
    }
}
