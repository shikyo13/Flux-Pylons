package com.zerotheabsolute.quantumflux.energy;

import java.util.Arrays;

public final class FairEnergyDistributorTest {
    public static void main(String[] args) {
        scarcePowerRotates();
        blockedReceiverDoesNotBiasTrickle();
        rejectedSharesRespectCycleLimits();
        nativeIncrementsUsePooledRemainders();
        sharedPoolCanExceedIntegerRange();
    }

    private static void scarcePowerRotates() {
        int[] received = new int[3];
        int cursor = 0;
        for (int input : new int[]{1, 1, 1, 5, 5, 5}) {
            var result = FairEnergyDistributor.distribute(input, new int[]{100, 100, 100}, cursor,
                    (index, offer) -> { received[index] += offer; return offer; });
            require(result.transferred() == input, "Every FE must reach a recipient");
            cursor = result.nextIndex();
            int minimum = Arrays.stream(received).min().orElseThrow();
            int maximum = Arrays.stream(received).max().orElseThrow();
            require(maximum - minimum <= 1, "Indivisible shares must rotate across pylons");
        }
        require(Arrays.equals(received, new int[]{6, 6, 6}), "Expected equal cumulative shares");
    }

    private static void blockedReceiverDoesNotBiasTrickle() {
        int[] received = new int[3];
        int cursor = 0;
        for (int cycle = 0; cycle < 12; cycle++) {
            var result = FairEnergyDistributor.distribute(1, new int[]{100, 100, 100}, cursor,
                    (index, offer) -> { if (index == 0) return 0; received[index] += offer; return offer; });
            require(result.transferred() == 1, "Blocked first recipient must not strand power");
            require(Math.abs(received[1] - received[2]) <= 1, "Blocked targets must not bias the rotation");
            cursor = result.nextIndex();
        }
    }

    private static void rejectedSharesRespectCycleLimits() {
        int[] received = new int[3];
        int[] limits = {3, 7, 100};
        var result = FairEnergyDistributor.distribute(200, limits, 0, (index, offer) -> {
            if (index == 1) return 0;
            received[index] += offer;
            require(received[index] <= limits[index], "Redistribution exceeded a pylon's cycle budget");
            return offer;
        });
        require(result.transferred() == 103, "Unused FE must remain in the shared pool");
        require(Arrays.equals(received, new int[]{3, 0, 100}), "Accepting pylons should use their remaining limits");
    }

    private static void nativeIncrementsUsePooledRemainders() {
        int[] received = new int[2];
        int cursor = 0;
        for (int cycle = 0; cycle < 6; cycle++) {
            var result = FairEnergyDistributor.distribute(2, new int[]{100, 100}, cursor, (index, offer) -> {
                int accepted = offer - offer % 2;
                received[index] += accepted;
                return accepted;
            });
            require(result.transferred() == 2, "Two rejected single-FE shares must combine");
            require(Math.abs(received[0] - received[1]) <= 2, "Native-increment remainders must rotate");
            cursor = result.nextIndex();
        }
        require(Arrays.equals(received, new int[]{6, 6}), "Native-increment delivery must remain fair");
        require(FairEnergyDistributor.distribute(1, new int[]{100, 100}, 0,
                (index, offer) -> offer - offer % 2).transferred() == 0, "An unusable FE must remain buffered");
    }

    private static void sharedPoolCanExceedIntegerRange() {
        long available = 2L * Integer.MAX_VALUE;
        long[] received = new long[2];
        var result = FairEnergyDistributor.distribute(available,
                new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}, 0,
                (index, offer) -> { received[index] += offer; return offer; });
        require(result.transferred() == available, "Large shared pools must conserve all FE");
        require(received[0] == Integer.MAX_VALUE && received[1] == Integer.MAX_VALUE,
                "Each endpoint must retain its integer cycle limit");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
