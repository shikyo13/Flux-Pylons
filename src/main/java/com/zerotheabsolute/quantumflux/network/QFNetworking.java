package com.zerotheabsolute.quantumflux.network;

import com.mojang.authlib.GameProfile;
import com.zerotheabsolute.quantumflux.QFConfig;
import com.zerotheabsolute.quantumflux.blockentity.QuantumPylonBlockEntity;
import com.zerotheabsolute.quantumflux.init.QFDataComponents;
import com.zerotheabsolute.quantumflux.item.QuantumGadgetItem;
import com.zerotheabsolute.quantumflux.network.data.QFNetwork;
import com.zerotheabsolute.quantumflux.network.data.QuantumFluxNetworkManager;
import com.zerotheabsolute.quantumflux.util.BeamStyle;
import com.zerotheabsolute.quantumflux.util.PriorityMode;
import com.zerotheabsolute.quantumflux.util.QFPasswordUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

public final class QFNetworking {

    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final double LOCAL_ACTION_DISTANCE_SQUARED = 8.0D * 8.0D;
    private static final int GENERAL_RATE_WINDOW_TICKS = 20;
    private static final int GENERAL_RATE_LIMIT = 40;
    private static final int PASSWORD_RATE_WINDOW_TICKS = 200;
    private static final int PASSWORD_RATE_LIMIT = 5;
    private static final int SHARED_MUTATION_RATE_WINDOW_TICKS = 20;
    private static final int SHARED_MUTATION_RATE_LIMIT = 4;
    private static final int MAX_RATE_LIMIT_ENTRIES = 2048;
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    // Accessed only from the server thread after enqueueWork().
    private static final Map<UUID, RateWindow> GENERAL_RATE_WINDOWS =
            new LinkedHashMap<>(64, 0.75F, true);
    private static final Map<UUID, RateWindow> PASSWORD_RATE_WINDOWS =
            new LinkedHashMap<>(64, 0.75F, true);
    private static final Map<UUID, RateWindow> PROFILE_LOOKUP_RATE_WINDOWS =
            new LinkedHashMap<>(64, 0.75F, true);
    private static final Map<PlayerNetworkKey, RateWindow> SHARED_MUTATION_RATE_WINDOWS =
            new LinkedHashMap<>(64, 0.75F, true);
    private static final Map<UUID, RateWindow> SHARED_NETWORK_MUTATION_RATE_WINDOWS =
            new LinkedHashMap<>(64, 0.75F, true);
    private static final Set<ServerLevel> PENDING_NETWORK_LIST_BROADCASTS = new HashSet<>();
    private static final Map<ServerLevel, Long> LAST_NETWORK_LIST_BROADCAST = new WeakHashMap<>();

    private QFNetworking() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("3");

        registrar.playToClient(PylonSyncPayload.TYPE, PylonSyncPayload.STREAM_CODEC,
                QFNetworking::handlePylonSync);
        registrar.playToClient(PylonTelemetryPayload.TYPE, PylonTelemetryPayload.STREAM_CODEC,
                QFNetworking::handlePylonTelemetry);
        registrar.playToClient(NetworkListSyncS2CPayload.TYPE, NetworkListSyncS2CPayload.STREAM_CODEC,
                QFNetworking::handleNetworkListSync);
        registrar.playToClient(NetworkTelemetryPayload.TYPE, NetworkTelemetryPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadBridge.handle(payload)));
        registrar.playToClient(ActionResultS2CPayload.TYPE, ActionResultS2CPayload.STREAM_CODEC,
                QFNetworking::handleActionResult);
        registrar.playToClient(OpenPylonPayload.TYPE, OpenPylonPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.zerotheabsolute.quantumflux.client.ClientScreenBridge.openPylonScreen(payload.pos())));

        registrar.playToServer(GadgetActionPayload.TYPE, GadgetActionPayload.STREAM_CODEC,
                QFNetworking::handleGadgetAction);
        registrar.playToServer(NetworkActionC2SPayload.TYPE, NetworkActionC2SPayload.STREAM_CODEC,
                QFNetworking::handleNetworkAction);

        // LinkActionPayload was never used by the client. It is intentionally not
        // registered because it duplicated the item interaction path and exposed
        // arbitrary BlockPos mutation to forged packets.
    }

    private static void handlePylonSync(PylonSyncPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPayloadBridge.handle(payload));
    }

    private static void handlePylonTelemetry(PylonTelemetryPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPayloadBridge.handle(payload));
    }

    private static void handleNetworkListSync(NetworkListSyncS2CPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPayloadBridge.handle(payload));
    }

    private static void handleActionResult(ActionResultS2CPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPayloadBridge.handle(payload));
    }

    private static void handleGadgetAction(GadgetActionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            sendActionResult(player, payload.requestId(), processGadgetAction(payload, player));
        });
    }

    /** Executes the packet handler's authoritative mutation path without sending its acknowledgement. */
    static ActionResultS2CPayload.Result processGadgetAction(
            GadgetActionPayload payload, ServerPlayer player) {
        if (!isEligibleSender(player)) return ActionResultS2CPayload.Result.NOT_ALLOWED;
        if (!allowAction(GENERAL_RATE_WINDOWS, player, GENERAL_RATE_WINDOW_TICKS, GENERAL_RATE_LIMIT)) {
            return ActionResultS2CPayload.Result.RATE_LIMITED;
        }

        ItemStack gadget = getActiveHeldGadget(player);
        if (gadget.isEmpty()) return ActionResultS2CPayload.Result.GADGET_REQUIRED;

        ServerLevel level = player.serverLevel();
        QuantumFluxNetworkManager manager = QuantumFluxNetworkManager.get(level);
        AuthorizedPylon authorized = authorizePylon(player, manager, payload.pylonPos());
        if (authorized.failure() != null) return authorized.failure();
        QuantumPylonBlockEntity pylon = authorized.pylon();

        return switch (payload.action()) {
            case SET_PRIORITY_MODE -> {
                PriorityMode mode = enumValue(PriorityMode.values(), payload.intPayload());
                if (mode == null) yield ActionResultS2CPayload.Result.INVALID_REQUEST;
                if (pylon.getPriorityMode() == mode) yield ActionResultS2CPayload.Result.NO_CHANGE;
                pylon.setPriorityMode(mode);
                yield ActionResultS2CPayload.Result.APPLIED;
            }
            case UNLINK_SINGLE -> {
                BlockPos targetPos = payload.targetPos();
                if (targetPos == null || !pylon.isLinkedTo(targetPos)) {
                    yield ActionResultS2CPayload.Result.NOT_FOUND;
                }
                pylon.unlink(targetPos);
                yield ActionResultS2CPayload.Result.APPLIED;
            }
            case UNLINK_ALL -> {
                if (pylon.getConnections().isEmpty()) yield ActionResultS2CPayload.Result.NO_CHANGE;
                pylon.unlinkAll();
                yield ActionResultS2CPayload.Result.APPLIED;
            }
            case OPEN_UPGRADES -> {
                player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (id, inventory, ignored) -> new com.zerotheabsolute.quantumflux.menu.PylonUpgradeMenu(id, inventory, pylon),
                        Component.translatable("screen.quantumflux.upgrades.title")), pylon.getBlockPos());
                yield ActionResultS2CPayload.Result.APPLIED;
            }
            case SET_REDSTONE_MODE -> {
                var mode = enumValue(com.zerotheabsolute.quantumflux.util.RedstoneMode.values(), payload.intPayload());
                if (mode == null) yield ActionResultS2CPayload.Result.INVALID_REQUEST;
                if (pylon.getRedstoneMode() == mode) yield ActionResultS2CPayload.Result.NO_CHANGE;
                pylon.setRedstoneMode(mode);
                yield ActionResultS2CPayload.Result.APPLIED;
            }
            case INVALID -> ActionResultS2CPayload.Result.INVALID_REQUEST;
        };
    }

    public static boolean canConfigurePylonNow(ServerPlayer player, QuantumPylonBlockEntity pylon) {
        if (!isEligibleSender(player) || getActiveHeldGadget(player).isEmpty()) return false;
        var authorized = authorizePylon(player, QuantumFluxNetworkManager.get(player.serverLevel()), pylon.getBlockPos());
        return authorized.failure() == null && authorized.pylon() == pylon;
    }

    public static void openPylonController(ServerPlayer player, QuantumPylonBlockEntity pylon) {
        if (!canConfigurePylonNow(player, pylon)) return;
        sendNetworkListToPlayer(player, QuantumFluxNetworkManager.get(player.serverLevel()));
        pylon.sendSyncTo(player);
        PacketDistributor.sendToPlayer(player, new OpenPylonPayload(pylon.getBlockPos()));
    }

    private static void handleNetworkAction(NetworkActionC2SPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!isEligibleSender(player)) {
                sendActionResult(player, payload.requestId(), ActionResultS2CPayload.Result.NOT_ALLOWED);
                return;
            }
            if (!allowAction(GENERAL_RATE_WINDOWS, player, GENERAL_RATE_WINDOW_TICKS, GENERAL_RATE_LIMIT)) {
                sendActionResult(player, payload.requestId(), ActionResultS2CPayload.Result.RATE_LIMITED);
                return;
            }

            ItemStack gadget = getActiveHeldGadget(player);
            if (gadget.isEmpty()) {
                sendActionResult(player, payload.requestId(), ActionResultS2CPayload.Result.GADGET_REQUIRED);
                return;
            }

            ServerLevel level = player.serverLevel();
            QuantumFluxNetworkManager manager = QuantumFluxNetworkManager.get(level);
            ActionResultS2CPayload.Result result = switch (payload.action()) {
                case CREATE -> {
                    Optional<String> name = QuantumFluxNetworkManager.normalizeNetworkName(payload.name());
                    if (name.isEmpty() || !QuantumFluxNetworkManager.isValidColor(payload.color())) {
                        yield ActionResultS2CPayload.Result.INVALID_REQUEST;
                    }
                    QFNetwork created = manager.createNetwork(player.getUUID(), name.get(), payload.color());
                    if (created == null) yield ActionResultS2CPayload.Result.LIMIT_REACHED;
                    setGadgetNetwork(player, gadget, created);
                    broadcastNetworkLists(level, manager);
                    yield ActionResultS2CPayload.Result.APPLIED;
                }
                case DELETE -> {
                    if (!isValidNetworkId(payload.networkId())) {
                        yield ActionResultS2CPayload.Result.INVALID_REQUEST;
                    }
                    QFNetwork deleting = manager.getNetwork(payload.networkId());
                    if (deleting == null) yield ActionResultS2CPayload.Result.NOT_FOUND;
                    if (!deleting.isOwner(player.getUUID())) yield ActionResultS2CPayload.Result.NOT_ALLOWED;
                    if (!allowSharedMutation(player, payload.networkId())) {
                        yield ActionResultS2CPayload.Result.RATE_LIMITED;
                    }
                    List<BlockPos> pylonPositions = List.copyOf(deleting.getPylonPositions());
                    if (!manager.deleteNetwork(payload.networkId(), player.getUUID())) {
                        yield ActionResultS2CPayload.Result.NO_CHANGE;
                    }
                    for (BlockPos pylonPos : pylonPositions) {
                        if (isLoaded(level, pylonPos)
                                && level.getBlockEntity(pylonPos) instanceof QuantumPylonBlockEntity pylon
                                && payload.networkId().equals(pylon.getNetworkId())) {
                            pylon.setNetworkId(null);
                        }
                    }
                    broadcastNetworkLists(level, manager);
                    yield ActionResultS2CPayload.Result.APPLIED;
                }
                case RENAME -> {
                    if (!isValidNetworkId(payload.networkId())) {
                        yield ActionResultS2CPayload.Result.INVALID_REQUEST;
                    }
                    Optional<String> name = QuantumFluxNetworkManager.normalizeNetworkName(payload.name());
                    if (name.isEmpty()) yield ActionResultS2CPayload.Result.INVALID_REQUEST;
                    QFNetwork network = manager.getNetwork(payload.networkId());
                    if (network == null) yield ActionResultS2CPayload.Result.NOT_FOUND;
                    if (!network.isOwner(player.getUUID())) yield ActionResultS2CPayload.Result.NOT_ALLOWED;
                    if (!allowSharedMutation(player, payload.networkId())) {
                        yield ActionResultS2CPayload.Result.RATE_LIMITED;
                    }
                    if (!manager.updateNetwork(payload.networkId(), player.getUUID(),
                            name.get(), null, null, null)) yield ActionResultS2CPayload.Result.NO_CHANGE;
                    broadcastNetworkLists(level, manager);
                    yield ActionResultS2CPayload.Result.APPLIED;
                }
                case RECOLOR -> handleNetworkRecolor(payload, player, gadget, level, manager);
                case ASSIGN_PYLON -> handlePylonAssignment(payload, player, level, manager);
                case UNASSIGN_PYLON -> handlePylonUnassignment(payload, player, level, manager);
                case SELECT -> {
                    if (!isValidNetworkId(payload.networkId())) {
                        yield ActionResultS2CPayload.Result.INVALID_REQUEST;
                    }
                    QFNetwork network = manager.getNetwork(payload.networkId());
                    if (network == null) yield ActionResultS2CPayload.Result.NOT_FOUND;
                    if (!network.canAccess(player.getUUID())) yield ActionResultS2CPayload.Result.NOT_ALLOWED;
                    setGadgetNetwork(player, gadget, network);
                    yield ActionResultS2CPayload.Result.APPLIED;
                }
                case SET_BEAM_STYLE -> {
                    if (!isValidNetworkId(payload.networkId())) {
                        yield ActionResultS2CPayload.Result.INVALID_REQUEST;
                    }
                    BeamStyle style = enumValue(BeamStyle.values(), payload.color());
                    QFNetwork network = manager.getNetwork(payload.networkId());
                    if (style == null) yield ActionResultS2CPayload.Result.INVALID_REQUEST;
                    if (network == null) yield ActionResultS2CPayload.Result.NOT_FOUND;
                    if (!network.canConfigure(player.getUUID())) yield ActionResultS2CPayload.Result.NOT_ALLOWED;
                    if (!allowSharedMutation(player, payload.networkId())) {
                        yield ActionResultS2CPayload.Result.RATE_LIMITED;
                    }
                    if (!manager.updatePresentation(payload.networkId(), player.getUUID(), style, null)) {
                        yield ActionResultS2CPayload.Result.NO_CHANGE;
                    }
                    forEachLoadedPylon(level, network, pylon -> pylon.setBeamStyle(style));
                    broadcastNetworkLists(level, manager);
                    yield ActionResultS2CPayload.Result.APPLIED;
                }
                case TOGGLE_BEAMS -> {
                    if (!isValidNetworkId(payload.networkId())) {
                        yield ActionResultS2CPayload.Result.INVALID_REQUEST;
                    }
                    QFNetwork network = manager.getNetwork(payload.networkId());
                    if (network == null) yield ActionResultS2CPayload.Result.NOT_FOUND;
                    if (!network.canConfigure(player.getUUID())) yield ActionResultS2CPayload.Result.NOT_ALLOWED;
                    if (!allowSharedMutation(player, payload.networkId())) {
                        yield ActionResultS2CPayload.Result.RATE_LIMITED;
                    }

                    boolean visible = !network.isBeamsVisible();
                    if (!manager.updatePresentation(payload.networkId(), player.getUUID(), null, visible)) {
                        yield ActionResultS2CPayload.Result.NO_CHANGE;
                    }
                    forEachLoadedPylon(level, network, pylon -> pylon.setBeamsVisible(visible));
                    broadcastNetworkLists(level, manager);
                    yield ActionResultS2CPayload.Result.APPLIED;
                }
                case SET_ACCESS_MODE -> {
                    if (!isValidNetworkId(payload.networkId())) {
                        yield ActionResultS2CPayload.Result.INVALID_REQUEST;
                    }
                    QFNetwork.AccessMode mode = enumValue(QFNetwork.AccessMode.values(), payload.color());
                    if (mode == null) yield ActionResultS2CPayload.Result.INVALID_REQUEST;
                    QFNetwork network = manager.getNetwork(payload.networkId());
                    if (network == null) yield ActionResultS2CPayload.Result.NOT_FOUND;
                    if (!network.isOwner(player.getUUID())) yield ActionResultS2CPayload.Result.NOT_ALLOWED;
                    if (!allowSharedMutation(player, payload.networkId())) {
                        yield ActionResultS2CPayload.Result.RATE_LIMITED;
                    }
                    String clearHash = mode == QFNetwork.AccessMode.PASSWORD ? null : "";
                    if (!manager.updateNetwork(payload.networkId(), player.getUUID(),
                            null, null, mode, clearHash)) {
                        yield ActionResultS2CPayload.Result.NO_CHANGE;
                    }
                    broadcastNetworkLists(level, manager);
                    yield ActionResultS2CPayload.Result.APPLIED;
                }
                case SET_PASSWORD -> {
                    if (!isValidNetworkId(payload.networkId())
                            || !QFPasswordUtil.isValidPassword(payload.name())) {
                        yield ActionResultS2CPayload.Result.INVALID_REQUEST;
                    }
                    if (!allowAction(PASSWORD_RATE_WINDOWS, player,
                            PASSWORD_RATE_WINDOW_TICKS, PASSWORD_RATE_LIMIT)) {
                        yield ActionResultS2CPayload.Result.RATE_LIMITED;
                    }
                    QFNetwork network = manager.getNetwork(payload.networkId());
                    if (network == null) yield ActionResultS2CPayload.Result.NOT_FOUND;
                    if (!network.isOwner(player.getUUID())) yield ActionResultS2CPayload.Result.NOT_ALLOWED;
                    if (network.getAccessMode() != QFNetwork.AccessMode.PASSWORD) {
                        yield ActionResultS2CPayload.Result.INVALID_REQUEST;
                    }
                    if (!manager.updateNetwork(payload.networkId(), player.getUUID(),
                            null, null, null, payload.name())) yield ActionResultS2CPayload.Result.NO_CHANGE;
                    broadcastNetworkLists(level, manager);
                    yield ActionResultS2CPayload.Result.APPLIED;
                }
                case ADD_MEMBER -> handleAddMember(payload, player, manager);
                case REMOVE_MEMBER -> handleRemoveMember(payload, player, manager);
                case JOIN_PASSWORD_NETWORK -> {
                    if (!isValidNetworkId(payload.networkId())
                            || !QFPasswordUtil.isValidPassword(payload.name())) {
                        yield ActionResultS2CPayload.Result.INVALID_REQUEST;
                    }
                    if (!allowAction(PASSWORD_RATE_WINDOWS, player,
                            PASSWORD_RATE_WINDOW_TICKS, PASSWORD_RATE_LIMIT)) {
                        yield ActionResultS2CPayload.Result.RATE_LIMITED;
                    }
                    QFNetwork network = manager.getNetwork(payload.networkId());
                    if (network == null) yield ActionResultS2CPayload.Result.NOT_FOUND;
                    if (network.getAccessMode() != QFNetwork.AccessMode.PASSWORD) {
                        yield ActionResultS2CPayload.Result.INVALID_REQUEST;
                    }
                    boolean alreadyConfigured = network != null && network.canConfigure(player.getUUID());
                    if (manager.joinWithPassword(payload.networkId(), player.getUUID(), payload.name())) {
                        network = manager.getNetwork(payload.networkId());
                        if (network != null) setGadgetNetwork(player, gadget, network);
                        if (alreadyConfigured) {
                            sendNetworkListToPlayer(player, manager);
                        } else {
                            broadcastNetworkLists(level, manager);
                        }
                        yield alreadyConfigured
                                ? ActionResultS2CPayload.Result.NO_CHANGE
                                : ActionResultS2CPayload.Result.APPLIED;
                    }
                    yield ActionResultS2CPayload.Result.WRONG_PASSWORD;
                }
                case INVALID -> ActionResultS2CPayload.Result.INVALID_REQUEST;
            };
            sendActionResult(player, payload.requestId(), result);
        });
    }

    private static ActionResultS2CPayload.Result handleNetworkRecolor(
            NetworkActionC2SPayload payload, ServerPlayer player, ItemStack gadget,
            ServerLevel level, QuantumFluxNetworkManager manager) {
        if (!isValidNetworkId(payload.networkId())
                || !QuantumFluxNetworkManager.isValidColor(payload.color())) {
            return ActionResultS2CPayload.Result.INVALID_REQUEST;
        }

        QFNetwork existing = manager.getNetwork(payload.networkId());
        if (existing == null) return ActionResultS2CPayload.Result.NOT_FOUND;
        if (!existing.isOwner(player.getUUID())) return ActionResultS2CPayload.Result.NOT_ALLOWED;
        if (!allowSharedMutation(player, payload.networkId())) {
            return ActionResultS2CPayload.Result.RATE_LIMITED;
        }

        if (!manager.updateNetwork(payload.networkId(), player.getUUID(),
                null, payload.color(), null, null)) return ActionResultS2CPayload.Result.NO_CHANGE;

        QFNetwork network = manager.getNetwork(payload.networkId());
        if (network == null) return ActionResultS2CPayload.Result.NOT_FOUND;
        forEachLoadedPylon(level, network, pylon -> pylon.setBeamColor(payload.color()));
        if (payload.networkId().equals(gadget.get(QFDataComponents.SELECTED_NETWORK.get()))) {
            gadget.set(QFDataComponents.GADGET_COLOR.get(), payload.color());
            player.inventoryMenu.broadcastChanges();
        }
        broadcastNetworkLists(level, manager);
        return ActionResultS2CPayload.Result.APPLIED;
    }

    private static ActionResultS2CPayload.Result handlePylonAssignment(
            NetworkActionC2SPayload payload, ServerPlayer player,
            ServerLevel level, QuantumFluxNetworkManager manager) {
        if (!isValidNetworkId(payload.networkId())
                || payload.pylonPos() == null) return ActionResultS2CPayload.Result.INVALID_REQUEST;
        if (!isLocalLoadedPosition(player, payload.pylonPos())) {
            return ActionResultS2CPayload.Result.NOT_ALLOWED;
        }
        BlockEntity blockEntity = level.getBlockEntity(payload.pylonPos());
        if (!(blockEntity instanceof QuantumPylonBlockEntity pylon)) {
            return ActionResultS2CPayload.Result.NOT_FOUND;
        }

        UUID currentId = pylon.getNetworkId();
        if (!manager.canConfigurePylon(payload.pylonPos(), currentId, player.getUUID())) {
            return ActionResultS2CPayload.Result.NOT_ALLOWED;
        }

        QuantumFluxNetworkManager.PylonMutationResult result =
                manager.assignPylon(payload.networkId(), payload.pylonPos(), player.getUUID());
        if (result != QuantumFluxNetworkManager.PylonMutationResult.SUCCESS) {
            return mutationResult(result);
        }

        QFNetwork destination = manager.getNetwork(payload.networkId());
        if (destination == null) return ActionResultS2CPayload.Result.NOT_FOUND;
        pylon.setNetworkId(destination.getUuid());
        pylon.setBeamColor(destination.getColor());
        pylon.setBeamStyle(destination.getBeamStyle());
        pylon.setBeamsVisible(destination.isBeamsVisible());
        broadcastNetworkLists(level, manager);
        return ActionResultS2CPayload.Result.APPLIED;
    }

    private static ActionResultS2CPayload.Result handlePylonUnassignment(
            NetworkActionC2SPayload payload, ServerPlayer player,
            ServerLevel level, QuantumFluxNetworkManager manager) {
        if (payload.pylonPos() == null) return ActionResultS2CPayload.Result.INVALID_REQUEST;
        if (!isLocalLoadedPosition(player, payload.pylonPos())) {
            return ActionResultS2CPayload.Result.NOT_ALLOWED;
        }
        BlockEntity blockEntity = level.getBlockEntity(payload.pylonPos());
        if (!(blockEntity instanceof QuantumPylonBlockEntity pylon)) {
            return ActionResultS2CPayload.Result.NOT_FOUND;
        }

        UUID currentId = pylon.getNetworkId();
        if (currentId == null) return ActionResultS2CPayload.Result.NO_CHANGE;
        if (!manager.canConfigurePylon(payload.pylonPos(), currentId, player.getUUID())) {
            return ActionResultS2CPayload.Result.NOT_ALLOWED;
        }

        QuantumFluxNetworkManager.PylonMutationResult result =
                manager.unassignPylon(payload.pylonPos(), player.getUUID());
        if (result != QuantumFluxNetworkManager.PylonMutationResult.SUCCESS) {
            return mutationResult(result);
        }
        pylon.setNetworkId(null);
        broadcastNetworkLists(level, manager);
        return ActionResultS2CPayload.Result.APPLIED;
    }

    private static ActionResultS2CPayload.Result handleAddMember(
            NetworkActionC2SPayload payload, ServerPlayer player,
            QuantumFluxNetworkManager manager) {
        if (!isValidNetworkId(payload.networkId()) || !isValidPlayerName(payload.name())) {
            return ActionResultS2CPayload.Result.INVALID_REQUEST;
        }
        QFNetwork network = manager.getNetwork(payload.networkId());
        if (network == null) return ActionResultS2CPayload.Result.NOT_FOUND;
        if (!network.isOwner(player.getUUID())) return ActionResultS2CPayload.Result.NOT_ALLOWED;
        if (player.getServer() == null) return ActionResultS2CPayload.Result.NOT_FOUND;
        if (!allowAction(PROFILE_LOOKUP_RATE_WINDOWS, player,
                PASSWORD_RATE_WINDOW_TICKS, PASSWORD_RATE_LIMIT)) {
            return ActionResultS2CPayload.Result.RATE_LIMITED;
        }

        Optional<GameProfile> profile = player.getServer().getProfileCache().get(payload.name());
        if (profile.isEmpty()) return ActionResultS2CPayload.Result.PLAYER_NOT_FOUND;
        if (network.getMembers().contains(profile.get().getId())) {
            return ActionResultS2CPayload.Result.NO_CHANGE;
        }
        if (manager.addMember(payload.networkId(), player.getUUID(), profile.get().getId())) {
            broadcastNetworkLists(player.serverLevel(), manager);
            return ActionResultS2CPayload.Result.APPLIED;
        }
        return ActionResultS2CPayload.Result.LIMIT_REACHED;
    }

    private static ActionResultS2CPayload.Result handleRemoveMember(
            NetworkActionC2SPayload payload, ServerPlayer player,
            QuantumFluxNetworkManager manager) {
        if (!isValidNetworkId(payload.networkId())) {
            return ActionResultS2CPayload.Result.INVALID_REQUEST;
        }
        QFNetwork network = manager.getNetwork(payload.networkId());
        if (network == null) return ActionResultS2CPayload.Result.NOT_FOUND;
        if (!network.isOwner(player.getUUID())) return ActionResultS2CPayload.Result.NOT_ALLOWED;

        UUID memberId = parseMemberId(payload.name(), player);
        if (memberId == null) return ActionResultS2CPayload.Result.PLAYER_NOT_FOUND;
        if (manager.removeMember(payload.networkId(), player.getUUID(), memberId)) {
            broadcastNetworkLists(player.serverLevel(), manager);
            return ActionResultS2CPayload.Result.APPLIED;
        }
        return ActionResultS2CPayload.Result.NO_CHANGE;
    }

    private static ActionResultS2CPayload.Result mutationResult(
            QuantumFluxNetworkManager.PylonMutationResult result) {
        return switch (result) {
            case SUCCESS -> ActionResultS2CPayload.Result.APPLIED;
            case NETWORK_NOT_FOUND -> ActionResultS2CPayload.Result.NOT_FOUND;
            case ALREADY_ASSIGNED, NOT_ASSIGNED -> ActionResultS2CPayload.Result.NO_CHANGE;
            case NETWORK_FULL -> ActionResultS2CPayload.Result.LIMIT_REACHED;
            case FORBIDDEN, ASSIGNED_TO_OTHER_NETWORK -> ActionResultS2CPayload.Result.NOT_ALLOWED;
        };
    }

    private static UUID parseMemberId(String value, ServerPlayer player) {
        if (value == null || value.length() > 64) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            if (!isValidPlayerName(value) || player.getServer() == null) return null;
            if (!allowAction(PROFILE_LOOKUP_RATE_WINDOWS, player,
                    PASSWORD_RATE_WINDOW_TICKS, PASSWORD_RATE_LIMIT)) return null;
            return player.getServer().getProfileCache().get(value)
                    .map(GameProfile::getId)
                    .orElse(null);
        }
    }

    private static AuthorizedPylon authorizePylon(ServerPlayer player,
                                                  QuantumFluxNetworkManager manager,
                                                  BlockPos pos) {
        ActionResultS2CPayload.Result positionFailure = validateLocalPosition(player, pos);
        if (positionFailure != null) return new AuthorizedPylon(null, positionFailure);
        BlockEntity blockEntity = player.serverLevel().getBlockEntity(pos);
        if (!(blockEntity instanceof QuantumPylonBlockEntity pylon)) {
            return new AuthorizedPylon(null, ActionResultS2CPayload.Result.NOT_FOUND);
        }

        UUID networkId = pylon.getNetworkId();
        return manager.canConfigurePylon(pos, networkId, player.getUUID())
                ? new AuthorizedPylon(pylon, null)
                : new AuthorizedPylon(null, ActionResultS2CPayload.Result.NOT_ALLOWED);
    }

    private static boolean isLocalLoadedPosition(ServerPlayer player, BlockPos pos) {
        return validateLocalPosition(player, pos) == null;
    }

    private static ActionResultS2CPayload.Result validateLocalPosition(
            ServerPlayer player, BlockPos pos) {
        if (pos == null) return ActionResultS2CPayload.Result.INVALID_REQUEST;
        ServerLevel level = player.serverLevel();
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || !level.hasChunkAt(pos)) return ActionResultS2CPayload.Result.NOT_FOUND;
        if (player.distanceToSqr(
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                > LOCAL_ACTION_DISTANCE_SQUARED) return ActionResultS2CPayload.Result.OUT_OF_RANGE;
        if (!player.mayBuild() || !level.mayInteract(player, pos)) {
            return ActionResultS2CPayload.Result.NOT_ALLOWED;
        }

        InteractionHand hand = isActiveGadget(player.getMainHandItem())
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        var permissionEvent = CommonHooks.onRightClickBlock(
                player, hand, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        return permissionEvent.isCanceled()
                || permissionEvent.getUseBlock().isFalse()
                || permissionEvent.getUseItem().isFalse()
                ? ActionResultS2CPayload.Result.NOT_ALLOWED
                : null;
    }

    private static boolean isLoaded(ServerLevel level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos)
                && level.getWorldBorder().isWithinBounds(pos)
                && level.hasChunkAt(pos);
    }

    private static ItemStack getActiveHeldGadget(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (isActiveGadget(mainHand)) return mainHand;
        ItemStack offHand = player.getOffhandItem();
        return isActiveGadget(offHand) ? offHand : ItemStack.EMPTY;
    }

    private static boolean isActiveGadget(ItemStack stack) {
        return stack.getItem() instanceof QuantumGadgetItem
                && Boolean.TRUE.equals(stack.get(QFDataComponents.GADGET_ACTIVE.get()));
    }

    private static boolean isValidNetworkId(UUID networkId) {
        return networkId != null && !ZERO_UUID.equals(networkId);
    }

    private static boolean isEligibleSender(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator();
    }

    private static boolean isValidPlayerName(String name) {
        return name != null && PLAYER_NAME.matcher(name).matches();
    }

    private static <T> T enumValue(T[] values, int ordinal) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    private static void setGadgetNetwork(ServerPlayer player, ItemStack gadget, QFNetwork network) {
        gadget.set(QFDataComponents.SELECTED_NETWORK.get(), network.getUuid());
        gadget.set(QFDataComponents.SELECTED_NETWORK_DIMENSION.get(),
                player.serverLevel().dimension().location().toString());
        gadget.set(QFDataComponents.GADGET_COLOR.get(), network.getColor());
        player.inventoryMenu.broadcastChanges();
    }

    private static void forEachLoadedPylon(ServerLevel level, QFNetwork network,
                                           java.util.function.Consumer<QuantumPylonBlockEntity> consumer) {
        for (BlockPos pylonPos : network.getPylonPositions()) {
            if (isLoaded(level, pylonPos)
                    && level.getBlockEntity(pylonPos) instanceof QuantumPylonBlockEntity pylon
                    && network.getUuid().equals(pylon.getNetworkId())) {
                consumer.accept(pylon);
            }
        }
    }

    private static boolean allowAction(Map<UUID, RateWindow> windows, ServerPlayer player,
                                       int windowTicks, int limit) {
        long now = player.serverLevel().getGameTime();
        RateWindow window = windows.computeIfAbsent(player.getUUID(), ignored -> new RateWindow(now));
        if (now < window.startedAt || now - window.startedAt >= windowTicks) {
            window.startedAt = now;
            window.count = 0;
        }
        if (window.count >= limit) return false;
        window.count++;

        if (windows.size() > MAX_RATE_LIMIT_ENTRIES) {
            Iterator<UUID> iterator = windows.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        return true;
    }

    private static boolean allowSharedMutation(ServerPlayer player, UUID networkId) {
        long now = player.serverLevel().getGameTime();
        PlayerNetworkKey key = new PlayerNetworkKey(player.getUUID(), networkId);
        RateWindow window = SHARED_MUTATION_RATE_WINDOWS.computeIfAbsent(
                key, ignored -> new RateWindow(now));
        if (now < window.startedAt || now - window.startedAt >= SHARED_MUTATION_RATE_WINDOW_TICKS) {
            window.startedAt = now;
            window.count = 0;
        }
        if (window.count >= SHARED_MUTATION_RATE_LIMIT) return false;
        window.count++;
        if (SHARED_MUTATION_RATE_WINDOWS.size() > MAX_RATE_LIMIT_ENTRIES) {
            Iterator<PlayerNetworkKey> iterator = SHARED_MUTATION_RATE_WINDOWS.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        RateWindow networkWindow = SHARED_NETWORK_MUTATION_RATE_WINDOWS.computeIfAbsent(
                networkId, ignored -> new RateWindow(now));
        if (now < networkWindow.startedAt
                || now - networkWindow.startedAt >= SHARED_MUTATION_RATE_WINDOW_TICKS) {
            networkWindow.startedAt = now;
            networkWindow.count = 0;
        }
        if (networkWindow.count >= SHARED_MUTATION_RATE_LIMIT) return false;
        networkWindow.count++;
        if (SHARED_NETWORK_MUTATION_RATE_WINDOWS.size() > MAX_RATE_LIMIT_ENTRIES) {
            Iterator<UUID> iterator = SHARED_NETWORK_MUTATION_RATE_WINDOWS.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        return true;
    }

    private static void sendActionResult(ServerPlayer player, int requestId,
                                         ActionResultS2CPayload.Result result) {
        if (requestId <= 0) return;
        PacketDistributor.sendToPlayer(player, new ActionResultS2CPayload(requestId, result));
    }

    /** Build and send the current dimension's listable networks to one player. */
    public static void sendNetworkListToPlayer(ServerPlayer player, QuantumFluxNetworkManager manager) {
        reconcileGadgetSelections(player, manager);
        List<QFNetwork> listable = manager.getListableNetworks(player.getUUID());
        listable.sort(java.util.Comparator
                .comparing((QFNetwork network) -> network.canConfigure(player.getUUID())).reversed()
                .thenComparingInt(QFNetwork::getNumericId));
        if (listable.size() > NetworkListSyncS2CPayload.MAX_SYNCED_NETWORKS) {
            listable = listable.subList(0, NetworkListSyncS2CPayload.MAX_SYNCED_NETWORKS);
        }
        List<NetworkListSyncS2CPayload.NetworkSummary> summaries = new ArrayList<>(listable.size());
        ServerLevel level = player.serverLevel();

        for (QFNetwork network : listable) {
            long totalEnergy = 0L;
            long totalCapacity = 0L;
            for (BlockPos pos : network.getPylonPositions()) {
                if (isLoaded(level, pos)
                        && level.getBlockEntity(pos) instanceof QuantumPylonBlockEntity pylon
                        && network.getUuid().equals(pylon.getNetworkId())) {
                    totalEnergy += pylon.getEnergyStorage().getEnergyStored();
                    totalCapacity += pylon.getEnergyStorage().getMaxEnergyStored();
                }
            }
            int energyPercent = totalCapacity > 0L
                    ? (int) Math.clamp(totalEnergy * 100L / totalCapacity, 0L, 100L)
                    : 0;

            boolean owner = network.isOwner(player.getUUID());
            boolean member = network.canConfigure(player.getUUID());
            List<NetworkListSyncS2CPayload.MemberSummary> members = new ArrayList<>();
            if (owner && player.getServer() != null) {
                for (UUID memberId : network.getMembers()) {
                    Optional<GameProfile> profile = player.getServer().getProfileCache().get(memberId);
                    members.add(new NetworkListSyncS2CPayload.MemberSummary(
                            memberId, profile.map(GameProfile::getName)
                                    .orElse(memberId.toString().substring(0, 8))));
                }
                members.sort(java.util.Comparator.comparing(
                        NetworkListSyncS2CPayload.MemberSummary::displayName,
                        String.CASE_INSENSITIVE_ORDER));
            }

            summaries.add(new NetworkListSyncS2CPayload.NetworkSummary(
                    network.getUuid(), network.getNumericId(), network.getName(), network.getColor(),
                    network.getPylonPositions().size(), energyPercent,
                    network.getAccessMode().ordinal(), owner, member,
                    network.getBeamStyle().ordinal(), network.isBeamsVisible(), members));
        }

        PacketDistributor.sendToPlayer(player, new NetworkListSyncS2CPayload(summaries));
        sendSelectedNetworkTelemetry(player, manager, new HashMap<>());
    }

    /** Queues one dimension-scoped refresh at the end of the current server tick. */
    public static void broadcastNetworkLists(ServerLevel level, QuantumFluxNetworkManager manager) {
        PENDING_NETWORK_LIST_BROADCASTS.add(level);
    }

    /** Coalesces all mutations in a tick so one player cannot amplify list rebuilds. */
    public static void flushNetworkListBroadcasts() {
        if (PENDING_NETWORK_LIST_BROADCASTS.isEmpty()) return;
        Iterator<ServerLevel> iterator = PENDING_NETWORK_LIST_BROADCASTS.iterator();
        while (iterator.hasNext()) {
            ServerLevel level = iterator.next();
            long now = level.getGameTime();
            long previous = LAST_NETWORK_LIST_BROADCAST.getOrDefault(level, Long.MIN_VALUE / 2);
            if (now >= previous && now - previous < 5L) continue;
            iterator.remove();
            LAST_NETWORK_LIST_BROADCAST.put(level, now);
            QuantumFluxNetworkManager manager = QuantumFluxNetworkManager.get(level);
            for (ServerPlayer onlinePlayer : level.players()) {
                sendNetworkListToPlayer(onlinePlayer, manager);
            }
        }
    }

    /** Keeps unloaded-pylon fallback telemetry fresh while the gadget is actively in use. */
    public static void refreshActiveGadgetNetworkLists(net.minecraft.server.MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (ServerLevel level : server.getAllLevels()) {
            QuantumFluxNetworkManager manager = QuantumFluxNetworkManager.get(level);
            Map<UUID, NetworkTelemetryPayload> snapshots = new HashMap<>();
            for (ServerPlayer player : level.players()) {
                if (getActiveHeldGadget(player).isEmpty()) continue;
                if (server.getTickCount() % 100 == 0) sendNetworkListToPlayer(player, manager);
                else sendSelectedNetworkTelemetry(player, manager, snapshots);
            }
        }
    }

    private static void sendSelectedNetworkTelemetry(ServerPlayer player, QuantumFluxNetworkManager manager,
                                                      Map<UUID, NetworkTelemetryPayload> snapshots) {
        NetworkTelemetryPayload payload = selectedNetworkTelemetry(player, manager, snapshots);
        if (payload != null) PacketDistributor.sendToPlayer(player, payload);
    }

    @org.jetbrains.annotations.Nullable
    static NetworkTelemetryPayload selectedNetworkTelemetry(ServerPlayer player, QuantumFluxNetworkManager manager,
                                                              Map<UUID, NetworkTelemetryPayload> snapshots) {
        ItemStack gadget = getActiveHeldGadget(player);
        if (gadget.isEmpty()) return null;
        UUID selected = gadget.get(QFDataComponents.SELECTED_NETWORK.get());
        String dimension = gadget.get(QFDataComponents.SELECTED_NETWORK_DIMENSION.get());
        if (selected == null || !player.level().dimension().location().toString().equals(dimension)) return null;
        QFNetwork network = manager.getNetwork(selected);
        if (network == null || !network.canAccess(player.getUUID())) return null;
        return snapshots.computeIfAbsent(selected,
                ignored -> NetworkTelemetryPayload.capture(player.serverLevel(), network));
    }

    private static void reconcileGadgetSelections(ServerPlayer player,
                                                   QuantumFluxNetworkManager manager) {
        String currentDimension = player.serverLevel().dimension().location().toString();
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            changed |= reconcileGadgetSelection(
                    player.getInventory().getItem(slot), player, manager, currentDimension);
        }
        changed |= reconcileGadgetSelection(
                player.containerMenu.getCarried(), player, manager, currentDimension);
        if (changed) player.inventoryMenu.broadcastChanges();
    }

    private static boolean reconcileGadgetSelection(ItemStack stack, ServerPlayer player,
                                                    QuantumFluxNetworkManager manager,
                                                    String currentDimension) {
        if (!(stack.getItem() instanceof QuantumGadgetItem)) return false;
        UUID selected = stack.get(QFDataComponents.SELECTED_NETWORK.get());
        if (selected == null) return false;
        String selectedDimension = stack.get(QFDataComponents.SELECTED_NETWORK_DIMENSION.get());
        QFNetwork network = manager.getNetwork(selected);
        if (currentDimension.equals(selectedDimension)
                && network != null && network.canAccess(player.getUUID())) {
            Integer gadgetColor = stack.get(QFDataComponents.GADGET_COLOR.get());
            if (gadgetColor == null || gadgetColor.intValue() != network.getColor()) {
                stack.set(QFDataComponents.GADGET_COLOR.get(), network.getColor());
                return true;
            }
            return false;
        }
        stack.remove(QFDataComponents.SELECTED_NETWORK.get());
        stack.remove(QFDataComponents.SELECTED_NETWORK_DIMENSION.get());
        stack.set(QFDataComponents.GADGET_COLOR.get(), QFConfig.DEFAULT_BEAM_COLOR.get() & 0xFFFFFF);
        return true;
    }

    private static final class RateWindow {
        private long startedAt;
        private int count;

        private RateWindow(long startedAt) {
            this.startedAt = startedAt;
        }
    }

    private record AuthorizedPylon(QuantumPylonBlockEntity pylon,
                                   ActionResultS2CPayload.Result failure) {}

    private record PlayerNetworkKey(UUID playerId, UUID networkId) {}
}
