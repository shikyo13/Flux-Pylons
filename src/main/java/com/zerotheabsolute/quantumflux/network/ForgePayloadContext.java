package com.zerotheabsolute.quantumflux.network;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

public record ForgePayloadContext(NetworkEvent.Context context) {
    public Player player() { return context.getSender(); }
    public void enqueueWork(Runnable action) { context.enqueueWork(action); }
}
