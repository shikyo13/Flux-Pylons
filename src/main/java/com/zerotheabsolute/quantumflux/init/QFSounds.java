package com.zerotheabsolute.quantumflux.init;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class QFSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, QuantumFlux.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> GADGET_ON = SOUNDS.register("gadget_on",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(QuantumFlux.MODID, "gadget_on")));

    public static final DeferredHolder<SoundEvent, SoundEvent> GADGET_OFF = SOUNDS.register("gadget_off",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(QuantumFlux.MODID, "gadget_off")));

    public static final DeferredHolder<SoundEvent, SoundEvent> PYLON_HUM = SOUNDS.register("pylon_hum",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(QuantumFlux.MODID, "pylon_hum")));

    private QFSounds() {}
}
