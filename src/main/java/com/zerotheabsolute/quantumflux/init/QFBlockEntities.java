package com.zerotheabsolute.quantumflux.init;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.function.Supplier;
import com.zerotheabsolute.quantumflux.blockentity.QuantumPylonBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class QFBlockEntities {

    private static final RegistryEntries<BlockEntityType<?>> BLOCK_ENTITY_TYPES = new RegistryEntries<>(BuiltInRegistries.BLOCK_ENTITY_TYPE);

    public static final Supplier<BlockEntityType<QuantumPylonBlockEntity>> QUANTUM_PYLON_BE =
            BLOCK_ENTITY_TYPES.register("quantum_pylon", () ->
                    BlockEntityType.Builder.of(QuantumPylonBlockEntity::new, QFBlocks.QUANTUM_PYLON.get())
                            .build(null));

    public static void register() {}
    private QFBlockEntities() {}
}
