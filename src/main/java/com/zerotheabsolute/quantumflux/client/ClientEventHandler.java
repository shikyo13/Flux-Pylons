package com.zerotheabsolute.quantumflux.client;

import com.zerotheabsolute.quantumflux.QFConfig;
import com.zerotheabsolute.quantumflux.QuantumFlux;
import com.zerotheabsolute.quantumflux.blockentity.QuantumPylonBlockEntity;
import com.zerotheabsolute.quantumflux.init.QFBlockEntities;
import com.zerotheabsolute.quantumflux.init.QFItems;
import com.zerotheabsolute.quantumflux.client.renderer.PylonBlockEntityRenderer;
import com.zerotheabsolute.quantumflux.client.renderer.BeamRenderFrameBudget;
import com.zerotheabsolute.quantumflux.client.screen.GadgetScreen;
import com.zerotheabsolute.quantumflux.client.sound.PylonHumSoundInstance;
import com.zerotheabsolute.quantumflux.network.PylonSyncPayload;
import com.zerotheabsolute.quantumflux.util.BeamStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;

import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;

public final class ClientEventHandler {

    private ClientEventHandler() {}

    // ── Renderer registration (MOD bus) ──

    @EventBusSubscriber(modid = QuantumFlux.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(QFBlockEntities.QUANTUM_PYLON_BE.get(),
                    PylonBlockEntityRenderer::new);
        }

        @SubscribeEvent
        public static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                ClientScreenBridge.installRenderBounds(PylonBlockEntityRenderer::renderBounds);
                net.minecraft.client.gui.screens.MenuScreens.register(com.zerotheabsolute.quantumflux.init.QFMenus.PYLON_UPGRADES.get(), com.zerotheabsolute.quantumflux.client.screen.PylonUpgradeScreen::new);
                com.zerotheabsolute.quantumflux.network.ClientPayloadBridge.install(
                        ClientPacketHandlers::handlePylonSync,
                        ClientPacketHandlers::handlePylonTelemetry,
                        ClientPacketHandlers::handleNetworkListSync,
                        ClientPacketHandlers::handleNetworkTelemetry,
                        ClientPacketHandlers::handleActionResult);
                ClientScreenBridge.installGadgetScreenOpener(
                        pos -> Minecraft.getInstance().setScreen(pos == null ? new GadgetScreen() : new GadgetScreen(pos)));

                net.minecraft.client.renderer.item.ItemProperties.register(
                        QFItems.QUANTUM_GADGET.get(),
                        new net.minecraft.resources.ResourceLocation(QuantumFlux.MODID, "active"),
                        (stack, level, entity, seed) ->
                                Boolean.TRUE.equals(com.zerotheabsolute.quantumflux.init.QFDataComponents.GADGET_ACTIVE.get(stack))
                                        ? 1.0f : 0.0f);
            });
        }


        @SubscribeEvent
        public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
            event.register((state, level, pos, tintIndex) -> {
                if (tintIndex != 0 || level == null || pos == null) return -1;
                // Top block reads from the bottom block's BE
                BlockPos bePos = state.getValue(com.zerotheabsolute.quantumflux.block.QuantumPylonBlock.HALF)
                        == com.zerotheabsolute.quantumflux.block.QuantumPylonBlock.PylonHalf.TOP
                        ? pos.below() : pos;
                if (level.getBlockEntity(bePos) instanceof QuantumPylonBlockEntity be) {
                    return be.getBeamColor();
                }
                return QFConfig.DEFAULT_BEAM_COLOR.get() & 0xFFFFFF;
            }, com.zerotheabsolute.quantumflux.init.QFBlocks.QUANTUM_PYLON.get());
        }

        @SubscribeEvent
        public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
            event.register((stack, tintIndex) -> {
                boolean active = Boolean.TRUE.equals(com.zerotheabsolute.quantumflux.init.QFDataComponents.GADGET_ACTIVE.get(stack));
                if (tintIndex == 1) return active ? 0xFF65E375 : 0xFF253E2D;
                if (tintIndex == 2) return active ? 0xFF512626 : 0xFFE25543;
                if (tintIndex != 0) return -1;
                if (!active) return 0xFF48565B;
                Integer color = com.zerotheabsolute.quantumflux.init.QFDataComponents.GADGET_COLOR.get(stack);
                // Minecraft 1.21 item colors include alpha; a plain RGB value is invisible.
                return 0xFF000000 | (color == null ? QFConfig.DEFAULT_BEAM_COLOR.get() : color) & 0xFFFFFF;
            }, QFItems.QUANTUM_GADGET.get());
            event.register((stack, tintIndex) -> tintIndex == 0 ? 0xFF00CFE8 : -1,
                    QFItems.QUANTUM_PYLON.get());
        }
    }

    // ── Client tick + HUD overlay (GAME bus) ──

    @EventBusSubscriber(modid = QuantumFlux.MODID, bus = EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class GameBusEvents {

        private static final Map<BlockPos, PylonHumSoundInstance> activeHums = new HashMap<>();
        private static final int MAX_CONCURRENT_HUMS = 3;
        private static final int PARTICLE_TICK_INTERVAL = 2;
        private static final int HUM_REFRESH_INTERVAL = 10;
        private static final int MAX_CACHE_ENTRIES_PER_EFFECT_TICK = 256;
        private static final int MAX_EFFECT_PYLONS_PER_TICK = 32;
        private static final int MAX_PARTICLES_PER_TICK = 64;
        private static final double HUM_START_DISTANCE_SQR = 6.0 * 6.0;
        private static final double HUM_STOP_DISTANCE_SQR = 8.0 * 8.0;

        @SubscribeEvent
        public static void onRenderFrame(TickEvent.RenderTickEvent event) {
            if (event.phase != TickEvent.Phase.START) return;
            BeamRenderFrameBudget.beginFrame();
        }

        @SubscribeEvent
        public static void onClientLevelTick(TickEvent.LevelTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            if (!(event.level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel)) return;
            if (clientLevel != Minecraft.getInstance().level) return;

            long gameTime = clientLevel.getGameTime();

            // Expire stale cache entries
            ClientDataCache.tick(gameTime);
            ClientNetworkCache.tick(gameTime);

            if (gameTime % PARTICLE_TICK_INTERVAL == 0) {
                spawnPylonParticles(clientLevel, gameTime);
            }

            if (gameTime % HUM_REFRESH_INTERVAL == 0) {
                managePylonHums(clientLevel);
            }
        }

        private static void spawnPylonParticles(
                net.minecraft.client.multiplayer.ClientLevel clientLevel, long gameTime) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) return;

            int configuredPerPylon = QFConfig.MAX_PARTICLES_PER_PYLON.get();
            int renderDistance = QFConfig.MAX_BEAM_RENDER_DISTANCE.get();
            double maximumDistanceSqr = (double) renderDistance * renderDistance;
            Vec3 cameraPosition = minecraft.gameRenderer.getMainCamera().getPosition();
            int remainingParticles = MAX_PARTICLES_PER_TICK;
            int scannedEntries = 0;
            int processedPylons = 0;

            for (Map.Entry<BlockPos, ClientDataCache.PylonClientData> entry
                    : ClientDataCache.getAll().entrySet()) {
                if (++scannedEntries > MAX_CACHE_ENTRIES_PER_EFFECT_TICK
                        || processedPylons >= MAX_EFFECT_PYLONS_PER_TICK
                        || remainingParticles <= 0) {
                    break;
                }

                BlockPos pylonPos = entry.getKey();
                ClientDataCache.PylonClientData data = entry.getValue();
                if (!data.isPowered() || !clientLevel.hasChunkAt(pylonPos)
                        || cameraPosition.distanceToSqr(
                        pylonPos.getX() + 0.5,
                        pylonPos.getY() + 1.0,
                        pylonPos.getZ() + 0.5) > maximumDistanceSqr) {
                    continue;
                }

                processedPylons++;
                DustParticleOptions particle = createParticle(data.beamColor, 0.65f);

                if (data.outputEnabled && data.beamsVisible && data.beamStyle == BeamStyle.PARTICLE
                        && configuredPerPylon > 0 && data.connections != null
                        && !data.connections.isEmpty()) {
                    int particleCount = Math.min(configuredPerPylon,
                            Math.min(remainingParticles, data.connections.size()));
                    int firstConnection = clientLevel.random.nextInt(data.connections.size());

                    for (int index = 0; index < data.connections.size() && particleCount > 0; index++) {
                        PylonSyncPayload.ConnectionEntry connection = data.connections.get(
                                (firstConnection + index) % data.connections.size());
                        if (!connection.status().showsBeam() || connection.lastTransferred() <= 0 || !clientLevel.hasChunkAt(connection.pos())
                                || !isConnectionInRange(pylonPos, connection.pos(), data.effectiveRange)) continue;

                        Vec3 start = new Vec3(
                                pylonPos.getX() + 0.5,
                                pylonPos.getY() + 1.62,
                                pylonPos.getZ() + 0.5);
                        Vec3 end = targetFaceEndpoint(connection.pos(), start);
                        double progress = 0.06 + clientLevel.random.nextDouble() * 0.88;
                        Vec3 position = start.lerp(end, progress);
                        clientLevel.addParticle(particle, position.x, position.y, position.z,
                                0.0, 0.0, 0.0);
                        particleCount--;
                        remainingParticles--;
                    }
                }

                // A sparse core sparkle replaces the old frame-rate-dependent renderer particles.
                if (gameTime % 6 == 0 && remainingParticles > 0) {
                    double x = pylonPos.getX() + 0.42 + clientLevel.random.nextDouble() * 0.16;
                    double y = pylonPos.getY() + 1.54 + clientLevel.random.nextDouble() * 0.16;
                    double z = pylonPos.getZ() + 0.42 + clientLevel.random.nextDouble() * 0.16;
                    clientLevel.addParticle(particle, x, y, z,
                            (clientLevel.random.nextDouble() - 0.5) * 0.008,
                            clientLevel.random.nextDouble() * 0.012,
                            (clientLevel.random.nextDouble() - 0.5) * 0.008);
                    remainingParticles--;
                }
            }
        }

        private static DustParticleOptions createParticle(int color, float scale) {
            return new DustParticleOptions(new Vector3f(
                    ((color >> 16) & 0xFF) / 255.0f,
                    ((color >> 8) & 0xFF) / 255.0f,
                    (color & 0xFF) / 255.0f), scale);
        }

        private static Vec3 targetFaceEndpoint(BlockPos target, Vec3 start) {
            Vec3 targetCenter = Vec3.atCenterOf(target);
            Vec3 towardSource = start.subtract(targetCenter);
            double dominantAxis = Math.max(Math.abs(towardSource.x),
                    Math.max(Math.abs(towardSource.y), Math.abs(towardSource.z)));
            if (dominantAxis < 0.001) return targetCenter;
            return targetCenter.add(towardSource.scale(0.515 / dominantAxis));
        }

        private static boolean isConnectionInRange(BlockPos source, BlockPos target, int effectiveRange) {
            long maximum = Math.max(8, Math.min(256, effectiveRange)) + 2L;
            long deltaX = (long) target.getX() - source.getX();
            long deltaY = (long) target.getY() - source.getY();
            long deltaZ = (long) target.getZ() - source.getZ();
            if (Math.abs(deltaX) > maximum || Math.abs(deltaY) > maximum || Math.abs(deltaZ) > maximum) {
                return false;
            }
            return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ <= maximum * maximum;
        }

        private static void managePylonHums(net.minecraft.client.multiplayer.ClientLevel clientLevel) {
            var mc = Minecraft.getInstance();
            if (mc.player == null) return;
            var soundManager = mc.getSoundManager();

            // Remove stale hums using a wider stop radius to avoid boundary thrashing.
            Iterator<Map.Entry<BlockPos, PylonHumSoundInstance>> it = activeHums.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                PylonHumSoundInstance hum = entry.getValue();
                if (hum.isStopped() || !soundManager.isActive(hum)) {
                    it.remove();
                    continue;
                }
                ClientDataCache.PylonClientData data = ClientDataCache.get(entry.getKey());
                boolean tooFar = mc.player.blockPosition().distSqr(entry.getKey()) > HUM_STOP_DISTANCE_SQR;
                if (data == null || !data.isPowered() || tooFar || QFConfig.PYLON_HUM_VOLUME.get() <= 0
                        || !clientLevel.hasChunkAt(entry.getKey())) {
                    hum.requestFadeOut();
                } else {
                    hum.resume();
                    hum.setTransferring(data.outputEnabled && data.throughput > 0);
                }
            }

            int availableSlots = MAX_CONCURRENT_HUMS - activeHums.size();
            if (availableSlots <= 0 || QFConfig.PYLON_HUM_VOLUME.get() <= 0) return;

            PriorityQueue<HumCandidate> nearestCandidates = new PriorityQueue<>(
                    java.util.Comparator.comparingDouble(HumCandidate::distanceSqr).reversed());
            int scannedEntries = 0;
            for (Map.Entry<BlockPos, ClientDataCache.PylonClientData> entry
                    : ClientDataCache.getAll().entrySet()) {
                if (++scannedEntries > MAX_CACHE_ENTRIES_PER_EFFECT_TICK) break;
                BlockPos pos = entry.getKey();
                if (!entry.getValue().isPowered() || activeHums.containsKey(pos)
                        || !clientLevel.hasChunkAt(pos)) {
                    continue;
                }
                double distanceSqr = mc.player.blockPosition().distSqr(pos);
                if (distanceSqr > HUM_START_DISTANCE_SQR) continue;

                nearestCandidates.offer(new HumCandidate(pos, distanceSqr));
                if (nearestCandidates.size() > availableSlots) {
                    nearestCandidates.poll();
                }
            }

            var candidates = new java.util.ArrayList<>(nearestCandidates);
            candidates.sort(java.util.Comparator.comparingDouble(HumCandidate::distanceSqr));
            for (HumCandidate candidate : candidates) {
                BlockPos pos = candidate.pos();

                PylonHumSoundInstance hum = new PylonHumSoundInstance(pos);
                var data = ClientDataCache.get(pos);
                hum.setTransferring(data != null && data.outputEnabled && data.throughput > 0);
                soundManager.play(hum);
                activeHums.put(pos, hum);
            }
        }

        private record HumCandidate(BlockPos pos, double distanceSqr) {
        }

        @SubscribeEvent
        public static void onLevelUnload(net.minecraftforge.event.level.LevelEvent.Unload event) {
            if (event.getLevel().isClientSide()) {
                stopAllHums();
                ClientDataCache.clear();
                ClientNetworkCache.clear();
            }
        }

        private static void stopAllHums() {
            for (PylonHumSoundInstance sound : activeHums.values()) {
                Minecraft.getInstance().getSoundManager().stop(sound);
            }
            activeHums.clear();
        }

        @SubscribeEvent
        public static void onRenderOverlay(RenderGuiEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player == null || mc.screen != null || mc.options.hideGui) return;

            ItemStack held = player.getMainHandItem();
            if (!held.is(QFItems.QUANTUM_GADGET.get())) {
                held = player.getOffhandItem();
                if (!held.is(QFItems.QUANTUM_GADGET.get())) return;
            }

            // Skip overlay when gadget is off
            if (!Boolean.TRUE.equals(com.zerotheabsolute.quantumflux.init.QFDataComponents.GADGET_ACTIVE.get(held))) return;

            if (!QFConfig.SHOW_GADGET_OVERLAY.get()) return;

            GuiGraphics gfx = event.getGuiGraphics();
            int messageY = PylonReadout.HUD_TOP;

            // Context-sensitive overlay based on what the player is looking at
            HitResult hit = mc.hitResult;
            Component overlayText = null;

            if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos target = blockHit.getBlockPos();
                Level level = player.level();

                BlockPos pylonPos = resolvePylonBottom(level, target);
                if (pylonPos != null) {
                    ClientDataCache.PylonClientData data = ClientDataCache.get(pylonPos);
                    if (data != null) {
                        messageY = PylonReadout.renderHud(gfx, mc, data);
                    } else {
                        overlayText = Component.translatable("overlay.quantumflux.pylon.no_data");
                    }
                } else {
                    ClientDataCache.LinkedTargetData linked = ClientDataCache.getLinkedTarget(target);
                    if (linked != null) {
                        BlockPos source = linked.pylonPos();
                        overlayText = Component.translatable("overlay.quantumflux.linked",
                                source.getX(), source.getY(), source.getZ(),
                                PylonReadout.formatRate(linked.connection().lastTransferred()),
                                linked.connection().status().label());
                    }
                }
            }

            var linkData = com.zerotheabsolute.quantumflux.init.QFDataComponents.LINKING_DATA.get(held);
            if (linkData != null && linkData.active()) {
                Component linkMessage = Component.translatable("overlay.quantumflux.linking",
                        linkData.pylonPos().getX(), linkData.pylonPos().getY(), linkData.pylonPos().getZ());
                messageY = PylonReadout.renderHudMessage(gfx, mc, linkMessage, messageY, 0xFFFFFF00);
            }

            if (overlayText != null) {
                PylonReadout.renderHudMessage(gfx, mc, overlayText, messageY, 0xFF7FEFFF);
            }
        }

        private static BlockPos resolvePylonBottom(Level level, BlockPos target) {
            var state = level.getBlockState(target);
            if (!(state.getBlock() instanceof com.zerotheabsolute.quantumflux.block.QuantumPylonBlock)) {
                return null;
            }
            return state.getValue(com.zerotheabsolute.quantumflux.block.QuantumPylonBlock.HALF)
                    == com.zerotheabsolute.quantumflux.block.QuantumPylonBlock.PylonHalf.TOP
                    ? target.below()
                    : target;
        }
    }
}
