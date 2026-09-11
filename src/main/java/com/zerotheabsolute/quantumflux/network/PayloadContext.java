package com.zerotheabsolute.quantumflux.network;

import net.minecraft.world.entity.player.Player;
import java.util.concurrent.Executor;

record PayloadContext(Player player, Executor executor) {
    void enqueueWork(Runnable task) { executor.execute(task); }
}
