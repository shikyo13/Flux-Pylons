package com.zerotheabsolute.quantumflux.gametest;
import net.minecraft.gametest.framework.GameTestHelper;
import java.util.Objects;
public final class FixtureAssertions {
    private FixtureAssertions() {}
    public static void equal(GameTestHelper helper, Object actual, Object expected, String message) {
        helper.assertTrue(Objects.equals(actual, expected), message + ": expected " + expected + ", got " + actual);
    }
}
