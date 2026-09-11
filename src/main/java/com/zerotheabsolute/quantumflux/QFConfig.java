package com.zerotheabsolute.quantumflux;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class QFConfig {

    // ── Server Config ──
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec.IntValue PYLON_BUFFER_SIZE;
    public static final ModConfigSpec.IntValue MAX_TRANSFER_PER_TICK;
    public static final ModConfigSpec.IntValue MAX_CONNECTIONS;
    public static final ModConfigSpec.IntValue DEFAULT_RANGE;
    public static final ModConfigSpec.IntValue TICK_INTERVAL;
    public static final ModConfigSpec.ConfigValue<String> DEFAULT_PRIORITY_MODE;
    public static final ModConfigSpec.ConfigValue<String> DEFAULT_BEAM_STYLE;
    public static final ModConfigSpec.IntValue DEFAULT_BEAM_COLOR;

    static {
        ModConfigSpec.Builder server = new ModConfigSpec.Builder();

        server.comment("Flux Pylon settings").push("pylon");
        PYLON_BUFFER_SIZE = server.comment("Internal energy buffer size (FE)")
                .defineInRange("pylonBufferSize", 100_000, 1_000, Integer.MAX_VALUE);
        MAX_TRANSFER_PER_TICK = server.comment(
                        "Maximum average FE per tick across all connections (cycle budget scales with tickInterval)")
                .defineInRange("maxTransferPerTick", 10_000, 100, Integer.MAX_VALUE);
        MAX_CONNECTIONS = server.comment("Maximum number of machines a single pylon can link to")
                .defineInRange("maxConnections", 20, 1, 128);
        DEFAULT_RANGE = server.comment("Default wireless range in blocks")
                .defineInRange("defaultRange", 16, 1, 256);
        TICK_INTERVAL = server.comment("Ticks between energy distribution cycles (1 = every tick)")
                .defineInRange("tickInterval", 1, 1, 20);
        DEFAULT_PRIORITY_MODE = server.comment("Default distribution mode: EQUAL, ROUND_ROBIN, or NEAREST_FIRST")
                .define("defaultPriorityMode", "EQUAL", QFConfig::isPriorityMode);
        DEFAULT_BEAM_STYLE = server.comment("Default beam style for newly placed pylons: SOLID, PULSE, or PARTICLE")
                .define("defaultBeamStyle", "SOLID", QFConfig::isBeamStyle);
        DEFAULT_BEAM_COLOR = server.comment("Default energy color for unassigned pylons as an RGB integer")
                .defineInRange("defaultBeamColor", 0x00FFFF, 0, 0xFFFFFF);
        server.pop();

        SERVER_SPEC = server.build();
    }

    // ── Client Config ──
    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec.BooleanValue RENDER_BEAMS_THROUGH_BLOCKS;
    public static final ModConfigSpec.IntValue MAX_BEAM_RENDER_DISTANCE;
    public static final ModConfigSpec.ConfigValue<String> BEAM_RENDER_QUALITY;
    public static final ModConfigSpec.IntValue MAX_BEAM_VERTICES_PER_FRAME;
    public static final ModConfigSpec.IntValue MAX_PARTICLES_PER_PYLON;
    public static final ModConfigSpec.BooleanValue SHOW_GADGET_OVERLAY;
    public static final ModConfigSpec.DoubleValue PYLON_HUM_VOLUME;

    static {
        ModConfigSpec.Builder client = new ModConfigSpec.Builder();

        client.comment("Beam rendering settings").push("beams");
        RENDER_BEAMS_THROUGH_BLOCKS = client.comment("Whether beams render through solid blocks")
                .define("renderBeamsThroughBlocks", false);
        MAX_BEAM_RENDER_DISTANCE = client.comment("Maximum distance (blocks) at which beams are visible")
                .defineInRange("maxBeamRenderDistance", 64, 8, 256);
        BEAM_RENDER_QUALITY = client.comment(
                        "Beam geometry quality: LOW (4 vertices), MEDIUM (8), or HIGH (12 per beam)")
                .define("beamRenderQuality", "HIGH", QFConfig::isBeamRenderQuality);
        MAX_BEAM_VERTICES_PER_FRAME = client.comment(
                        "Global connection-beam selection budget shared by all pylons each frame",
                        "The selected geometry is capped to this many vertices in each world render pass",
                        "At HIGH quality, the default permits up to 128 nearest beams; 0 disables connection beams")
                .defineInRange("maxBeamVerticesPerFrame", 1_536, 0, 49_152);
        MAX_PARTICLES_PER_PYLON = client.comment(
                        "Maximum beam particles emitted by one pylon per particle tick (0 disables beam particles)")
                .defineInRange("maxParticlesPerPylon", 4, 0, 32);
        client.pop();

        client.comment("HUD settings").push("hud");
        SHOW_GADGET_OVERLAY = client.comment("Show context overlay when holding the Flux Gadget")
                .define("showGadgetOverlay", true);
        client.pop();

        client.comment("Pylon sound settings").push("audio");
        PYLON_HUM_VOLUME = client.comment(
                        "Maximum pylon hum volume before Minecraft's Blocks volume; 0 mutes it",
                        "Powered idle pylons use 35 percent of this volume")
                .defineInRange("pylonHumVolume", 0.15, 0.0, 1.0);
        client.pop();

        CLIENT_SPEC = client.build();
    }

    private static boolean isPriorityMode(Object value) {
        if (!(value instanceof String name)) return false;
        try {
            com.zerotheabsolute.quantumflux.util.PriorityMode.valueOf(name);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isBeamStyle(Object value) {
        if (!(value instanceof String name)) return false;
        try {
            com.zerotheabsolute.quantumflux.util.BeamStyle.valueOf(name);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isBeamRenderQuality(Object value) {
        if (!(value instanceof String name)) return false;
        try {
            com.zerotheabsolute.quantumflux.util.BeamRenderQuality.valueOf(name);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private QFConfig() {}
}
