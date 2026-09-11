package com.zerotheabsolute.quantumflux.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.zerotheabsolute.quantumflux.client.ClientDataCache;
import com.zerotheabsolute.quantumflux.client.ClientNetworkCache;
import com.zerotheabsolute.quantumflux.client.PylonStatus;
import com.zerotheabsolute.quantumflux.client.tutorial.FluxTutorialScreen;
import com.zerotheabsolute.quantumflux.init.QFDataComponents;
import com.zerotheabsolute.quantumflux.item.QuantumGadgetItem;
import com.zerotheabsolute.quantumflux.network.GadgetActionPayload;
import com.zerotheabsolute.quantumflux.network.ActionResultS2CPayload;
import com.zerotheabsolute.quantumflux.network.NetworkActionC2SPayload;
import com.zerotheabsolute.quantumflux.network.NetworkListSyncS2CPayload;
import com.zerotheabsolute.quantumflux.network.PylonSyncPayload;
import com.zerotheabsolute.quantumflux.network.NetworkTelemetryPayload;
import com.zerotheabsolute.quantumflux.network.data.QuantumFluxNetworkManager;
import com.zerotheabsolute.quantumflux.util.BeamStyle;
import com.zerotheabsolute.quantumflux.util.GadgetAction;
import com.zerotheabsolute.quantumflux.util.PriorityMode;
import com.zerotheabsolute.quantumflux.util.RedstoneMode;
import com.zerotheabsolute.quantumflux.util.QFPasswordUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import com.zerotheabsolute.quantumflux.network.ForgePacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.zerotheabsolute.quantumflux.client.screen.GadgetScreenModel.*;
import static com.zerotheabsolute.quantumflux.client.screen.GadgetScreenTheme.*;

/**
 * Server-backed network management screen for the Flux Gadget.
 * Widgets own all actionable controls so keyboard focus and narration work by default.
 */
public final class GadgetScreen extends Screen {

    private static final ColorChoice[] COLORS = {
            new ColorChoice(0x00FFFF, "cyan"),
            new ColorChoice(0x00CED1, "turquoise"),
            new ColorChoice(0x4169E1, "blue"),
            new ColorChoice(0x9B59B6, "purple"),
            new ColorChoice(0xFF69B4, "pink"),
            new ColorChoice(0xFF4444, "red"),
            new ColorChoice(0xFF8C00, "orange"),
            new ColorChoice(0xFFFFFF, "white")
    };

    enum Tab { NETWORKS, OVERVIEW, PYLONS, MEMBERS, SETTINGS }
    enum NetworkForm { NONE, CREATE, JOIN }
    enum SettingsPage { GENERAL, BEAMS, ACCESS }

    int panelX;
    int panelY;
    int panelWidth;
    int panelHeight;
    int contentTop;
    int contentBottom;
    private int renderMouseX, renderMouseY;

    Tab selectedTab = Tab.NETWORKS;
    SettingsPage settingsPage = SettingsPage.GENERAL;
    NetworkForm networkForm = NetworkForm.NONE;
    private boolean initialized;

    int networkScroll;
    private int pylonScroll;
    int connectionScroll;
    private int memberScroll;
    private int newColor = 0x00FFFF;
    BlockPos selectedPylonPos;
    UUID joiningNetworkId;
    private UUID requestedSelectionId;
    private UUID draftNetworkId;
    private boolean deleteArmed;
    private int deleteArmTicks;
    private boolean unlinkAllArmed;

    private String createNameDraft = "";
    private String renameDraft = "";
    private String passwordDraft = "";
    private String memberDraft = "";
    private String joinPasswordDraft = "";

    private EditBox createNameField;
    private EditBox renameField;
    private MaskedEditBox passwordField;
    private EditBox memberField;
    private MaskedEditBox joinPasswordField;

    private final List<NetworkRow> networkRows = new ArrayList<>();
    private final List<PylonRow> pylonRows = new ArrayList<>();
    private final List<ConnectionRow> connectionRows = new ArrayList<>();
    List<NetworkTelemetryPayload.PylonSummary> pylonSnapshot = List.of();

    Component statusMessage;
    int statusColor = TEXT_DIM;
    private int statusTicks;
    private long widgetSignature;
    private int nextRequestId = 1;
    private final Map<Integer, NetworkActionC2SPayload.Action> pendingRequests = new LinkedHashMap<>();

    public GadgetScreen() {
        super(Component.translatable("screen.quantumflux.title"));
    }

    public GadgetScreen(BlockPos pylonPos) {
        this();
        selectedPylonPos = pylonPos;
    }

    @Override
    protected void init() {
        calculateLayout();
        if (!initialized) {
            createNameDraft = Component.translatable("screen.quantumflux.create.default_name").getString();
            selectedTab = getSelectedNetworkId() == null ? Tab.NETWORKS
                    : selectedPylonPos == null ? Tab.OVERVIEW : Tab.PYLONS;
            initialized = true;
        }
        rebuildWidgets(false);
    }

    private void calculateLayout() {
        panelWidth = Math.min(MAX_WIDTH, Math.max(1, width - OUTER_MARGIN * 2));
        panelHeight = Math.min(MAX_HEIGHT, Math.max(1, height - OUTER_MARGIN * 2));
        if (panelWidth < 240) panelWidth = Math.max(1, width - 4);
        if (panelHeight < 190) panelHeight = Math.max(1, height - 4);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        contentTop = panelY + HEADER_HEIGHT + TAB_HEIGHT + CONTENT_GAP;
        contentBottom = panelY + panelHeight - STATUS_HEIGHT - CONTENT_GAP;
    }

    private void rebuildWidgets(boolean focusPrimary) {
        clearWidgets();
        networkRows.clear();
        pylonRows.clear();
        connectionRows.clear();
        createNameField = null;
        renameField = null;
        passwordField = null;
        memberField = null;
        joinPasswordField = null;

        addTabButtons();
        Button guide = addRenderableWidget(Button.builder(Component.literal("?"),
                ignored -> FluxTutorialScreen.open(this, selectedTab == Tab.NETWORKS ? 0
                        : selectedTab == Tab.PYLONS || selectedTab == Tab.OVERVIEW ? 3 : 2))
                .bounds(panelX + panelWidth - 26, panelY + 4, 20, 17)
                .createNarration(defaultNarration -> Component.translatable("screen.quantumflux.guide.title")).build());
        guide.setTooltip(Tooltip.create(Component.translatable("screen.quantumflux.guide.title")));

        switch (selectedTab) {
            case NETWORKS -> buildNetworkWidgets(focusPrimary);
            case OVERVIEW -> buildOverviewWidgets();
            case PYLONS -> buildPylonWidgets();
            case MEMBERS -> {
                var network = selectedNetwork();
                if (network != null) {
                    resetDraftForNetwork(network);
                    buildMemberSettings(network, focusPrimary);
                }
            }
            case SETTINGS -> buildSettingsWidgets(focusPrimary);
        }
        widgetSignature = computeWidgetSignature();
    }

    private void addTabButtons() {
        UUID selectedId = getSelectedNetworkId();
        int gap = 2;
        int count = Tab.values().length;
        int available = panelWidth - OUTER_MARGIN * 2 - gap * (count - 1);
        int tabWidth = available / count;
        int x = panelX + OUTER_MARGIN;
        int y = panelY + HEADER_HEIGHT;
        for (Tab tab : Tab.values()) {
            int index = tab.ordinal();
            int widthForTab = index == count - 1 ? panelX + panelWidth - OUTER_MARGIN - x : tabWidth;
            Button button = addButton(tabLabel(tab), x, y, widthForTab, TAB_HEIGHT,
                    ignored -> selectTab(tab));
            button.active = tab != selectedTab && (tab == Tab.NETWORKS || selectedId != null);
            if (tab == Tab.MEMBERS && (selectedNetwork() == null || !selectedNetwork().isOwner())) {
                button.active = false;
                button.setTooltip(Tooltip.create(ownerOnly()));
            }
            x += widthForTab + gap;
        }
    }

    private void buildNetworkWidgets(boolean focusPrimary) {
        int x = contentLeft();
        int width = contentWidth();
        int y = contentTop + 4;

        if (networkForm == NetworkForm.CREATE) {
            createNameField = new EditBox(font, x, y + 22, width, BUTTON_HEIGHT,
                    Component.translatable("screen.quantumflux.create.name"));
            createNameField.setMaxLength(QuantumFluxNetworkManager.MAX_NETWORK_NAME_LENGTH * 2);
            createNameField.setHint(Component.translatable("screen.quantumflux.create.name_hint"));
            createNameField.setValue(createNameDraft);
            createNameField.setResponder(value -> createNameDraft = value);
            addRenderableWidget(createNameField);

            int colorY = y + 62;
            int afterColors = addColorButtons(colorY, newColor, choice -> {
                newColor = choice.color();
                rebuildWidgets(false);
            }, true);
            int buttonY = Math.min(afterColors + 8, contentBottom - BUTTON_HEIGHT);
            int half = (width - 4) / 2;
            addButton(Component.translatable("screen.quantumflux.action.create"), x, buttonY, half,
                    BUTTON_HEIGHT, ignored -> submitCreate());
            addButton(Component.translatable("gui.cancel"), x + half + 4, buttonY, width - half - 4,
                    BUTTON_HEIGHT, ignored -> cancelNetworkForm());
            if (focusPrimary) setInitialFocus(createNameField);
            return;
        }

        if (networkForm == NetworkForm.JOIN) {
            NetworkListSyncS2CPayload.NetworkSummary network = ClientNetworkCache.getNetwork(joiningNetworkId);
            if (network == null) {
                cancelNetworkForm();
                return;
            }
            joinPasswordField = new MaskedEditBox(font, x, y + 38, width, BUTTON_HEIGHT,
                    Component.translatable("screen.quantumflux.password"));
            joinPasswordField.setMaxLength(64);
            joinPasswordField.setHint(Component.translatable("screen.quantumflux.password.hint"));
            joinPasswordField.setValue(joinPasswordDraft);
            joinPasswordField.setResponder(value -> joinPasswordDraft = value);
            addRenderableWidget(joinPasswordField);
            int buttonY = y + 66;
            int half = (width - 4) / 2;
            addButton(Component.translatable("screen.quantumflux.action.join"), x, buttonY, half,
                    BUTTON_HEIGHT, ignored -> submitJoin());
            addButton(Component.translatable("gui.cancel"), x + half + 4, buttonY, width - half - 4,
                    BUTTON_HEIGHT, ignored -> cancelNetworkForm());
            if (focusPrimary) setInitialFocus(joinPasswordField);
            return;
        }

        int newWidth = Math.min(112, Math.max(78, width / 3));
        Button newButton = addButton(Component.translatable("screen.quantumflux.networks.new"),
                x + width - newWidth, y, newWidth, BUTTON_HEIGHT, ignored -> openCreateForm());
        long owned = ClientNetworkCache.getNetworks().stream().filter(NetworkListSyncS2CPayload.NetworkSummary::isOwner).count();
        newButton.active = owned < QuantumFluxNetworkManager.MAX_NETWORKS_PER_OWNER;
        if (!newButton.active) {
            newButton.setTooltip(Tooltip.create(Component.translatable("screen.quantumflux.networks.limit",
                    QuantumFluxNetworkManager.MAX_NETWORKS_PER_OWNER)));
        }

        List<NetworkListSyncS2CPayload.NetworkSummary> networks = sortedNetworks();
        int listY = networkListY();
        int maxVisible = visibleNetworkRows();
        networkScroll = clampScroll(networkScroll, networks.size(), maxVisible);
        int end = Math.min(networks.size(), networkScroll + maxVisible);
        for (int i = networkScroll; i < end; i++) {
            NetworkListSyncS2CPayload.NetworkSummary network = networks.get(i);
            int rowY = listY + (i - networkScroll) * (BUTTON_HEIGHT + ROW_GAP);
            Button row = addButton(networkRowLabel(network), x, rowY, width, BUTTON_HEIGHT,
                    ignored -> chooseNetwork(network));
            row.setTooltip(Tooltip.create(networkTooltip(network)));
            networkRows.add(new NetworkRow(network.uuid(), row));
        }
    }

    private int addColorButtons(int y, int selectedColor,
                                java.util.function.Consumer<ColorChoice> onChoice, boolean createMode) {
        int x = contentLeft();
        int width = contentWidth();
        int gap = 2;
        int widestLabel = 0;
        for (ColorChoice choice : COLORS) {
            widestLabel = Math.max(widestLabel,
                    font.width(Component.translatable("screen.quantumflux.color." + choice.key())) + 20);
        }
        int columns = width >= widestLabel * 8 + gap * 7 || contentBottom - contentTop < 140 ? 8 : 4;
        int buttonWidth = (width - gap * (columns - 1)) / columns;
        for (int i = 0; i < COLORS.length; i++) {
            ColorChoice choice = COLORS[i];
            int col = i % columns;
            int row = i / columns;
            int bx = x + col * (buttonWidth + gap);
            int by = y + row * (BUTTON_HEIGHT + gap);
            MutableComponent label = Component.literal("\u25a0 ")
                    .withStyle(style -> style.withColor(choice.color()))
                    .append(Component.translatable("screen.quantumflux.color." + choice.key())
                            .withStyle(style -> style.withColor(0xFFFFFF)));
            Button button = addButton(label, bx, by, buttonWidth, BUTTON_HEIGHT,
                    ignored -> onChoice.accept(choice));
            button.active = choice.color() != selectedColor;
            String action = createMode ? "screen.quantumflux.color.choose" : "screen.quantumflux.color.set";
            button.setTooltip(Tooltip.create(Component.translatable(action,
                    Component.translatable("screen.quantumflux.color." + choice.key()))));
        }
        int rows = (COLORS.length + columns - 1) / columns;
        return y + rows * BUTTON_HEIGHT + (rows - 1) * gap;
    }

    private void buildOverviewWidgets() {
        UUID selectedId = getSelectedNetworkId();
        if (selectedId == null || ClientNetworkCache.getNetwork(selectedId) == null) {
            addButton(Component.translatable("screen.quantumflux.action.choose_network"),
                    contentLeft(), contentBottom - BUTTON_HEIGHT, contentWidth(), BUTTON_HEIGHT,
                    ignored -> selectTab(Tab.NETWORKS));
            return;
        }
        int historyY = contentTop + 94;
        int historyHeight = contentBottom - BUTTON_HEIGHT - 4 - historyY;
        if (historyHeight >= 24) {
            addRenderableWidget(new ThroughputHistoryWidget(contentLeft(), historyY,
                    contentWidth(), historyHeight, selectedId));
        }
        int half = (contentWidth() - 4) / 2;
        addButton(Component.translatable("screen.quantumflux.action.manage_pylons"),
                contentLeft(), contentBottom - BUTTON_HEIGHT, half, BUTTON_HEIGHT,
                ignored -> selectTab(Tab.PYLONS));
        addButton(Component.translatable("screen.quantumflux.action.network_settings"),
                contentLeft() + half + 4, contentBottom - BUTTON_HEIGHT,
                contentWidth() - half - 4, BUTTON_HEIGHT, ignored -> selectTab(Tab.SETTINGS));
    }

    private void buildPylonWidgets() {
        UUID selectedId = getSelectedNetworkId();
        if (selectedId == null) return;
        if (selectedPylonPos != null) {
            buildPylonDetailWidgets(selectedId);
            return;
        }

        pylonSnapshot = sortedPylons(selectedId);
        int x = contentLeft();
        int listY = contentTop + 27;
        int maxVisible = Math.max(1, (contentBottom - listY) / (BUTTON_HEIGHT + ROW_GAP));
        pylonScroll = clampScroll(pylonScroll, pylonSnapshot.size(), maxVisible);
        int end = Math.min(pylonSnapshot.size(), pylonScroll + maxVisible);
        for (int i = pylonScroll; i < end; i++) {
            NetworkTelemetryPayload.PylonSummary entry = pylonSnapshot.get(i);
            int rowY = listY + (i - pylonScroll) * (BUTTON_HEIGHT + ROW_GAP);
            Button row = addButton(pylonRowLabel(selectedId, entry), x, rowY,
                    contentWidth(), BUTTON_HEIGHT, ignored -> openPylon(entry.pos()));
            row.setTooltip(Tooltip.create(pylonSummaryHint(entry)));
            pylonRows.add(new PylonRow(entry.pos(), row));
        }
    }

    private void buildPylonDetailWidgets(UUID selectedId) {
        int x = contentLeft();
        ClientDataCache.PylonClientData data = ClientDataCache.get(selectedPylonPos);
        addButton(Component.translatable("gui.back"), x, contentTop + 3,
                Math.min(84, contentWidth() / 3), BUTTON_HEIGHT, ignored -> closePylon());
        if (data == null || !selectedId.equals(data.networkId)) return;

        boolean configurable = canConfigureSelectedNetwork();
        boolean near = isNear(selectedPylonPos);
        boolean canMutate = configurable && near;
        int backWidth = Math.min(84, contentWidth() / 3);
        Button redstone = addButton(Component.translatable("screen.quantumflux.redstone.control", data.redstoneMode.label()),
                x + backWidth + 4, contentTop + 3, contentWidth() - backWidth - 4, BUTTON_HEIGHT,
                ignored -> {
                    RedstoneMode next = RedstoneMode.values()[(data.redstoneMode.ordinal() + 1) % RedstoneMode.values().length];
                    sendGadgetAction(new GadgetActionPayload(selectedPylonPos, GadgetAction.SET_REDSTONE_MODE,
                            BlockPos.ZERO, next.ordinal(), 0));
                });
        redstone.active = canMutate;
        redstone.setTooltip(Tooltip.create(canMutate
                ? Component.translatable("screen.quantumflux.redstone.hint")
                : mutationDisabledReason(configurable, near)));
        int priorityY = contentTop + 55;
        int gap = 2;
        int priorityWidth = (contentWidth() - gap * 2) / 3;
        for (PriorityMode mode : PriorityMode.values()) {
            int bx = x + mode.ordinal() * (priorityWidth + gap);
            Button button = addButton(priorityLabel(mode), bx, priorityY, priorityWidth, BUTTON_HEIGHT,
                    ignored -> setPriority(mode));
            button.active = canMutate && data.priorityMode != mode;
            if (!canMutate) button.setTooltip(Tooltip.create(mutationDisabledReason(configurable, near)));
        }

        int listY = connectionListY();
        int maxVisible = visibleConnectionRows();
        connectionScroll = clampScroll(connectionScroll, data.connections.size(), maxVisible);
        int end = Math.min(data.connections.size(), connectionScroll + maxVisible);
        for (int i = connectionScroll; i < end; i++) {
            PylonSyncPayload.ConnectionEntry connection = data.connections.get(i);
            int rowY = listY + (i - connectionScroll) * (BUTTON_HEIGHT + ROW_GAP);
            int connectionIndex = i;
            Button row = addButton(connectionRowLabel(connection), x, rowY, contentWidth(), BUTTON_HEIGHT,
                    ignored -> unlinkConnection(connection.pos()));
            row.active = canMutate;
            row.setTooltip(Tooltip.create(connectionTooltip(connection, canMutate
                    ? Component.translatable("screen.quantumflux.pylons.unlink_tooltip", position(connection.pos()))
                    : mutationDisabledReason(configurable, near))));
            connectionRows.add(new ConnectionRow(connectionIndex, row));
        }

        if (!unlinkAllArmed) {
            int upgradeWidth = data.connections.isEmpty() ? contentWidth() : (contentWidth() - 4) / 2;
            Button upgrades = addButton(Component.translatable("screen.quantumflux.upgrades.open"),
                    x + contentWidth() - upgradeWidth, contentBottom - BUTTON_HEIGHT, upgradeWidth, BUTTON_HEIGHT,
                    ignored -> sendGadgetAction(new GadgetActionPayload(
                            selectedPylonPos, GadgetAction.OPEN_UPGRADES, BlockPos.ZERO, 0, 0)));
            upgrades.active = canMutate;
            upgrades.setTooltip(Tooltip.create(canMutate
                    ? Component.translatable("screen.quantumflux.upgrades.open_hint")
                    : mutationDisabledReason(configurable, near)));
        }

        if (!data.connections.isEmpty()) {
            int actionY = contentBottom - BUTTON_HEIGHT;
            if (unlinkAllArmed) {
                int half = (contentWidth() - 4) / 2;
                Button confirm = addButton(Component.translatable("screen.quantumflux.pylons.confirm_unlink_all"),
                        x, actionY, half, BUTTON_HEIGHT, ignored -> unlinkAll());
                confirm.active = canMutate;
                addButton(Component.translatable("gui.cancel"), x + half + 4, actionY,
                        contentWidth() - half - 4, BUTTON_HEIGHT, ignored -> {
                            unlinkAllArmed = false;
                            rebuildWidgets(false);
                        });
            } else {
                Button unlink = addButton(Component.translatable("screen.quantumflux.pylons.unlink_all"),
                        x, actionY, (contentWidth() - 4) / 2, BUTTON_HEIGHT, ignored -> {
                            unlinkAllArmed = true;
                            rebuildWidgets(false);
                        });
                unlink.active = canMutate;
                if (!canMutate) unlink.setTooltip(Tooltip.create(mutationDisabledReason(configurable, near)));
            }
        }
    }

    int connectionListY() {
        return contentTop + 104;
    }

    int visibleConnectionRows() {
        return Math.max(0, (contentBottom - BUTTON_HEIGHT - 3 - connectionListY())
                / (BUTTON_HEIGHT + ROW_GAP));
    }

    private void buildSettingsWidgets(boolean focusPrimary) {
        UUID selectedId = getSelectedNetworkId();
        NetworkListSyncS2CPayload.NetworkSummary network = selectedId == null
                ? null : ClientNetworkCache.getNetwork(selectedId);
        if (network == null) return;

        int x = contentLeft();
        int gap = 2;
        int sectionWidth = (contentWidth() - gap * 2) / 3;
        Button general = addButton(Component.translatable("screen.quantumflux.settings.general"),
                x, contentTop + 3, sectionWidth, BUTTON_HEIGHT,
                ignored -> selectSettingsPage(SettingsPage.GENERAL));
        Button beams = addButton(Component.translatable("screen.quantumflux.settings.beams"),
                x + sectionWidth + gap, contentTop + 3, sectionWidth, BUTTON_HEIGHT,
                ignored -> selectSettingsPage(SettingsPage.BEAMS));
        Button access = addButton(Component.translatable("screen.quantumflux.settings.access"),
                x + (sectionWidth + gap) * 2, contentTop + 3,
                contentWidth() - sectionWidth * 2 - gap * 2, BUTTON_HEIGHT,
                ignored -> selectSettingsPage(SettingsPage.ACCESS));
        general.active = settingsPage != SettingsPage.GENERAL;
        beams.active = settingsPage != SettingsPage.BEAMS;
        access.active = settingsPage != SettingsPage.ACCESS;
        resetDraftForNetwork(network);
        switch (settingsPage) {
            case GENERAL -> buildGeneralSettings(network, focusPrimary);
            case BEAMS -> buildBeamSettings(network);
            case ACCESS -> buildAccessSettings(network, focusPrimary);
        }
    }

    private void resetDraftForNetwork(NetworkListSyncS2CPayload.NetworkSummary network) {
        if (!network.uuid().equals(draftNetworkId)) {
            draftNetworkId = network.uuid();
            renameDraft = network.name();
            passwordDraft = "";
            memberDraft = "";
            memberScroll = 0;
        }

    }

    private void buildGeneralSettings(NetworkListSyncS2CPayload.NetworkSummary network, boolean focusPrimary) {
        int x = contentLeft();
        int width = contentWidth();
        int y = settingsNameY();
        int saveWidth = Math.min(72, Math.max(56, width / 5));
        renameField = new EditBox(font, x, y, width - saveWidth - 4, BUTTON_HEIGHT,
                Component.translatable("screen.quantumflux.settings.network_name"));
        renameField.setMaxLength(QuantumFluxNetworkManager.MAX_NETWORK_NAME_LENGTH * 2);
        renameField.setValue(renameDraft);
        renameField.setResponder(value -> renameDraft = value);
        renameField.setEditable(network.isOwner());
        renameField.active = network.isOwner();
        addRenderableWidget(renameField);
        Button save = addButton(Component.translatable("screen.quantumflux.action.save"),
                x + width - saveWidth, y, saveWidth, BUTTON_HEIGHT, ignored -> submitRename(network));
        save.active = network.isOwner();
        if (!network.isOwner()) save.setTooltip(Tooltip.create(ownerOnly()));

        int colorY = settingsColorY();
        int afterColors = addColorButtons(colorY, network.color(), choice -> recolor(network, choice), false);
        if (!network.isOwner()) {
            for (var child : children()) {
                if (child instanceof Button button && button.getY() >= colorY && button.getBottom() <= afterColors) {
                    button.active = false;
                    button.setTooltip(Tooltip.create(ownerOnly()));
                }
            }
        }

        if (focusPrimary && network.isOwner()) setInitialFocus(renameField);
    }

    private void buildBeamSettings(NetworkListSyncS2CPayload.NetworkSummary network) {
        int x = contentLeft();
        int width = contentWidth();
        boolean canConfigure = network.isMember();
        int beamY = settingsBeamY();
        int gap = 2;
        int beamWidth = (width - gap * 2) / 3;
        for (BeamStyle style : BeamStyle.values()) {
            int bx = x + style.ordinal() * (beamWidth + gap);
            Button button = addButton(beamStyleLabel(style), bx, beamY, beamWidth, BUTTON_HEIGHT,
                    ignored -> setNetworkBeamStyle(network.uuid(), style));
            button.active = canConfigure && network.beamStyle() != style.ordinal();
            if (!canConfigure) {
                button.setTooltip(Tooltip.create(settingsDisabledReason(network)));
            }
        }
        int visibleY = beamY + BUTTON_HEIGHT + 7;
        Component visibility = Component.translatable(network.beamsVisible()
                        ? "screen.quantumflux.settings.beams_on" : "screen.quantumflux.settings.beams_off");
        Button visible = addButton(visibility, x, visibleY, width, BUTTON_HEIGHT,
                ignored -> toggleNetworkBeams(network.uuid()));
        visible.active = canConfigure;
        if (!canConfigure) {
            visible.setTooltip(Tooltip.create(settingsDisabledReason(network)));
        }
    }

    private void buildAccessSettings(NetworkListSyncS2CPayload.NetworkSummary network, boolean focusPrimary) {
        int x = contentLeft();
        int width = contentWidth();
        int modeY = settingsNameY();
        int gap = 2;
        int modeWidth = (width - gap * 2) / 3;
        for (int i = 0; i < 3; i++) {
            int mode = i;
            Button button = addButton(accessModeLabel(i), x + i * (modeWidth + gap), modeY,
                    modeWidth, BUTTON_HEIGHT, ignored -> setAccessMode(network.uuid(), mode));
            button.active = network.isOwner() && network.accessMode() != i;
            if (!network.isOwner()) button.setTooltip(Tooltip.create(ownerOnly()));
        }

        int nextY = modeY + BUTTON_HEIGHT + 7;
        if (network.isOwner() && network.accessMode() == 2) {
            int setWidth = Math.min(72, Math.max(56, width / 5));
            passwordField = new MaskedEditBox(font, x, nextY, width - setWidth - 4, BUTTON_HEIGHT,
                    Component.translatable("screen.quantumflux.password"));
            passwordField.setMaxLength(64);
            passwordField.setHint(Component.translatable("screen.quantumflux.password.new_hint"));
            passwordField.setValue(passwordDraft);
            passwordField.setResponder(value -> passwordDraft = value);
            addRenderableWidget(passwordField);
            addButton(Component.translatable("screen.quantumflux.action.set"),
                    x + width - setWidth, nextY, setWidth, BUTTON_HEIGHT,
                    ignored -> submitPassword(network.uuid()));
            nextY += BUTTON_HEIGHT + 7;
        }

        int deleteY = contentBottom - BUTTON_HEIGHT;
        if (network.isOwner() && deleteArmed) {
            int half = (width - 4) / 2;
            Button confirm = addButton(Component.translatable("screen.quantumflux.delete.confirm", network.name()),
                    x, deleteY, half, BUTTON_HEIGHT, ignored -> confirmDelete(network.uuid()));
            confirm.setTooltip(Tooltip.create(Component.translatable("screen.quantumflux.delete.irreversible")));
            addButton(Component.translatable("gui.cancel"), x + half + 4, deleteY,
                    width - half - 4, BUTTON_HEIGHT, ignored -> disarmDelete());
        } else if (network.isOwner()) {
            Button delete = addButton(Component.translatable("screen.quantumflux.delete.action"),
                    x, deleteY, width, BUTTON_HEIGHT, ignored -> armDelete());
            delete.setTooltip(Tooltip.create(Component.translatable("screen.quantumflux.delete.warning")));
        }
        if (focusPrimary && passwordField != null) setInitialFocus(passwordField);
    }

    private void buildMemberSettings(NetworkListSyncS2CPayload.NetworkSummary network, boolean focusPrimary) {
        if (!network.isOwner()) return;
        int x = contentLeft();
        int width = contentWidth();
        int addY = contentBottom - BUTTON_HEIGHT;
        int addWidth = Math.min(72, Math.max(56, width / 5));
        memberField = new EditBox(font, x, addY, width - addWidth - 4, BUTTON_HEIGHT,
                Component.translatable("screen.quantumflux.members.player_name"));
        memberField.setMaxLength(16);
        memberField.setFilter(value -> value.matches("[A-Za-z0-9_]{0,16}"));
        memberField.setHint(Component.translatable("screen.quantumflux.members.player_hint"));
        memberField.setValue(memberDraft);
        memberField.setResponder(value -> memberDraft = value);
        addRenderableWidget(memberField);
        addButton(Component.translatable("screen.quantumflux.members.add"),
                x + width - addWidth, addY, addWidth, BUTTON_HEIGHT,
                ignored -> submitMember(network.uuid()));

        int listY = contentTop + 36;
        int maxVisible = Math.max(0, (addY - listY - 4) / (BUTTON_HEIGHT + ROW_GAP));
        List<NetworkListSyncS2CPayload.MemberSummary> members = network.members();
        memberScroll = clampScroll(memberScroll, members.size(), maxVisible);
        int end = Math.min(members.size(), memberScroll + maxVisible);
        for (int i = memberScroll; i < end; i++) {
            NetworkListSyncS2CPayload.MemberSummary member = members.get(i);
            int rowY = listY + (i - memberScroll) * (BUTTON_HEIGHT + ROW_GAP);
            Button remove = addButton(Component.translatable("screen.quantumflux.members.remove", member.displayName()),
                    x, rowY, width, BUTTON_HEIGHT,
                    ignored -> removeMember(network.uuid(), member.uuid().toString()));
            remove.setTooltip(Tooltip.create(
                    Component.translatable("screen.quantumflux.members.remove_tooltip", member.displayName())));
        }
        if (focusPrimary) setInitialFocus(memberField);
    }

    @Override
    public void tick() {
        super.tick();
        if (statusTicks > 0 && --statusTicks == 0) statusMessage = null;
        if (deleteArmed && deleteArmTicks > 0 && --deleteArmTicks == 0) {
            deleteArmed = false;
            rebuildWidgets(false);
            return;
        }

        UUID selected = getSelectedNetworkId();
        if (requestedSelectionId != null && requestedSelectionId.equals(selected)) {
            requestedSelectionId = null;
            selectedTab = Tab.OVERVIEW;
            setStatus(Component.translatable("screen.quantumflux.status.selected"), GREEN);
            rebuildWidgets(false);
            return;
        }
        if (networkForm == NetworkForm.JOIN && joiningNetworkId != null) {
            NetworkListSyncS2CPayload.NetworkSummary joined = ClientNetworkCache.getNetwork(joiningNetworkId);
            if (joined != null && joined.isMember() && joiningNetworkId.equals(selected)) {
                networkForm = NetworkForm.NONE;
                joiningNetworkId = null;
                joinPasswordDraft = "";
                selectedTab = Tab.OVERVIEW;
                setStatus(Component.translatable("screen.quantumflux.status.joined"), GREEN);
                rebuildWidgets(false);
                return;
            }
        }

        long signature = computeWidgetSignature();
        if (signature != widgetSignature && !hasFocusedTextField()) {
            rebuildWidgets(false);
        } else {
            refreshDynamicLabels();
        }
    }

    private void refreshDynamicLabels() {
        for (NetworkRow row : networkRows) {
            NetworkListSyncS2CPayload.NetworkSummary network = ClientNetworkCache.getNetwork(row.networkId());
            if (network != null) row.button().setMessage(networkRowLabel(network));
        }
        for (PylonRow row : pylonRows) {
            NetworkTelemetryPayload.PylonSummary data = pylonSummary(getSelectedNetworkId(), row.pos());
            if (data != null) {
                row.button().setMessage(pylonRowLabel(getSelectedNetworkId(), data));
                row.button().setTooltip(Tooltip.create(pylonSummaryHint(data)));
            }
        }
        ClientDataCache.PylonClientData selected = selectedPylonPos == null ? null : ClientDataCache.get(selectedPylonPos);
        if (selected != null) {
            for (ConnectionRow row : connectionRows) {
                if (row.index() < selected.connections.size()) {
                    PylonSyncPayload.ConnectionEntry connection = selected.connections.get(row.index());
                    row.button().setMessage(connectionRowLabel(connection));
                    Component action = row.button().active
                            ? Component.translatable("screen.quantumflux.pylons.unlink_tooltip", position(connection.pos()))
                            : mutationDisabledReason(canConfigureSelectedNetwork(), isNear(selectedPylonPos));
                    row.button().setTooltip(Tooltip.create(connectionTooltip(connection, action)));
                }
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Screen.render calls this before its widgets. Drawing the panel here avoids
        // a second vanilla blur pass over the controller's labels and telemetry.
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        renderMouseX = mouseX;
        renderMouseY = mouseY;
        GadgetScreenRenderer.render(this, graphics);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!insideContent(mouseX, mouseY) || scrollY == 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int delta = scrollY > 0 ? -1 : 1;
        if (selectedTab == Tab.NETWORKS && networkForm == NetworkForm.NONE) {
            networkScroll += delta;
            rebuildWidgets(false);
            return true;
        }
        if (selectedTab == Tab.PYLONS) {
            if (selectedPylonPos == null) pylonScroll += delta;
            else connectionScroll += delta;
            rebuildWidgets(false);
            return true;
        }
        if (selectedTab == Tab.MEMBERS) {
            memberScroll += delta;
            rebuildWidgets(false);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            if (createNameField != null && createNameField.isFocused()) { submitCreate(); return true; }
            if (joinPasswordField != null && joinPasswordField.isFocused()) { submitJoin(); return true; }
            if (renameField != null && renameField.isFocused()) {
                NetworkListSyncS2CPayload.NetworkSummary network = selectedNetwork();
                if (network != null) submitRename(network);
                return true;
            }
            if (passwordField != null && passwordField.isFocused()) {
                UUID id = getSelectedNetworkId();
                if (id != null) submitPassword(id);
                return true;
            }
            if (memberField != null && memberField.isFocused()) {
                UUID id = getSelectedNetworkId();
                if (id != null) submitMember(id);
                return true;
            }
        }
        if (keyCode == InputConstants.KEY_ESCAPE) {
            if (networkForm != NetworkForm.NONE) { cancelNetworkForm(); return true; }
            if (deleteArmed) { disarmDelete(); return true; }
            if (unlinkAllArmed) { unlinkAllArmed = false; rebuildWidgets(false); return true; }
            if (selectedPylonPos != null) { closePylon(); return true; }
        }
        if (!(getFocused() instanceof EditBox) && scrollActiveListWithKeyboard(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean scrollActiveListWithKeyboard(int keyCode) {
        if (keyCode != InputConstants.KEY_PAGEUP && keyCode != InputConstants.KEY_PAGEDOWN
                && keyCode != InputConstants.KEY_HOME && keyCode != InputConstants.KEY_END) {
            return false;
        }

        int size;
        int visible;
        int current;
        if (selectedTab == Tab.NETWORKS && networkForm == NetworkForm.NONE) {
            size = sortedNetworks().size();
            visible = visibleNetworkRows();
            current = networkScroll;
        } else if (selectedTab == Tab.PYLONS && selectedPylonPos == null) {
            int listY = contentTop + 27;
            size = pylonSnapshot.size();
            visible = Math.max(1, (contentBottom - listY) / (BUTTON_HEIGHT + ROW_GAP));
            current = pylonScroll;
        } else if (selectedTab == Tab.PYLONS) {
            ClientDataCache.PylonClientData data = ClientDataCache.get(selectedPylonPos);
            if (data == null) return false;
            size = data.connections.size();
            visible = visibleConnectionRows();
            if (visible == 0) return false;
            current = connectionScroll;
        } else if (selectedTab == Tab.MEMBERS) {
            NetworkListSyncS2CPayload.NetworkSummary network = selectedNetwork();
            if (network == null || !network.isOwner()) return false;
            int listY = contentTop + 36;
            int addY = contentBottom - BUTTON_HEIGHT;
            size = network.members().size();
            visible = Math.max(1, (addY - listY - 4) / (BUTTON_HEIGHT + ROW_GAP));
            current = memberScroll;
        } else {
            return false;
        }

        int maximum = Math.max(0, size - visible);
        int next = switch (keyCode) {
            case InputConstants.KEY_HOME -> 0;
            case InputConstants.KEY_END -> maximum;
            case InputConstants.KEY_PAGEUP -> Math.max(0, current - visible);
            case InputConstants.KEY_PAGEDOWN -> Math.min(maximum, current + visible);
            default -> current;
        };
        if (next == current) return true;
        if (selectedTab == Tab.NETWORKS) networkScroll = next;
        else if (selectedTab == Tab.PYLONS && selectedPylonPos == null) pylonScroll = next;
        else if (selectedTab == Tab.PYLONS) connectionScroll = next;
        else memberScroll = next;
        rebuildWidgets(false);
        return true;
    }

    private void selectTab(Tab tab) {
        selectedTab = tab;
        networkForm = NetworkForm.NONE;
        joiningNetworkId = null;
        joinPasswordDraft = "";
        passwordDraft = "";
        selectedPylonPos = null;
        deleteArmed = false;
        unlinkAllArmed = false;
        networkScroll = 0;
        pylonScroll = 0;
        connectionScroll = 0;
        rebuildWidgets(false);
    }

    private void selectSettingsPage(SettingsPage page) {
        if (page != SettingsPage.ACCESS) passwordDraft = "";
        settingsPage = page;
        deleteArmed = false;
        memberScroll = 0;
        rebuildWidgets(false);
    }

    private void openCreateForm() {
        networkForm = NetworkForm.CREATE;
        joiningNetworkId = null;
        clearStatus();
        rebuildWidgets(true);
    }

    private void cancelNetworkForm() {
        networkForm = NetworkForm.NONE;
        joiningNetworkId = null;
        joinPasswordDraft = "";
        rebuildWidgets(false);
    }

    private void submitCreate() {
        Optional<String> normalized = QuantumFluxNetworkManager.normalizeNetworkName(createNameDraft);
        if (normalized.isEmpty()) {
            setStatus(Component.translatable("screen.quantumflux.error.invalid_name",
                    QuantumFluxNetworkManager.MAX_NETWORK_NAME_LENGTH), RED);
            return;
        }
        sendNetworkAction(NetworkActionC2SPayload.create(normalized.get(), newColor));
        networkForm = NetworkForm.NONE;
        createNameDraft = Component.translatable("screen.quantumflux.create.default_name").getString();
        setStatus(Component.translatable("screen.quantumflux.status.request_sent"), TEXT_DIM);
        rebuildWidgets(false);
    }

    private void chooseNetwork(NetworkListSyncS2CPayload.NetworkSummary network) {
        if (network.accessMode() == 2 && !network.isMember()) {
            networkForm = NetworkForm.JOIN;
            joiningNetworkId = network.uuid();
            joinPasswordDraft = "";
            rebuildWidgets(true);
            return;
        }
        if (!(network.isOwner() || network.isMember() || network.accessMode() == 1)) {
            setStatus(Component.translatable("screen.quantumflux.error.no_access"), RED);
            return;
        }
        requestedSelectionId = network.uuid();
        sendNetworkAction(NetworkActionC2SPayload.select(network.uuid()));
        setStatus(Component.translatable("screen.quantumflux.status.selecting"), TEXT_DIM);
    }

    private void submitJoin() {
        if (joiningNetworkId == null || joinPasswordDraft.isEmpty()) {
            setStatus(Component.translatable("screen.quantumflux.error.password_required"), RED);
            return;
        }
        if (!QFPasswordUtil.isValidPassword(joinPasswordDraft)) {
            setStatus(Component.translatable("screen.quantumflux.error.invalid_password"), RED);
            return;
        }
        sendNetworkAction(NetworkActionC2SPayload.joinPasswordNetwork(
                joiningNetworkId, joinPasswordDraft));
        joinPasswordDraft = "";
        if (joinPasswordField != null) joinPasswordField.setValue("");
        setStatus(Component.translatable("screen.quantumflux.status.join_requested"), TEXT_DIM);
    }

    private void openPylon(BlockPos pos) {
        selectedPylonPos = pos;
        connectionScroll = 0;
        unlinkAllArmed = false;
        rebuildWidgets(false);
    }

    private void closePylon() {
        selectedPylonPos = null;
        connectionScroll = 0;
        unlinkAllArmed = false;
        rebuildWidgets(false);
    }

    private void setPriority(PriorityMode mode) {
        if (selectedPylonPos == null) return;
        sendGadgetAction(new GadgetActionPayload(
                selectedPylonPos, GadgetAction.SET_PRIORITY_MODE, BlockPos.ZERO, mode.ordinal(), 0));
        setStatus(Component.translatable("screen.quantumflux.status.request_sent"), TEXT_DIM);
    }

    private void unlinkConnection(BlockPos targetPos) {
        if (selectedPylonPos == null) return;
        sendGadgetAction(new GadgetActionPayload(
                selectedPylonPos, GadgetAction.UNLINK_SINGLE, targetPos, 0, 0));
        setStatus(Component.translatable("screen.quantumflux.status.request_sent"), TEXT_DIM);
    }

    private void unlinkAll() {
        if (selectedPylonPos == null) return;
        sendGadgetAction(new GadgetActionPayload(
                selectedPylonPos, GadgetAction.UNLINK_ALL, BlockPos.ZERO, 0, 0));
        unlinkAllArmed = false;
        setStatus(Component.translatable("screen.quantumflux.status.request_sent"), TEXT_DIM);
        rebuildWidgets(false);
    }

    private void submitRename(NetworkListSyncS2CPayload.NetworkSummary network) {
        if (!network.isOwner()) return;
        Optional<String> normalized = QuantumFluxNetworkManager.normalizeNetworkName(renameDraft);
        if (normalized.isEmpty()) {
            setStatus(Component.translatable("screen.quantumflux.error.invalid_name",
                    QuantumFluxNetworkManager.MAX_NETWORK_NAME_LENGTH), RED);
            return;
        }
        if (normalized.get().equals(network.name())) {
            setStatus(Component.translatable("screen.quantumflux.status.no_changes"), TEXT_DIM);
            return;
        }
        sendNetworkAction(NetworkActionC2SPayload.rename(network.uuid(), normalized.get()));
        setStatus(Component.translatable("screen.quantumflux.status.request_sent"), TEXT_DIM);
    }

    private void recolor(NetworkListSyncS2CPayload.NetworkSummary network, ColorChoice choice) {
        if (!network.isOwner()) return;
        sendNetworkAction(NetworkActionC2SPayload.recolor(network.uuid(), choice.color()));
        setStatus(Component.translatable("screen.quantumflux.status.request_sent"), TEXT_DIM);
    }

    private void setNetworkBeamStyle(UUID networkId, BeamStyle style) {
        sendNetworkAction(NetworkActionC2SPayload.setBeamStyle(networkId, style.ordinal()));
        setStatus(Component.translatable("screen.quantumflux.status.request_sent"), TEXT_DIM);
    }

    private void toggleNetworkBeams(UUID networkId) {
        sendNetworkAction(NetworkActionC2SPayload.toggleBeams(networkId));
        setStatus(Component.translatable("screen.quantumflux.status.request_sent"), TEXT_DIM);
    }

    private void setAccessMode(UUID networkId, int mode) {
        sendNetworkAction(NetworkActionC2SPayload.setAccessMode(networkId, mode));
        passwordDraft = "";
        setStatus(Component.translatable("screen.quantumflux.status.request_sent"), TEXT_DIM);
    }

    private void submitPassword(UUID networkId) {
        if (passwordDraft.isEmpty()) {
            setStatus(Component.translatable("screen.quantumflux.error.password_required"), RED);
            return;
        }
        if (!QFPasswordUtil.isValidPassword(passwordDraft)) {
            setStatus(Component.translatable("screen.quantumflux.error.invalid_password"), RED);
            return;
        }
        sendNetworkAction(NetworkActionC2SPayload.setPassword(networkId, passwordDraft));
        passwordDraft = "";
        if (passwordField != null) passwordField.setValue("");
        setStatus(Component.translatable("screen.quantumflux.status.password_requested"), TEXT_DIM);
    }

    private void submitMember(UUID networkId) {
        if (!memberDraft.matches("[A-Za-z0-9_]{1,16}")) {
            setStatus(Component.translatable("screen.quantumflux.error.invalid_player"), RED);
            return;
        }
        sendNetworkAction(NetworkActionC2SPayload.addMember(networkId, memberDraft));
        memberDraft = "";
        if (memberField != null) memberField.setValue("");
        setStatus(Component.translatable("screen.quantumflux.status.member_requested"), TEXT_DIM);
    }

    private void removeMember(UUID networkId, String memberName) {
        sendNetworkAction(NetworkActionC2SPayload.removeMember(networkId, memberName));
        setStatus(Component.translatable("screen.quantumflux.status.request_sent"), TEXT_DIM);
    }

    private void armDelete() {
        deleteArmed = true;
        deleteArmTicks = 200;
        setStatus(Component.translatable("screen.quantumflux.delete.prompt"), YELLOW);
        rebuildWidgets(false);
    }

    private void disarmDelete() {
        deleteArmed = false;
        deleteArmTicks = 0;
        clearStatus();
        rebuildWidgets(false);
    }

    private void confirmDelete(UUID networkId) {
        sendNetworkAction(NetworkActionC2SPayload.delete(networkId));
        deleteArmed = false;
        selectedTab = Tab.NETWORKS;
        setStatus(Component.translatable("screen.quantumflux.status.delete_requested"), TEXT_DIM);
        rebuildWidgets(false);
    }

    private void sendNetworkAction(NetworkActionC2SPayload payload) {
        int requestId = allocateRequestId();
        pendingRequests.put(requestId, payload.action());
        trimPendingRequests();
        ForgePacketDistributor.sendToServer(payload.withRequestId(requestId));
    }

    private void sendGadgetAction(GadgetActionPayload payload) {
        int requestId = allocateRequestId();
        pendingRequests.put(requestId, NetworkActionC2SPayload.Action.INVALID);
        trimPendingRequests();
        ForgePacketDistributor.sendToServer(payload.withRequestId(requestId));
    }

    private int allocateRequestId() {
        if (nextRequestId <= 0) nextRequestId = 1;
        while (pendingRequests.containsKey(nextRequestId)) nextRequestId++;
        return nextRequestId++;
    }

    private void trimPendingRequests() {
        while (pendingRequests.size() > 32) {
            pendingRequests.remove(pendingRequests.keySet().iterator().next());
        }
    }

    public void handleActionResult(ActionResultS2CPayload payload) {
        NetworkActionC2SPayload.Action action = pendingRequests.remove(payload.requestId());
        if (action == null) return;
        if (!payload.result().success() && action == NetworkActionC2SPayload.Action.SELECT) {
            requestedSelectionId = null;
        }
        if (payload.result().success()
                && action == NetworkActionC2SPayload.Action.JOIN_PASSWORD_NETWORK) {
            networkForm = NetworkForm.NONE;
            joiningNetworkId = null;
            joinPasswordDraft = "";
            rebuildWidgets(false);
        }
        Component message = Component.translatable(payload.result().translationKey());
        setStatus(message, payload.result().success() ? GREEN : RED);
        if (minecraft != null) minecraft.getNarrator().sayNow(message);
    }

    private Button addButton(Component label, int x, int y, int width, int height,
                             Button.OnPress onPress) {
        return addRenderableWidget(Button.builder(label, onPress).bounds(x, y, width, height).build());
    }

    int contentLeft() { return panelX + OUTER_MARGIN; }
    int contentWidth() { return panelWidth - OUTER_MARGIN * 2; }
    int networkListY() { return contentTop + 4 + BUTTON_HEIGHT + 5; }
    int visibleNetworkRows() {
        return Math.max(1, (contentBottom - 14 - networkListY() + ROW_GAP) / (BUTTON_HEIGHT + ROW_GAP));
    }
    int settingsNameY() { return contentTop + 54; }
    int settingsColorY() { return contentTop + 96; }
    int settingsBeamY() { return contentTop + 60; }

    /** Long player-supplied names remain bounded, with the complete text available on hover. */
    void drawText(GuiGraphics graphics, Component text, int x, int y, int availableWidth, int color) {
        int maxWidth = Math.max(0, availableWidth);
        if (font.width(text) <= maxWidth) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }
        String ellipsis = "\u2026";
        String shown = maxWidth < font.width(ellipsis) ? ""
                : font.plainSubstrByWidth(text.getString(), maxWidth - font.width(ellipsis)) + ellipsis;
        graphics.drawString(font, shown, x, y, color, false);
        if (renderMouseX >= x && renderMouseX < x + maxWidth
                && renderMouseY >= y && renderMouseY < y + font.lineHeight) {
            setTooltipForNextRenderPass(text);
        }
    }

    NetworkListSyncS2CPayload.NetworkSummary selectedNetwork() {
        UUID id = getSelectedNetworkId();
        return id == null ? null : ClientNetworkCache.getNetwork(id);
    }

    private UUID getSelectedNetworkId() {
        ItemStack gadget = heldGadget();
        if (gadget.isEmpty()) return null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;
        String selectedDimension = gadget.get(QFDataComponents.SELECTED_NETWORK_DIMENSION.get());
        String currentDimension = minecraft.level.dimension().location().toString();
        if (selectedDimension == null || !selectedDimension.equals(currentDimension)) return null;
        return gadget.get(QFDataComponents.SELECTED_NETWORK.get());
    }

    private ItemStack heldGadget() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return ItemStack.EMPTY;
        ItemStack main = minecraft.player.getMainHandItem();
        if (main.getItem() instanceof QuantumGadgetItem) return main;
        ItemStack off = minecraft.player.getOffhandItem();
        return off.getItem() instanceof QuantumGadgetItem ? off : ItemStack.EMPTY;
    }

    boolean canConfigureSelectedNetwork() {
        NetworkListSyncS2CPayload.NetworkSummary network = selectedNetwork();
        return network != null && network.isMember();
    }

    boolean isNear(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.distanceToSqr(
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    private Component tabLabel(Tab tab) {
        return Component.translatable("screen.quantumflux.tab." + tab.name().toLowerCase(Locale.ROOT));
    }

    Component linkingStatus() {
        ItemStack gadget = heldGadget();
        if (gadget.isEmpty()) return null;
        QFDataComponents.LinkingData linking = gadget.get(QFDataComponents.LINKING_DATA.get());
        return linking != null && linking.active()
                ? Component.translatable("screen.quantumflux.status.linking") : null;
    }

    private long computeWidgetSignature() {
        long result = 17;
        result = result * 31 + selectedTab.ordinal();
        result = result * 31 + settingsPage.ordinal();
        result = result * 31 + networkForm.ordinal();
        result = result * 31 + ClientNetworkCache.getNetworks().hashCode();
        result = result * 31 + java.util.Objects.hashCode(getSelectedNetworkId());
        result = result * 31 + java.util.Objects.hashCode(selectedPylonPos);
        UUID selectedId = getSelectedNetworkId();
        if (selectedId != null) {
            List<NetworkTelemetryPayload.PylonSummary> pylons = sortedPylons(selectedId);
            for (NetworkTelemetryPayload.PylonSummary entry : pylons) {
                result = result * 31 + entry.pos().hashCode();
                result = result * 31 + entry.availability().ordinal();
                ClientDataCache.PylonClientData data = ClientDataCache.get(entry.pos());
                if (data == null) continue;
                result = result * 31 + data.priorityMode.ordinal();
                result = result * 31 + data.redstoneMode.ordinal();
                result = result * 31 + data.beamStyle.ordinal();
                result = result * 31 + Boolean.hashCode(data.beamsVisible);
                result = result * 31 + data.connections.stream()
                        .map(PylonSyncPayload.ConnectionEntry::pos).toList().hashCode();
            }
        }
        if (selectedPylonPos != null) result = result * 31 + Boolean.hashCode(isNear(selectedPylonPos));
        return result;
    }

    private boolean hasFocusedTextField() {
        return isFocused(createNameField) || isFocused(joinPasswordField) || isFocused(renameField)
                || isFocused(passwordField) || isFocused(memberField);
    }

    private static boolean isFocused(EditBox field) {
        return field != null && field.isFocused();
    }

    @Override
    public Component getNarrationMessage() {
        MutableComponent narration = getTitle().copy()
                .append(Component.literal(". "))
                .append(tabLabel(selectedTab));
        NetworkListSyncS2CPayload.NetworkSummary network = selectedNetwork();
        if (network != null) {
            narration.append(Component.literal(". "))
                    .append(Component.translatable("screen.quantumflux.narration.network",
                            network.name(), network.numericId(), roleLabel(network)));
        }
        if (statusMessage != null) narration.append(Component.literal(". ")).append(statusMessage);
        return narration;
    }

    @Override
    public void removed() {
        passwordDraft = "";
        joinPasswordDraft = "";
        if (passwordField != null) passwordField.setValue("");
        if (joinPasswordField != null) joinPasswordField.setValue("");
        super.removed();
    }

    private void setStatus(Component message, int color) {
        statusMessage = message;
        statusColor = color;
        statusTicks = 160;
        triggerImmediateNarration(false);
    }

    private void clearStatus() {
        statusMessage = null;
        statusTicks = 0;
    }

    private boolean insideContent(double mouseX, double mouseY) {
        return mouseX >= contentLeft() && mouseX < contentLeft() + contentWidth()
                && mouseY >= contentTop && mouseY < contentBottom;
    }

    static int colorChoiceCount() { return COLORS.length; }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record ColorChoice(int color, String key) {}
    private record NetworkRow(UUID networkId, Button button) {}
    private record PylonRow(BlockPos pos, Button button) {}
    private record ConnectionRow(int index, Button button) {}
}
