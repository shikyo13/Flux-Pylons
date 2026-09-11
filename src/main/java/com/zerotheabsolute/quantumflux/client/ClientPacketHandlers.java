package com.zerotheabsolute.quantumflux.client;

import com.zerotheabsolute.quantumflux.blockentity.QuantumPylonBlockEntity;
import com.zerotheabsolute.quantumflux.network.NetworkListSyncS2CPayload;
import com.zerotheabsolute.quantumflux.network.ActionResultS2CPayload;
import com.zerotheabsolute.quantumflux.network.PylonSyncPayload;
import com.zerotheabsolute.quantumflux.network.PylonTelemetryPayload;
import com.zerotheabsolute.quantumflux.network.NetworkTelemetryPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Client-only packet handlers. Separated from QFNetworking to avoid
 * classloading net.minecraft.client.Minecraft on dedicated servers.
 */
public final class ClientPacketHandlers {

    private ClientPacketHandlers() {}

    public static void handlePylonSync(PylonSyncPayload payload) {
        long gameTime = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getGameTime() : 0L;

        // Detect beam color change before updating cache
        ClientDataCache.PylonClientData existing = ClientDataCache.get(payload.pos());
        int oldColor = existing != null ? existing.beamColor : payload.beamColor();

        ClientDataCache.receive(payload, gameTime);

        // Keep the client BE tint source aligned even on the first custom payload.
        var mc = Minecraft.getInstance();
        boolean blockEntityChanged = false;
        if (mc.level != null
                && mc.level.getBlockEntity(payload.pos()) instanceof QuantumPylonBlockEntity pylon
                && pylon.getBeamColor() != payload.beamColor()) {
            pylon.setBeamColor(payload.beamColor());
            blockEntityChanged = true;
        }
        if (blockEntityChanged || oldColor != payload.beamColor()) {
            markBlockForRerender(payload.pos());
            markBlockForRerender(payload.pos().above());
        }
    }

    public static void handleNetworkListSync(NetworkListSyncS2CPayload payload) {
        ClientNetworkCache.receive(payload.networks());
    }

    public static void handleNetworkTelemetry(NetworkTelemetryPayload payload) {
        var level = Minecraft.getInstance().level;
        ClientNetworkCache.receiveTelemetry(payload, level == null ? 0 : level.getGameTime());
    }

    public static void handlePylonTelemetry(PylonTelemetryPayload payload) {
        long gameTime = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getGameTime() : 0L;
        ClientDataCache.receiveTelemetry(payload, gameTime);
    }

    public static void handleActionResult(ActionResultS2CPayload payload) {
        var screen = Minecraft.getInstance().screen;
        if (screen instanceof com.zerotheabsolute.quantumflux.client.screen.GadgetScreen gadgetScreen) {
            gadgetScreen.handleActionResult(payload);
        }
    }

    /** Mark the chunk section containing this block as needing a re-render. */
    public static void markBlockForRerender(BlockPos pos) {
        var mc = Minecraft.getInstance();
        if (mc.levelRenderer != null) {
            mc.levelRenderer.setSectionDirty(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        }
    }
}
