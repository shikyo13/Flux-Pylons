package com.zerotheabsolute.quantumflux.mixin;

import com.zerotheabsolute.quantumflux.blockentity.QuantumPylonBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Send pylon state after the initial chunk packet so a late joiner can render its links. */
@Mixin(ServerPlayer.class)
abstract class ChunkTrackingMixin {
    @Inject(method = "trackChunk", at = @At("TAIL"))
    private void quantumflux$sendPylons(ChunkPos position, Packet<?> packet, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        var chunk = player.serverLevel().getChunkSource().getChunkNow(position.x, position.z);
        if (chunk == null) return;
        for (var blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof QuantumPylonBlockEntity pylon) pylon.sendSyncTo(player);
        }
    }
}
