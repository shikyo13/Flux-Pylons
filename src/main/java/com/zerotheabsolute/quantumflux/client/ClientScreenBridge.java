package com.zerotheabsolute.quantumflux.client;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;

/**
 * Distribution-neutral bridge for client UI actions initiated by common item code.
 *
 * <p>This class intentionally contains no references to Minecraft client classes. The
 * client-only lifecycle subscriber installs the real implementation after client setup,
 * which keeps common item registration safe on a dedicated server.</p>
 */
public final class ClientScreenBridge {

    private static volatile Consumer<BlockPos> gadgetScreenOpener = ignored -> {};

    private ClientScreenBridge() {}

    public static void installGadgetScreenOpener(Consumer<BlockPos> opener) {
        gadgetScreenOpener = Objects.requireNonNull(opener, "opener");
    }

    public static void openGadgetScreen() {
        gadgetScreenOpener.accept(null);
    }

    public static void openPylonScreen(BlockPos pos) { gadgetScreenOpener.accept(pos); }
}
