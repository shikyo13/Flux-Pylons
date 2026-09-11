package com.zerotheabsolute.quantumflux.init;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.function.Supplier;
import com.zerotheabsolute.quantumflux.block.QuantumPylonBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class QFBlocks {

    private static final RegistryEntries<Block> BLOCKS = new RegistryEntries<>(BuiltInRegistries.BLOCK);

    public static final Supplier<QuantumPylonBlock> QUANTUM_PYLON = BLOCKS.register(
            "quantum_pylon",
            () -> new QuantumPylonBlock(
                    BlockBehaviour.Properties.of()
                            .strength(5.0f, 6.0f)
                            .lightLevel(s -> s.getValue(QuantumPylonBlock.ACTIVE) ? 15 : 0)
                            .sound(SoundType.AMETHYST)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()
            )
    );

    public static void register() {}
    private QFBlocks() {}
}
