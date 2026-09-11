package com.zerotheabsolute.quantumflux.energy;

import java.math.BigInteger;
import java.util.Arrays;

/** Pure, deterministic proportional allocation with exact integer conservation. */
public final class ProportionalEnergyAllocator {

    private ProportionalEnergyAllocator() {}

    /**
     * Allocates energy in proportion to capacity using the largest-remainder method.
     * Negative capacities are treated as zero and energy is clamped to aggregate capacity.
     */
    public static int[] allocate(long requestedEnergy, int[] capacities) {
        int[] allocation = new int[capacities.length];
        if (requestedEnergy <= 0 || capacities.length == 0) return allocation;

        long totalCapacity = 0;
        for (int capacity : capacities) {
            totalCapacity = Math.addExact(totalCapacity, Math.max(0, (long) capacity));
        }
        if (totalCapacity == 0) return allocation;

        long energy = Math.min(requestedEnergy, totalCapacity);
        if (energy == totalCapacity) {
            for (int i = 0; i < capacities.length; i++) {
                allocation[i] = Math.max(0, capacities[i]);
            }
            return allocation;
        }

        long[] remainders = new long[capacities.length];
        long allocated = 0;
        for (int i = 0; i < capacities.length; i++) {
            int capacity = Math.max(0, capacities[i]);
            if (capacity == 0) continue;

            QuotientRemainder result = multiplyDivide(energy, capacity, totalCapacity);
            allocation[i] = Math.toIntExact(result.quotient());
            remainders[i] = result.remainder();
            allocated += result.quotient();
        }

        int unitsRemaining = Math.toIntExact(energy - allocated);
        Integer[] indices = new Integer[capacities.length];
        for (int i = 0; i < indices.length; i++) indices[i] = i;
        Arrays.sort(indices, (left, right) -> {
            int byRemainder = Long.compare(remainders[right], remainders[left]);
            return byRemainder != 0 ? byRemainder : Integer.compare(left, right);
        });

        for (int index : indices) {
            if (unitsRemaining == 0) break;
            if (allocation[index] < Math.max(0, capacities[index])) {
                allocation[index]++;
                unitsRemaining--;
            }
        }

        if (unitsRemaining != 0) {
            throw new IllegalStateException("Unable to conserve proportional energy allocation");
        }
        return allocation;
    }

    private static QuotientRemainder multiplyDivide(long value, int multiplier, long divisor) {
        try {
            long product = Math.multiplyExact(value, (long) multiplier);
            return new QuotientRemainder(product / divisor, product % divisor);
        } catch (ArithmeticException overflow) {
            BigInteger[] result = BigInteger.valueOf(value)
                    .multiply(BigInteger.valueOf(multiplier))
                    .divideAndRemainder(BigInteger.valueOf(divisor));
            return new QuotientRemainder(result[0].longValueExact(), result[1].longValueExact());
        }
    }

    private record QuotientRemainder(long quotient, long remainder) {}
}
