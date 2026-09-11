package com.zerotheabsolute.quantumflux.network;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.network.CustomPayloadEvent;

public record ForgePayloadContext(CustomPayloadEvent.Context context) {
    public Player player() { return context.getSender(); }
    public void enqueueWork(Runnable action) { context.enqueueWork(action); }
}
