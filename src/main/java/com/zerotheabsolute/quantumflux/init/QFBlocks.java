package com.zerotheabsolute.quantumflux.init;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import com.zerotheabsolute.quantumflux.block.QuantumPylonBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class QFBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(QuantumFlux.MODID);

    public static final DeferredBlock<QuantumPylonBlock> QUANTUM_PYLON = BLOCKS.register(
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

    private QFBlocks() {}
}
