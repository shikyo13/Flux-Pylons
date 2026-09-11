package com.zerotheabsolute.quantumflux.energy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;

/** Fair delivery from a shared pool, with independent per-recipient cycle limits. */
public final class FairEnergyDistributor {
    public record Result(long transferred, int nextIndex) {}

    private FairEnergyDistributor() {}

    /** The sender returns only the E actually accepted by the indexed recipient. */
    public static Result distribute(long available, int[] limits, int cursor, IntBinaryOperator sender) {
        if (limits.length == 0 || available <= 0) return new Result(0, cursor);
        int count = limits.length;
        int start = Math.floorMod(cursor, count);
        int next = start;
        int[] delivered = new int[count];
        List<Integer> accepting = new ArrayList<>(count);
        for (int offset = 0; offset < count; offset++) {
            int index = (start + offset) % count;
            if (limits[index] > 0) accepting.add(index);
        }
        long remaining = available;

        while (remaining > 0 && !accepting.isEmpty()) {
            long share = remaining / accepting.size();
            long remainder = remaining % accepting.size();
            long sentThisPass = 0;
            List<Integer> stillAccepting = new ArrayList<>(accepting.size());
            for (int slot = 0; slot < accepting.size(); slot++) {
                int index = accepting.get(slot);
                int offer = (int) Math.min(limits[index] - delivered[index],
                        share + (slot < remainder ? 1 : 0));
                int sent = offer == 0 ? 0 : Math.clamp(sender.applyAsInt(index, offer), 0, offer);
                delivered[index] += sent;
                remaining -= sent;
                sentThisPass += sent;
                if (sent > 0 && slot < remainder) next = (index + 1) % count;
                if (sent == offer && delivered[index] < limits[index]) stillAccepting.add(index);
            }
            if (sentThisPass == 0 && stillAccepting.size() == accepting.size()) break;
            accepting = stillAccepting;
        }

        // Native-energy adapters can reject all of the smaller fair shares.
        // Pool their remainder, while retaining the same per-cycle limits.
        for (int offset = 0; remaining > 0 && offset < count; offset++) {
            int index = (start + offset) % count;
            int offer = (int) Math.min(remaining, Math.max(0, limits[index] - delivered[index]));
            if (offer == 0) continue;
            int sent = Math.clamp(sender.applyAsInt(index, offer), 0, offer);
            delivered[index] += sent;
            remaining -= sent;
            if (sent > 0) next = (index + 1) % count;
        }
        return new Result(available - remaining, next);
    }
}
