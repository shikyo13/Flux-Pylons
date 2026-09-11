package com.zerotheabsolute.quantumflux.gametest;

public final class FabricTestBootstrap implements net.fabricmc.api.ModInitializer {
    @Override public void onInitialize() { EnergyReceiverFixture.register(); }
}
