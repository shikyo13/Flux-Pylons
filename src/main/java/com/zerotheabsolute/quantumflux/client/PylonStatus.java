package com.zerotheabsolute.quantumflux.client;

import net.minecraft.network.chat.Component;

/** Player-facing states derived only from synchronized readings. */
public enum PylonStatus {
    PAUSED("screen.quantumflux.flow.paused", "screen.quantumflux.flow.paused_hint", 0xFFFFD166),
    TRANSFERRING("screen.quantumflux.flow.transferring", "screen.quantumflux.flow.transferring_hint", 0xFF5EE68A),
    NETWORK_BUFFER("screen.quantumflux.flow.network_buffer", "screen.quantumflux.flow.network_buffer_hint", 0xFF96A2B3),
    UNLINKED("screen.quantumflux.flow.unlinked", "screen.quantumflux.flow.unlinked_hint", 0xFFFFD166),
    NO_POWER("screen.quantumflux.flow.no_power", "screen.quantumflux.flow.no_power_hint", 0xFFFFD166),
    STANDBY("screen.quantumflux.flow.standby", "screen.quantumflux.flow.standby_hint", 0xFF96A2B3);

    private final String labelKey;
    private final String hintKey;
    private final int color;

    PylonStatus(String labelKey, String hintKey, int color) {
        this.labelKey = labelKey;
        this.hintKey = hintKey;
        this.color = color;
    }

    public static PylonStatus of(long energy, double throughput, int connections) {
        if (connections == 0) return UNLINKED;
        if (throughput > 0) return TRANSFERRING;
        return energy > 0 ? STANDBY : NO_POWER;
    }

    public static PylonStatus of(ClientDataCache.PylonClientData data) {
        if (!data.outputEnabled) return PAUSED;
        if (data.networkId != null && data.connections.isEmpty()) return NETWORK_BUFFER;
        return of(data.availableEnergy, data.throughput, data.connections.size());
    }

    public Component label() { return Component.translatable(labelKey); }
    public Component hint() { return Component.translatable(hintKey); }
    public int color() { return color; }
}
