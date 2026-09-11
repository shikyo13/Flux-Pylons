package com.zerotheabsolute.quantumflux.init;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Supplier;

final class RegistryEntries<T> {
    private final Registry<T> registry;
    RegistryEntries(Registry<T> registry) { this.registry = registry; }
    <V extends T> Supplier<V> register(String name, Supplier<V> factory) {
        V value = Registry.register(registry, ResourceLocation.fromNamespaceAndPath(QuantumFlux.MODID, name), factory.get());
        return () -> value;
    }
}
