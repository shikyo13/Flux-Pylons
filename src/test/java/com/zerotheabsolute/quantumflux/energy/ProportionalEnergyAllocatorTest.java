package com.zerotheabsolute.quantumflux.energy;

import java.util.Arrays;
import java.util.Random;

/** Dependency-free focused regression checks, runnable with {@code java -ea}. */
public final class ProportionalEnergyAllocatorTest {

    public static void main(String[] args) {
        assertAllocation(2, new int[]{10, 10, 10}, new int[]{1, 1, 0});
        assertAllocation(101, new int[]{1, 1, 100}, new int[]{1, 1, 99});
        assertAllocation(60, new int[]{10, 20, 30}, new int[]{10, 20, 30});
        assertAllocation(1_000, new int[]{10, 20, 30}, new int[]{10, 20, 30});
        assertAllocation(50, new int[]{-10, 0, 100}, new int[]{0, 0, 50});
        assertAllocation(-1, new int[]{10, 20}, new int[]{0, 0});
        assertAllocation((long) Integer.MAX_VALUE * 2,
                new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE},
                new int[]{1_431_655_765, 1_431_655_765, 1_431_655_764});
        assertAllocation((long) Integer.MAX_VALUE * 3,
                new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE},
                new int[]{1_610_612_736, 1_610_612_735, 1_610_612_735, 1_610_612_735});
        assertRandomConservation();
    }

    private static void assertRandomConservation() {
        Random random = new Random(0x51A7E5L);
        for (int iteration = 0; iteration < 10_000; iteration++) {
            int[] capacities = new int[1 + random.nextInt(64)];
            long totalCapacity = 0;
            for (int i = 0; i < capacities.length; i++) {
                capacities[i] = random.nextInt(Integer.MAX_VALUE);
                totalCapacity += capacities[i];
            }

            long requested = random.nextLong(Math.max(1, totalCapacity + 1));
            int[] allocation = ProportionalEnergyAllocator.allocate(requested, capacities);
            long allocated = 0;
            for (int i = 0; i < allocation.length; i++) {
                if (allocation[i] < 0 || allocation[i] > capacities[i]) {
                    throw new AssertionError("Allocation outside capacity at index " + i);
                }
                allocated += allocation[i];
            }
            if (allocated != requested) {
                throw new AssertionError("Expected randomized total " + requested + " but got " + allocated);
            }
        }
    }

    private static void assertAllocation(long energy, int[] capacities, int[] expected) {
        int[] actual = ProportionalEnergyAllocator.allocate(energy, capacities);
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError("Expected " + Arrays.toString(expected)
                    + " but got " + Arrays.toString(actual));
        }

        long expectedTotal = Math.min(Math.max(0, energy), Arrays.stream(capacities)
                .mapToLong(capacity -> Math.max(0, capacity))
                .sum());
        long actualTotal = Arrays.stream(actual).mapToLong(value -> value).sum();
        if (expectedTotal != actualTotal) {
            throw new AssertionError("Expected total " + expectedTotal + " but got " + actualTotal);
        }
    }
}
