package com.zerotheabsolute.quantumflux.util;

/** Client beam geometry tiers with an exact per-beam vertex cost. */
public enum BeamRenderQuality {
    LOW(4),
    MEDIUM(8),
    HIGH(12);

    private final int verticesPerBeam;

    BeamRenderQuality(int verticesPerBeam) {
        this.verticesPerBeam = verticesPerBeam;
    }

    public int verticesPerBeam() {
        return verticesPerBeam;
    }

    public static BeamRenderQuality parse(String value) {
        if (value == null) return HIGH;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return HIGH;
        }
    }
}
