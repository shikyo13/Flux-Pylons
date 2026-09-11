package com.zerotheabsolute.quantumflux.client.renderer;

import com.zerotheabsolute.quantumflux.QFConfig;
import com.zerotheabsolute.quantumflux.blockentity.QuantumPylonBlockEntity;
import com.zerotheabsolute.quantumflux.client.ClientDataCache;
import com.zerotheabsolute.quantumflux.network.PylonSyncPayload;
import com.zerotheabsolute.quantumflux.util.BeamRenderQuality;
import com.zerotheabsolute.quantumflux.util.BeamStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One global, render-order-independent beam selection for each client frame.
 * Selection is prepared lazily from the synchronized cache using the current camera.
 */
public final class BeamRenderFrameBudget {

    private static final double RETAINED_SCORE_MULTIPLIER = 0.96;

    private static long frameSequence;
    private static long preparedFrame = Long.MIN_VALUE;
    private static Set<BeamBudgetAllocator.BeamKey> selected = Set.of();

    private BeamRenderFrameBudget() {}

    /** Called exactly once by the NeoForge pre-frame event. */
    public static void beginFrame() {
        frameSequence++;
    }

    static void prepare(Vec3 cameraPosition, BeamRenderQuality quality) {
        if (preparedFrame == frameSequence) return;

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || cameraPosition == null) {
            selected = Set.of();
            preparedFrame = frameSequence;
            return;
        }

        int renderDistance = QFConfig.MAX_BEAM_RENDER_DISTANCE.get();
        double maximumDistanceSquared = (double) renderDistance * renderDistance;
        List<BeamBudgetAllocator.Candidate> candidates = new ArrayList<>();
        Set<BeamBudgetAllocator.BeamKey> lastSelection = selected;

        for (Map.Entry<BlockPos, ClientDataCache.PylonClientData> entry
                : ClientDataCache.getAll().entrySet()) {
            BlockPos source = entry.getKey();
            ClientDataCache.PylonClientData data = entry.getValue();
            if (data == null || !data.isPowered() || !data.outputEnabled || !data.beamsVisible
                    || data.beamStyle == BeamStyle.PARTICLE
                    || data.connections == null || data.connections.isEmpty()
                    || !level.hasChunkAt(source)
                    || !(level.getBlockEntity(source) instanceof QuantumPylonBlockEntity)) {
                continue;
            }

            double sourceX = source.getX() + 0.5;
            double sourceY = source.getY() + 1.62;
            double sourceZ = source.getZ() + 0.5;
            if (cameraPosition.distanceToSqr(sourceX, sourceY, sourceZ) > maximumDistanceSquared) {
                continue;
            }

            for (PylonSyncPayload.ConnectionEntry connection : data.connections) {
                BlockPos target = connection.pos();
                if (target == null || !connection.status().showsBeam() || !level.hasChunkAt(target)
                        || !isConnectionInRange(source, target, data.effectiveRange)) {
                    continue;
                }

                BeamBudgetAllocator.BeamKey key = new BeamBudgetAllocator.BeamKey(
                        source.asLong(), target.asLong());
                double distanceSquared = distanceToSegmentSquared(
                        cameraPosition,
                        sourceX, sourceY, sourceZ,
                        target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
                candidates.add(new BeamBudgetAllocator.Candidate(
                        key, distanceSquared, lastSelection.contains(key)));
            }
        }

        selected = BeamBudgetAllocator.select(
                candidates,
                QFConfig.MAX_BEAM_VERTICES_PER_FRAME.get(),
                quality.verticesPerBeam(),
                RETAINED_SCORE_MULTIPLIER);
        preparedFrame = frameSequence;
    }

    static boolean isSelected(BlockPos source, BlockPos target) {
        return selected.contains(new BeamBudgetAllocator.BeamKey(source.asLong(), target.asLong()));
    }

    static boolean isConnectionInRange(BlockPos source, BlockPos target, int effectiveRange) {
        long maximum = Math.max(8, Math.min(256, effectiveRange)) + 2L;
        long deltaX = (long) target.getX() - source.getX();
        long deltaY = (long) target.getY() - source.getY();
        long deltaZ = (long) target.getZ() - source.getZ();
        if (Math.abs(deltaX) > maximum || Math.abs(deltaY) > maximum || Math.abs(deltaZ) > maximum) {
            return false;
        }
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ <= maximum * maximum;
    }

    private static double distanceToSegmentSquared(Vec3 point,
                                                   double startX, double startY, double startZ,
                                                   double endX, double endY, double endZ) {
        double segmentX = endX - startX;
        double segmentY = endY - startY;
        double segmentZ = endZ - startZ;
        double lengthSquared = segmentX * segmentX + segmentY * segmentY + segmentZ * segmentZ;
        if (lengthSquared <= 1.0E-9) {
            return point.distanceToSqr(startX, startY, startZ);
        }

        double projection = ((point.x - startX) * segmentX
                + (point.y - startY) * segmentY
                + (point.z - startZ) * segmentZ) / lengthSquared;
        double clamped = Math.clamp(projection, 0.0, 1.0);
        return point.distanceToSqr(
                startX + segmentX * clamped,
                startY + segmentY * clamped,
                startZ + segmentZ * clamped);
    }
}
