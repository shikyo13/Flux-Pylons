package com.zerotheabsolute.quantumflux.init;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public final class QFSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, QuantumFlux.MODID);

    public static final RegistryObject<SoundEvent> GADGET_ON = SOUNDS.register("gadget_on",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(QuantumFlux.MODID, "gadget_on")));

    public static final RegistryObject<SoundEvent> GADGET_OFF = SOUNDS.register("gadget_off",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(QuantumFlux.MODID, "gadget_off")));

    public static final RegistryObject<SoundEvent> PYLON_HUM = SOUNDS.register("pylon_hum",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(QuantumFlux.MODID, "pylon_hum")));

    private QFSounds() {}
}
