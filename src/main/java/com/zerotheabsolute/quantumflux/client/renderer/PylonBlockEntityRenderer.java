package com.zerotheabsolute.quantumflux.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zerotheabsolute.quantumflux.QFConfig;
import com.zerotheabsolute.quantumflux.QuantumFlux;
import com.zerotheabsolute.quantumflux.blockentity.QuantumPylonBlockEntity;
import com.zerotheabsolute.quantumflux.client.ClientDataCache;
import com.zerotheabsolute.quantumflux.network.PylonSyncPayload;
import com.zerotheabsolute.quantumflux.util.BeamRenderQuality;
import com.zerotheabsolute.quantumflux.util.BeamStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Renders bounded, client-configurable pylon energy effects and connection beams. */
public final class PylonBlockEntityRenderer implements BlockEntityRenderer<QuantumPylonBlockEntity> {

    private static final ResourceLocation BEAM_CORE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(QuantumFlux.MODID, "textures/misc/beam_core.png");
    private static final ResourceLocation BEAM_GLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(QuantumFlux.MODID, "textures/misc/beam_glow.png");
    private static final ResourceLocation ENERGY_ORB_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(QuantumFlux.MODID, "textures/misc/energy_orb.png");
    private static final ResourceLocation ENERGY_RING_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(QuantumFlux.MODID, "textures/misc/energy_ring.png");

    private static final int FULLBRIGHT = 0xF000F0;
    private static final float CORE_X = 0.5f;
    private static final float CORE_Y = 1.62f;
    private static final float CORE_Z = 0.5f;
    private static final float BEAM_CORE_WIDTH = 0.020f;
    private static final float BEAM_GLOW_WIDTH = 0.072f;
    private static final float[] RING_INCLINATIONS = {26.0f, -54.0f};
    private static final float[] RING_SPEEDS = {0.017f, -0.012f};

    public PylonBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(QuantumPylonBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ClientDataCache.PylonClientData data = ClientDataCache.get(blockEntity.getBlockPos());
        Level level = blockEntity.getLevel();
        if (data == null || level == null) return;

        BlockPos pylonPos = blockEntity.getBlockPos();
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        int renderDistance = QFConfig.MAX_BEAM_RENDER_DISTANCE.get();
        if (cameraPos.distanceToSqr(
                pylonPos.getX() + 0.5,
                pylonPos.getY() + 1.0,
                pylonPos.getZ() + 0.5) > (double) renderDistance * renderDistance) {
            return;
        }

        float time = level.getGameTime() + partialTick;
        if (data.isPowered()) {
            renderPylonCore(poseStack, bufferSource, data, time);
        }

        if (!data.isPowered() || !data.outputEnabled || !data.beamsVisible || data.beamStyle == BeamStyle.PARTICLE
                || data.connections == null || data.connections.isEmpty()) {
            return;
        }

        renderConnectionBeams(poseStack, bufferSource, pylonPos, cameraPos, data, time);
    }

    private void renderConnectionBeams(PoseStack poseStack, MultiBufferSource bufferSource,
                                       BlockPos pylonPos, Vec3 cameraPos,
                                       ClientDataCache.PylonClientData data, float time) {
        float red = colorChannel(data.beamColor, 16);
        float green = colorChannel(data.beamColor, 8);
        float blue = colorChannel(data.beamColor, 0);
        float glowIntensity = Mth.clamp(data.glowIntensity, 0.2f, 1.5f);

        float coreAlpha = 0.78f;
        float glowAlpha = 0.30f;
        float widthMultiplier = 1.0f;
        float uvOffset = 0.0f;
        if (data.beamStyle == BeamStyle.PULSE) {
            float pulseTime = time * Mth.clamp(data.pulseSpeed, 0.1f, 5.0f);
            float pulse = 0.5f + 0.5f * Mth.sin(pulseTime * 0.12f);
            coreAlpha = 0.62f + 0.18f * pulse;
            glowAlpha = 0.20f + 0.13f * pulse;
            widthMultiplier = 0.90f + 0.15f * pulse;
            uvOffset = Mth.frac(pulseTime * 0.025f);
        }

        boolean seeThrough = QFConfig.RENDER_BEAMS_THROUGH_BLOCKS.get();
        BeamRenderQuality quality = BeamRenderQuality.parse(QFConfig.BEAM_RENDER_QUALITY.get());
        if (QFConfig.MAX_BEAM_VERTICES_PER_FRAME.get() < quality.verticesPerBeam()) return;
        BeamRenderFrameBudget.prepare(cameraPos, quality);
        Vector3f start = new Vector3f(CORE_X, CORE_Y, CORE_Z);
        float cameraX = (float) (cameraPos.x - pylonPos.getX());
        float cameraY = (float) (cameraPos.y - pylonPos.getY());
        float cameraZ = (float) (cameraPos.z - pylonPos.getZ());
        float coreRed = red * 0.55f + 0.45f;
        float coreGreen = green * 0.55f + 0.45f;
        float coreBlue = blue * 0.55f + 0.45f;
        Set<Long> renderedTargets = new HashSet<>();
        var beams = new ArrayList<BeamSegment>();

        for (PylonSyncPayload.ConnectionEntry connection : data.connections) {
            BlockPos target = connection.pos();
            if (target == null || !connection.status().showsBeam() || !renderedTargets.add(target.asLong())) {
                continue;
            }
            if (!BeamRenderFrameBudget.isConnectionInRange(
                    pylonPos, target, data.effectiveRange)
                    || !BeamRenderFrameBudget.isSelected(pylonPos, target)) {
                continue;
            }

            Vector3f end = targetFaceEndpoint(pylonPos, target, start);
            // Charged but idle links remain visible as faint guides. Only
            // measured delivery receives the full field brightness.
            float activity = connection.lastTransferred() > 0 ? 1.0f : 0.22f;
            beams.add(new BeamSegment(end, activity));
        }
        if (beams.isEmpty()) return;

        // These custom render types share the source's fallback builder. Asking
        // for the glow buffer finishes the core buffer, so emit each whole pass
        // before requesting another type; never retain a consumer across that switch.
        RenderType coreType = QFRenderTypes.softEmissive(BEAM_CORE_TEXTURE, seeThrough);
        VertexConsumer coreConsumer = bufferSource.getBuffer(coreType);
        for (BeamSegment beam : beams) {
            Vector3f end = beam.end();
            float activity = beam.activity();
            switch (quality) {
                case LOW -> renderBillboardBeam(poseStack, coreConsumer, start, end,
                        cameraX, cameraY, cameraZ,
                        BEAM_GLOW_WIDTH * 0.45f * glowIntensity * widthMultiplier,
                        coreRed, coreGreen, coreBlue, coreAlpha * activity, uvOffset);
                case MEDIUM -> renderBillboardBeam(poseStack, coreConsumer, start, end,
                        cameraX, cameraY, cameraZ,
                        BEAM_CORE_WIDTH * 1.35f * glowIntensity,
                        coreRed, coreGreen, coreBlue, coreAlpha * activity, uvOffset);
                case HIGH -> renderCrossQuads(poseStack, coreConsumer, start, end,
                        BEAM_CORE_WIDTH * glowIntensity,
                        coreRed, coreGreen, coreBlue, coreAlpha * activity, uvOffset);
            }
        }
        if (quality != BeamRenderQuality.LOW) {
            VertexConsumer glowConsumer = bufferSource.getBuffer(
                    QFRenderTypes.softEmissive(BEAM_GLOW_TEXTURE, seeThrough));
            for (BeamSegment beam : beams) {
                renderBillboardBeam(poseStack, glowConsumer, start, beam.end(),
                        cameraX, cameraY, cameraZ,
                        BEAM_GLOW_WIDTH * glowIntensity * widthMultiplier,
                        red, green, blue, glowAlpha * beam.activity(), uvOffset);
            }
        }
    }

    private record BeamSegment(Vector3f end, float activity) {}

    private void renderCrossQuads(PoseStack poseStack, VertexConsumer consumer,
                                  Vector3f start, Vector3f end, float width,
                                  float red, float green, float blue, float alpha, float uvOffset) {
        Vector3f direction = new Vector3f(end).sub(start);
        float length = direction.length();
        if (length < 0.01f) return;
        direction.div(length);

        Vector3f reference = Math.abs(direction.y()) < 0.99f
                ? new Vector3f(0.0f, 1.0f, 0.0f)
                : new Vector3f(1.0f, 0.0f, 0.0f);
        Vector3f perpendicularA = direction.cross(reference, new Vector3f()).normalize().mul(width);
        Vector3f perpendicularB = direction.cross(perpendicularA, new Vector3f()).normalize().mul(width);
        Matrix4f matrix = poseStack.last().pose();

        emitQuad(matrix, consumer, start, end, perpendicularA,
                red, green, blue, alpha, uvOffset, length);
        emitQuad(matrix, consumer, start, end, perpendicularB,
                red, green, blue, alpha, uvOffset, length);
    }

    private void renderBillboardBeam(PoseStack poseStack, VertexConsumer consumer,
                                     Vector3f start, Vector3f end,
                                     float cameraX, float cameraY, float cameraZ,
                                     float width, float red, float green, float blue,
                                     float alpha, float uvOffset) {
        Vector3f direction = new Vector3f(end).sub(start);
        float length = direction.length();
        if (length < 0.01f) return;
        direction.div(length);

        Vector3f toCameraAtStart = new Vector3f(cameraX, cameraY, cameraZ).sub(start);
        Vector3f perpendicularStart = direction.cross(toCameraAtStart, new Vector3f());
        if (perpendicularStart.lengthSquared() < 0.0001f) {
            Vector3f reference = Math.abs(direction.y()) < 0.99f
                    ? new Vector3f(0.0f, 1.0f, 0.0f)
                    : new Vector3f(1.0f, 0.0f, 0.0f);
            direction.cross(reference, perpendicularStart);
        }
        perpendicularStart.normalize().mul(width);

        Vector3f toCameraAtEnd = new Vector3f(cameraX, cameraY, cameraZ).sub(end);
        Vector3f perpendicularEnd = direction.cross(toCameraAtEnd, new Vector3f());
        if (perpendicularEnd.lengthSquared() < 0.0001f) {
            perpendicularEnd.set(perpendicularStart);
        } else {
            perpendicularEnd.normalize().mul(width);
        }

        Vector3f normal = direction.cross(perpendicularStart, new Vector3f()).normalize();
        Matrix4f matrix = poseStack.last().pose();
        consumer.addVertex(matrix, start.x() - perpendicularStart.x(), start.y() - perpendicularStart.y(), start.z() - perpendicularStart.z())
                .setColor(red, green, blue, alpha).setUv(0.0f, uvOffset).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT)
                .setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, end.x() - perpendicularEnd.x(), end.y() - perpendicularEnd.y(), end.z() - perpendicularEnd.z())
                .setColor(red, green, blue, alpha).setUv(0.0f, uvOffset + length).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT)
                .setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, end.x() + perpendicularEnd.x(), end.y() + perpendicularEnd.y(), end.z() + perpendicularEnd.z())
                .setColor(red, green, blue, alpha).setUv(1.0f, uvOffset + length).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT)
                .setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, start.x() + perpendicularStart.x(), start.y() + perpendicularStart.y(), start.z() + perpendicularStart.z())
                .setColor(red, green, blue, alpha).setUv(1.0f, uvOffset).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT)
                .setNormal(normal.x(), normal.y(), normal.z());
    }

    private void emitQuad(Matrix4f matrix, VertexConsumer consumer,
                          Vector3f start, Vector3f end, Vector3f perpendicular,
                          float red, float green, float blue, float alpha,
                          float uvOffset, float uvLength) {
        Vector3f direction = new Vector3f(end).sub(start).normalize();
        Vector3f normal = direction.cross(perpendicular, new Vector3f()).normalize();
        consumer.addVertex(matrix, start.x() - perpendicular.x(), start.y() - perpendicular.y(), start.z() - perpendicular.z())
                .setColor(red, green, blue, alpha).setUv(0.0f, uvOffset).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT)
                .setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, end.x() - perpendicular.x(), end.y() - perpendicular.y(), end.z() - perpendicular.z())
                .setColor(red, green, blue, alpha).setUv(0.0f, uvOffset + uvLength).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT)
                .setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, end.x() + perpendicular.x(), end.y() + perpendicular.y(), end.z() + perpendicular.z())
                .setColor(red, green, blue, alpha).setUv(1.0f, uvOffset + uvLength).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT)
                .setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, start.x() + perpendicular.x(), start.y() + perpendicular.y(), start.z() + perpendicular.z())
                .setColor(red, green, blue, alpha).setUv(1.0f, uvOffset).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT)
                .setNormal(normal.x(), normal.y(), normal.z());
    }

    private void renderPylonCore(PoseStack poseStack, MultiBufferSource bufferSource,
                                 ClientDataCache.PylonClientData data, float time) {
        float charge = data.maxEnergy > 0
                ? Mth.clamp((float) data.energy / data.maxEnergy, 0.0f, 1.0f)
                : 0.0f;
        float fieldStrength = data.outputEnabled && data.throughput > 0 ? 1.0f : 0.18f + 0.22f * charge;
        renderEnergyOrb(poseStack, bufferSource, data.beamColor, fieldStrength, time);
        renderEnergyRings(poseStack, bufferSource, data.beamColor, fieldStrength, time);
    }

    private void renderEnergyOrb(PoseStack poseStack, MultiBufferSource bufferSource,
                                 int color, float charge, float time) {
        VertexConsumer consumer = bufferSource.getBuffer(
                QFRenderTypes.softEmissive(ENERGY_ORB_TEXTURE, false));
        float red = colorChannel(color, 16);
        float green = colorChannel(color, 8);
        float blue = colorChannel(color, 0);
        float halfSize = (0.255f + 0.010f * Mth.sin(time * 0.08f)) * (0.90f + 0.10f * charge);
        float alpha = 0.36f + 0.36f * charge + 0.020f * charge * Mth.sin(time * 0.10f);
        float rotation = time * 0.015f;

        Quaternionf cameraRotation = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        Vector3f right = cameraRotation.transform(new Vector3f(1.0f, 0.0f, 0.0f));
        Vector3f up = cameraRotation.transform(new Vector3f(0.0f, 1.0f, 0.0f));
        float cosine = Mth.cos(rotation);
        float sine = Mth.sin(rotation);
        Vector3f rotatedRight = new Vector3f(right).mul(cosine).add(new Vector3f(up).mul(sine));
        Vector3f rotatedUp = new Vector3f(up).mul(cosine).sub(new Vector3f(right).mul(sine));
        Vector3f normal = rotatedRight.cross(rotatedUp, new Vector3f()).normalize();
        rotatedRight.mul(halfSize);
        rotatedUp.mul(halfSize);

        Matrix4f matrix = poseStack.last().pose();
        emitBillboardQuad(matrix, consumer, rotatedRight, rotatedUp, normal,
                red, green, blue, alpha);
        // A small white-hot center keeps dark network colors legible without
        // washing out the surrounding colored containment field.
        rotatedRight.mul(0.48f);
        rotatedUp.mul(0.48f);
        emitBillboardQuad(matrix, consumer, rotatedRight, rotatedUp, normal,
                0.75f + 0.25f * red, 0.75f + 0.25f * green, 0.75f + 0.25f * blue,
                0.50f + 0.40f * charge);
    }

    private void emitBillboardQuad(Matrix4f matrix, VertexConsumer consumer,
                                   Vector3f right, Vector3f up, Vector3f normal,
                                   float red, float green, float blue, float alpha) {
        consumer.addVertex(matrix, CORE_X - right.x() - up.x(), CORE_Y - right.y() - up.y(), CORE_Z - right.z() - up.z())
                .setColor(red, green, blue, alpha).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT)
                .setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, CORE_X + right.x() - up.x(), CORE_Y + right.y() - up.y(), CORE_Z + right.z() - up.z())
                .setColor(red, green, blue, alpha).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT)
                .setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, CORE_X + right.x() + up.x(), CORE_Y + right.y() + up.y(), CORE_Z + right.z() + up.z())
                .setColor(red, green, blue, alpha).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT)
                .setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, CORE_X - right.x() + up.x(), CORE_Y - right.y() + up.y(), CORE_Z - right.z() + up.z())
                .setColor(red, green, blue, alpha).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT)
                .setNormal(normal.x(), normal.y(), normal.z());
    }

    private void renderEnergyRings(PoseStack poseStack, MultiBufferSource bufferSource,
                                   int color, float charge, float time) {
        VertexConsumer consumer = bufferSource.getBuffer(
                QFRenderTypes.softEmissive(ENERGY_RING_TEXTURE, false));
        float red = colorChannel(color, 16);
        float green = colorChannel(color, 8);
        float blue = colorChannel(color, 0);
        // The ring mask occupies about two thirds of its quad. Account for that
        // inset so the visible rings surround the copper containment coils.
        float radius = 0.52f + 0.05f * charge;
        Matrix4f matrix = poseStack.last().pose();

        for (int index = 0; index < RING_INCLINATIONS.length; index++) {
            Quaternionf rotation = new Quaternionf()
                    .rotateX(RING_INCLINATIONS[index] * Mth.DEG_TO_RAD)
                    .rotateY(RING_SPEEDS[index] * time);
            Vector3f normal = rotation.transform(new Vector3f(0.0f, 1.0f, 0.0f));
            Vector3f cornerA = rotation.transform(new Vector3f(-radius, 0.0f, -radius));
            Vector3f cornerB = rotation.transform(new Vector3f(radius, 0.0f, -radius));
            Vector3f cornerC = rotation.transform(new Vector3f(radius, 0.0f, radius));
            Vector3f cornerD = rotation.transform(new Vector3f(-radius, 0.0f, radius));
            float alpha = 0.18f + 0.40f * charge
                    + 0.015f * charge * Mth.sin(time * 0.05f + index * 1.7f);

            emitRingVertex(matrix, consumer, cornerA, red, green, blue, alpha, 0.0f, 0.0f, normal);
            emitRingVertex(matrix, consumer, cornerB, red, green, blue, alpha, 1.0f, 0.0f, normal);
            emitRingVertex(matrix, consumer, cornerC, red, green, blue, alpha, 1.0f, 1.0f, normal);
            emitRingVertex(matrix, consumer, cornerD, red, green, blue, alpha, 0.0f, 1.0f, normal);
        }
    }

    private void emitRingVertex(Matrix4f matrix, VertexConsumer consumer, Vector3f corner,
                                float red, float green, float blue, float alpha,
                                float u, float v, Vector3f normal) {
        consumer.addVertex(matrix, CORE_X + corner.x(), CORE_Y + corner.y(), CORE_Z + corner.z())
                .setColor(red, green, blue, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT)
                .setNormal(normal.x(), normal.y(), normal.z());
    }

    private static Vector3f targetFaceEndpoint(BlockPos source, BlockPos target, Vector3f start) {
        Vector3f targetCenter = new Vector3f(
                target.getX() - source.getX() + 0.5f,
                target.getY() - source.getY() + 0.5f,
                target.getZ() - source.getZ() + 0.5f);
        Vector3f towardSource = new Vector3f(start).sub(targetCenter);
        float dominantAxis = Math.max(Math.abs(towardSource.x()),
                Math.max(Math.abs(towardSource.y()), Math.abs(towardSource.z())));
        if (dominantAxis < 0.001f) return targetCenter;
        return targetCenter.add(towardSource.mul(0.515f / dominantAxis));
    }

    private static float colorChannel(int color, int shift) {
        return ((color >> shift) & 0xFF) / 255.0f;
    }

    private static net.minecraft.client.renderer.culling.Frustum frameFrustum;

    public static void setFrameFrustum(net.minecraft.client.renderer.culling.Frustum frustum) {
        frameFrustum = frustum;
    }

    @Override
    public boolean shouldRender(QuantumPylonBlockEntity blockEntity, Vec3 cameraPosition) {
        return BlockEntityRenderer.super.shouldRender(blockEntity, cameraPosition)
                && (frameFrustum == null || frameFrustum.isVisible(getRenderBoundingBox(blockEntity)));
    }

    public AABB getRenderBoundingBox(QuantumPylonBlockEntity blockEntity) {
        BlockPos source = blockEntity.getBlockPos();
        AABB bounds = new AABB(
                source.getX() - 0.2,
                source.getY() - 0.2,
                source.getZ() - 0.2,
                source.getX() + 1.2,
                source.getY() + 2.2,
                source.getZ() + 1.2);

        ClientDataCache.PylonClientData data = ClientDataCache.get(source);
        if (data == null || !data.isPowered() || !data.outputEnabled || !data.beamsVisible || data.beamStyle == BeamStyle.PARTICLE
                || data.connections == null) {
            return bounds;
        }

        BeamRenderQuality quality = BeamRenderQuality.parse(QFConfig.BEAM_RENDER_QUALITY.get());
        if (QFConfig.MAX_BEAM_VERTICES_PER_FRAME.get() < quality.verticesPerBeam()) return bounds;
        BeamRenderFrameBudget.prepare(
                Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(), quality);

        for (PylonSyncPayload.ConnectionEntry connection : data.connections) {
            if (!BeamRenderFrameBudget.isConnectionInRange(
                    source, connection.pos(), data.effectiveRange)
                    || !BeamRenderFrameBudget.isSelected(source, connection.pos())) continue;
            BlockPos target = connection.pos();
            bounds = bounds.minmax(new AABB(
                    target.getX() - 0.1,
                    target.getY() - 0.1,
                    target.getZ() - 0.1,
                    target.getX() + 1.1,
                    target.getY() + 1.1,
                    target.getZ() + 1.1));
        }
        return bounds;
    }

    @Override
    public int getViewDistance() {
        return QFConfig.MAX_BEAM_RENDER_DISTANCE.get();
    }

    @Override
    public boolean shouldRenderOffScreen(QuantumPylonBlockEntity blockEntity) {
        // The source section can be outside the visible-section set while a selected beam
        // crosses the camera frustum. Global registration keeps that beam reachable; the
        // bounded AABB, view distance, and shared vertex allocator still perform the culling.
        return true;
    }
}
