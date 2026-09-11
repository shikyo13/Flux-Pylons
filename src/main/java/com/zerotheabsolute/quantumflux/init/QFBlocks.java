package com.zerotheabsolute.quantumflux.init;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import com.zerotheabsolute.quantumflux.block.QuantumPylonBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public final class QFBlocks {

    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.BLOCKS, QuantumFlux.MODID);

    public static final RegistryObject<QuantumPylonBlock> QUANTUM_PYLON = BLOCKS.register(
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
