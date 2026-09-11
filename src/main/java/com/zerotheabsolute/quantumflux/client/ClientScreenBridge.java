package com.zerotheabsolute.quantumflux.client;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.world.phys.AABB;
import com.zerotheabsolute.quantumflux.blockentity.QuantumPylonBlockEntity;
import net.minecraft.core.BlockPos;

/**
 * Distribution-neutral bridge for client UI actions and pylon render bounds.
 *
 * <p>This class intentionally contains no references to Minecraft client classes. The
 * client-only lifecycle subscriber installs the real implementation after client setup,
 * which keeps common item registration safe on a dedicated server.</p>
 */
public final class ClientScreenBridge {

    private static volatile Consumer<BlockPos> gadgetScreenOpener = ignored -> {};

    private static volatile Function<QuantumPylonBlockEntity, AABB> renderBounds =
            pylon -> new AABB(pylon.getBlockPos()).expandTowards(0, 1, 0);

    public static void installRenderBounds(Function<QuantumPylonBlockEntity, AABB> provider) {
        renderBounds = Objects.requireNonNull(provider, "provider");
    }

    public static AABB renderBounds(QuantumPylonBlockEntity pylon) {
        return renderBounds.apply(pylon);
    }

    private ClientScreenBridge() {}

    public static void installGadgetScreenOpener(Consumer<BlockPos> opener) {
        gadgetScreenOpener = Objects.requireNonNull(opener, "opener");
    }

    public static void openGadgetScreen() {
        gadgetScreenOpener.accept(null);
    }

    public static void openPylonScreen(BlockPos pos) { gadgetScreenOpener.accept(pos); }
}
