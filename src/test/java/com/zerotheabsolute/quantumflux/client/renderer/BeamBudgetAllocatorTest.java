package com.zerotheabsolute.quantumflux.client.renderer;

import com.zerotheabsolute.quantumflux.util.BeamRenderQuality;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Dependency-free deterministic regression checks, runnable with {@code java -ea}. */
public final class BeamBudgetAllocatorTest {

    public static void main(String[] args) {
        selectsNearestWithinExactVertexBudget();
        tieBreaksIndependentlyOfInputOrder();
        retainedBeamWinsOnlyNearTheCutoff();
        rejectsInvalidCandidatesAndDuplicateKeys();
        qualityCostsMatchRenderedGeometry();
    }

    private static void selectsNearestWithinExactVertexBudget() {
        List<BeamBudgetAllocator.Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < 10; index++) candidates.add(candidate(index, index * index, false));

        Set<BeamBudgetAllocator.BeamKey> selected = BeamBudgetAllocator.select(
                candidates, 25, 12, 0.96);
        assertEquals(Set.of(key(0), key(1)), selected, "nearest beams");
        if (selected.size() * 12 > 25) throw new AssertionError("Vertex budget exceeded");
    }

    private static void tieBreaksIndependentlyOfInputOrder() {
        List<BeamBudgetAllocator.Candidate> candidates = List.of(
                candidate(8, 10.0, false),
                candidate(3, 10.0, false),
                candidate(5, 10.0, false));
        Set<BeamBudgetAllocator.BeamKey> expected = Set.of(key(3), key(5));
        Random random = new Random(0xB34FL);

        for (int iteration = 0; iteration < 1_000; iteration++) {
            List<BeamBudgetAllocator.Candidate> shuffled = new ArrayList<>(candidates);
            Collections.shuffle(shuffled, random);
            assertEquals(expected, BeamBudgetAllocator.select(shuffled, 8, 4, 0.96),
                    "deterministic tie selection");
        }
    }

    private static void retainedBeamWinsOnlyNearTheCutoff() {
        BeamBudgetAllocator.Candidate retained = candidate(1, 100.0, true);
        BeamBudgetAllocator.Candidate slightlyNearer = candidate(2, 98.0, false);
        assertEquals(Set.of(key(1)), BeamBudgetAllocator.select(
                        List.of(retained, slightlyNearer), 4, 4, 0.96),
                "retention hysteresis");

        BeamBudgetAllocator.Candidate materiallyNearer = candidate(2, 90.0, false);
        assertEquals(Set.of(key(2)), BeamBudgetAllocator.select(
                        List.of(retained, materiallyNearer), 4, 4, 0.96),
                "nearest beam replaces stale selection");
    }

    private static void rejectsInvalidCandidatesAndDuplicateKeys() {
        List<BeamBudgetAllocator.Candidate> candidates = List.of(
                candidate(1, Double.NaN, false),
                candidate(2, -1.0, false),
                candidate(3, 3.0, false),
                candidate(3, 1.0, false));
        assertEquals(Set.of(key(3)), BeamBudgetAllocator.select(candidates, 16, 4, 0.96),
                "invalid and duplicate candidates");
        assertEquals(Set.of(), BeamBudgetAllocator.select(candidates, 3, 4, 0.96),
                "sub-beam vertex budget");
    }

    private static void qualityCostsMatchRenderedGeometry() {
        if (BeamRenderQuality.LOW.verticesPerBeam() != 4
                || BeamRenderQuality.MEDIUM.verticesPerBeam() != 8
                || BeamRenderQuality.HIGH.verticesPerBeam() != 12) {
            throw new AssertionError("Beam quality vertex costs changed");
        }
        if (BeamRenderQuality.parse("MEDIUM") != BeamRenderQuality.MEDIUM
                || BeamRenderQuality.parse("invalid") != BeamRenderQuality.HIGH) {
            throw new AssertionError("Beam quality parsing changed");
        }
    }

    private static BeamBudgetAllocator.Candidate candidate(long id, double distance, boolean retained) {
        return new BeamBudgetAllocator.Candidate(key(id), distance, retained);
    }

    private static BeamBudgetAllocator.BeamKey key(long id) {
        return new BeamBudgetAllocator.BeamKey(id, id + 10_000);
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + " but got " + actual);
        }
    }
}
