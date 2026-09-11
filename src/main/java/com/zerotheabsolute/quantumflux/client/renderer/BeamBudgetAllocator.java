package com.zerotheabsolute.quantumflux.client.renderer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** Deterministic nearest-first selection for a fixed per-frame vertex budget. */
public final class BeamBudgetAllocator {

    private BeamBudgetAllocator() {}

    public record BeamKey(long source, long target) {}

    public record Candidate(BeamKey key, double distanceSquared, boolean retained) {}

    public static Set<BeamKey> select(Collection<Candidate> candidates,
                                      int vertexBudget,
                                      int verticesPerBeam,
                                      double retainedScoreMultiplier) {
        if (candidates == null || candidates.isEmpty()
                || vertexBudget <= 0 || verticesPerBeam <= 0) {
            return Set.of();
        }

        int maximumBeams = vertexBudget / verticesPerBeam;
        if (maximumBeams <= 0) return Set.of();
        double retention = Math.clamp(retainedScoreMultiplier, 0.0, 1.0);

        Comparator<Candidate> bestFirst = Comparator
                .comparingDouble((Candidate candidate) -> score(candidate, retention))
                .thenComparingLong(candidate -> candidate.key().source())
                .thenComparingLong(candidate -> candidate.key().target());
        Map<BeamKey, Candidate> unique = new HashMap<>();
        for (Candidate candidate : candidates) {
            if (candidate == null || candidate.key() == null
                    || !Double.isFinite(candidate.distanceSquared())
                    || candidate.distanceSquared() < 0.0) {
                continue;
            }
            Candidate existing = unique.get(candidate.key());
            if (existing == null || bestFirst.compare(candidate, existing) < 0) {
                unique.put(candidate.key(), candidate);
            }
        }

        PriorityQueue<Candidate> winners = new PriorityQueue<>(
                Math.max(1, Math.min(maximumBeams, unique.size())), bestFirst.reversed());

        for (Candidate candidate : unique.values()) {
            if (winners.size() < maximumBeams) {
                winners.offer(candidate);
            } else if (bestFirst.compare(candidate, winners.peek()) < 0) {
                winners.poll();
                winners.offer(candidate);
            }
        }

        List<Candidate> ordered = new ArrayList<>(winners);
        ordered.sort(bestFirst);
        Set<BeamKey> result = new HashSet<>(ordered.size());
        for (Candidate candidate : ordered) result.add(candidate.key());
        return Set.copyOf(result);
    }

    private static double score(Candidate candidate, double retainedScoreMultiplier) {
        return candidate.distanceSquared() * (candidate.retained() ? retainedScoreMultiplier : 1.0);
    }
}
