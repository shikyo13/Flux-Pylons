package com.zerotheabsolute.quantumflux.init;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import com.zerotheabsolute.quantumflux.blockentity.QuantumPylonBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class QFBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, QuantumFlux.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuantumPylonBlockEntity>> QUANTUM_PYLON_BE =
            BLOCK_ENTITY_TYPES.register("quantum_pylon", () ->
                    BlockEntityType.Builder.of(QuantumPylonBlockEntity::new, QFBlocks.QUANTUM_PYLON.get())
                            .build(null));

    private QFBlockEntities() {}
}
