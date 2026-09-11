package com.zerotheabsolute.quantumflux.mixin;

import com.zerotheabsolute.quantumflux.blockentity.QuantumPylonBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Send rich pylon state after the player's initial chunk packet, including already-loaded chunks. */
@Mixin(PlayerChunkSender.class)
abstract class ChunkTrackingMixin {
    @Inject(method = "sendChunk", at = @At("TAIL"))
    private static void quantumflux$sendPylons(ServerGamePacketListenerImpl connection, ServerLevel level, LevelChunk chunk, CallbackInfo ci) {
        for (var blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof QuantumPylonBlockEntity pylon) pylon.sendSyncTo(connection.player);
        }
    }
}
