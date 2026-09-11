package com.zerotheabsolute.quantumflux.init;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public final class QFSounds {

    private static final RegistryEntries<SoundEvent> SOUNDS = new RegistryEntries<>(BuiltInRegistries.SOUND_EVENT);

    public static final Supplier<SoundEvent> GADGET_ON = SOUNDS.register("gadget_on",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(QuantumFlux.MODID, "gadget_on")));

    public static final Supplier<SoundEvent> GADGET_OFF = SOUNDS.register("gadget_off",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(QuantumFlux.MODID, "gadget_off")));

    public static final Supplier<SoundEvent> PYLON_HUM = SOUNDS.register("pylon_hum",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(QuantumFlux.MODID, "pylon_hum")));

    public static void register() {}
    private QFSounds() {}
}
