package com.zerotheabsolute.quantumflux.client.screen;

import com.zerotheabsolute.quantumflux.client.ClientDataCache;
import com.zerotheabsolute.quantumflux.client.ClientNetworkCache;
import com.zerotheabsolute.quantumflux.client.PylonReadout;
import com.zerotheabsolute.quantumflux.client.PylonStatus;
import com.zerotheabsolute.quantumflux.network.NetworkListSyncS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

import static com.zerotheabsolute.quantumflux.client.screen.GadgetScreenModel.*;
import static com.zerotheabsolute.quantumflux.client.screen.GadgetScreenTheme.*;

/** Draws non-interactive screen chrome and server-synchronized telemetry. */
final class GadgetScreenRenderer {

    private GadgetScreenRenderer() {}

    static void render(GadgetScreen screen, GuiGraphics graphics) {
        graphics.fill(screen.panelX, screen.panelY,
                screen.panelX + screen.panelWidth, screen.panelY + screen.panelHeight, BG);
        graphics.fill(screen.panelX + 1, screen.panelY + 1,
                screen.panelX + screen.panelWidth - 1, screen.panelY + HEADER_HEIGHT - 1, HEADER_BG);
        border(graphics, screen.panelX, screen.panelY, screen.panelWidth, screen.panelHeight, BORDER);
        graphics.fill(screen.contentLeft() - 3, screen.contentTop - 2,
                screen.contentLeft() + screen.contentWidth() + 3, screen.contentBottom + 2, PANEL);
        border(graphics, screen.contentLeft() - 3, screen.contentTop - 2,
                screen.contentWidth() + 6, screen.contentBottom - screen.contentTop + 4, BORDER_DIM);

        renderHeader(screen, graphics);
        switch (screen.selectedTab) {
            case NETWORKS -> renderNetworks(screen, graphics);
            case OVERVIEW -> renderOverview(screen, graphics);
            case PYLONS -> renderPylons(screen, graphics);
            case MEMBERS -> renderMembers(screen, graphics);
            case SETTINGS -> renderSettings(screen, graphics);
        }
        renderStatus(screen, graphics);
    }

    private static void renderHeader(GadgetScreen screen, GuiGraphics graphics) {
        Font font = font();
        NetworkListSyncS2CPayload.NetworkSummary network = screen.selectedNetwork();
        if (network == null) {
            graphics.drawCenteredString(font, screen.getTitle(),
                    screen.panelX + screen.panelWidth / 2, screen.panelY + 8, ACCENT);
            return;
        }
        graphics.fill(screen.panelX + 9, screen.panelY + 8,
                screen.panelX + 17, screen.panelY + 16, 0xFF000000 | network.color());
        Component label = Component.translatable("screen.quantumflux.header.network",
                network.name(), network.numericId());
        int labelWidth = screen.panelWidth - 62;
        if (screen.selectedTab == GadgetScreen.Tab.PYLONS && screen.selectedPylonPos != null) {
            Component pylon = Component.translatable("screen.quantumflux.pylons.detail",
                    position(screen.selectedPylonPos));
            int pylonWidth = Math.min(font.width(pylon), (screen.panelWidth - 60) / 2);
            int pylonX = screen.panelX + screen.panelWidth - 35 - pylonWidth;
            screen.drawText(graphics, pylon, pylonX, screen.panelY + 8, pylonWidth, TEXT);
            labelWidth = Math.max(0, pylonX - screen.panelX - 30);
        }
        screen.drawText(graphics, label, screen.panelX + 22, screen.panelY + 8, labelWidth, ACCENT);
    }

    private static void renderNetworks(GadgetScreen screen, GuiGraphics graphics) {
        Font font = font();
        int x = screen.contentLeft();
        int y = screen.contentTop + 7;
        if (screen.networkForm == GadgetScreen.NetworkForm.CREATE) {
            graphics.drawString(font, Component.translatable("screen.quantumflux.create.title"), x, y, ACCENT);
            graphics.drawString(font, Component.translatable("screen.quantumflux.create.color"),
                    x, y + 48, TEXT_DIM);
            return;
        }
        if (screen.networkForm == GadgetScreen.NetworkForm.JOIN) {
            NetworkListSyncS2CPayload.NetworkSummary network = ClientNetworkCache.getNetwork(screen.joiningNetworkId);
            graphics.drawString(font, Component.translatable("screen.quantumflux.join.title"), x, y, ACCENT);
            if (network != null) {
                screen.drawText(graphics, Component.translatable("screen.quantumflux.header.network",
                        network.name(), network.numericId()), x, y + 17, screen.contentWidth(), TEXT);
            }
            return;
        }
        graphics.drawString(font, Component.translatable("screen.quantumflux.networks.heading"),
                x, y + 5, ACCENT);
        List<NetworkListSyncS2CPayload.NetworkSummary> networks = sortedNetworks();
        if (networks.isEmpty()) {
            drawWrapped(graphics, Component.translatable("screen.quantumflux.networks.empty"),
                    x, y + 44, screen.contentWidth(), TEXT_DIM);
        } else {
            int maxVisible = screen.visibleNetworkRows();
            if (networks.size() > maxVisible) {
                graphics.drawString(font, Component.translatable("screen.quantumflux.list.position",
                        screen.networkScroll + 1,
                        Math.min(networks.size(), screen.networkScroll + maxVisible), networks.size()),
                        x, screen.contentBottom - 10, TEXT_DIM);
            }
        }
    }

    private static void renderOverview(GadgetScreen screen, GuiGraphics graphics) {
        Font font = font();
        int x = screen.contentLeft();
        int y = screen.contentTop + 6;
        NetworkListSyncS2CPayload.NetworkSummary network = screen.selectedNetwork();
        if (network == null) {
            drawWrapped(graphics, Component.translatable("screen.quantumflux.no_network"),
                    x, y + 26, screen.contentWidth(), TEXT_DIM);
            return;
        }

        Telemetry telemetry = telemetry(network.uuid());
        graphics.fill(x, y, x + 9, y + 9, 0xFF000000 | network.color());
        screen.drawText(graphics, Component.translatable("screen.quantumflux.overview.name",
                network.name(), network.numericId()), x + 14, y, screen.contentWidth() - 14, ACCENT);
        graphics.drawString(font, roleLabel(network), x + 14, y + 12, roleColor(network));

        var snapshot = ClientNetworkCache.snapshot(network.uuid());
        if (snapshot == null) {
            drawWrapped(graphics, Component.translatable("screen.quantumflux.flow.no_telemetry"),
                    x, y + 35, screen.contentWidth(), TEXT_DIM);
            return;
        }

        PylonStatus status = PylonStatus.of(telemetry.energy(), telemetry.throughput(), telemetry.connections());
        Component statusLabel = telemetry.loadedPylons() == 0
                ? Component.translatable("screen.quantumflux.flow.no_loaded") : status.label();
        graphics.drawString(font, statusLabel,
                x + screen.contentWidth() - font.width(statusLabel), y + 12,
                telemetry.loadedPylons() == 0 ? TEXT_DIM : status.color());

        int barY = y + 30;
        int pct = telemetry.capacity() > 0
                ? (int) com.zerotheabsolute.quantumflux.util.Numbers.clamp(telemetry.energy() * 100L / telemetry.capacity(), 0L, 100L) : 0;
        PylonReadout.renderMeter(graphics, x, barY + 14, screen.contentWidth(), 6,
                telemetry.energy(), telemetry.capacity(), 0xFF000000 | network.color());
        graphics.drawCenteredString(font, Component.translatable("screen.quantumflux.overview.energy_bar",
                formatEnergy(telemetry.energy()), formatEnergy(telemetry.capacity()), pct),
                x + screen.contentWidth() / 2, barY, TEXT);

        int statsY = barY + 28;
        int secondColumn = x + screen.contentWidth() / 2 + 4;
        graphics.drawString(font, Component.translatable("screen.quantumflux.overview.loaded_inline",
                telemetry.loadedPylons(), snapshot.pylons().size()), x, statsY, TEXT);
        graphics.drawString(font, Component.translatable("screen.quantumflux.overview.links_inline",
                telemetry.connections()), secondColumn, statsY, TEXT);
        graphics.drawString(font, Component.translatable("screen.quantumflux.overview.output_inline",
                formatRate(telemetry.throughput())), x, statsY + 14, TEXT);
        graphics.drawString(font, Component.translatable("screen.quantumflux.overview.peak_inline",
                formatRate(telemetry.peak())), secondColumn, statsY + 14, TEXT);
    }

    private static void renderPylons(GadgetScreen screen, GuiGraphics graphics) {
        Font font = font();
        int x = screen.contentLeft();
        int y = screen.contentTop + 7;
        NetworkListSyncS2CPayload.NetworkSummary network = screen.selectedNetwork();
        if (network == null) {
            drawWrapped(graphics, Component.translatable("screen.quantumflux.no_network"),
                    x, y + 24, screen.contentWidth(), TEXT_DIM);
            return;
        }
        if (screen.selectedPylonPos == null) {
            graphics.drawString(font, Component.translatable("screen.quantumflux.pylons.heading"),
                    x, y, ACCENT);
            if (screen.pylonSnapshot.isEmpty()) {
                drawWrapped(graphics, Component.translatable(ClientNetworkCache.snapshot(network.uuid()) == null
                                ? "screen.quantumflux.flow.no_telemetry" : "screen.quantumflux.pylons.empty"),
                        x, y + 34, screen.contentWidth(), TEXT_DIM);
            }
            return;
        }

        ClientDataCache.PylonClientData data = ClientDataCache.get(screen.selectedPylonPos);
        if (data == null || !network.uuid().equals(data.networkId)) {
            var remote = pylonSummary(network.uuid(), screen.selectedPylonPos);
            if (remote == null) {
                drawWrapped(graphics, Component.translatable("screen.quantumflux.flow.no_telemetry"),
                        x, y + 34, screen.contentWidth(), TEXT_DIM);
                return;
            }
            graphics.drawString(font, pylonSummaryStatus(network.uuid(), remote), x, y + 30, TEXT);
            if (remote.loaded()) {
                graphics.drawString(font, Component.translatable("screen.quantumflux.readout.energy",
                        formatEnergy(remote.energy()), formatEnergy(remote.capacity())), x, y + 45, TEXT_DIM);
                graphics.drawString(font, Component.translatable("screen.quantumflux.overview.output_inline",
                        formatRate(remote.throughput())), x, y + 60, TEXT);
                graphics.drawString(font, Component.translatable("screen.quantumflux.pylons.connections",
                        remote.connections(), remote.maxConnections()), x, y + 75, TEXT);
            }
            drawWrappedClipped(graphics, pylonSummaryHint(remote), x, y + 94,
                    screen.contentWidth(), screen.contentBottom, TEXT_DIM);
            return;
        }
        PylonStatus status = PylonStatus.of(data);
        graphics.drawString(font, status.label(), x, y + 27, status.color());
        Component output = Component.translatable("screen.quantumflux.unit.fe_per_tick", formatRate(data.throughput));
        graphics.drawString(font, output, x + screen.contentWidth() - font.width(output), y + 27, TEXT);
        Component energy = Component.translatable("screen.quantumflux.readout.energy",
                formatEnergy(data.energy), formatEnergy(data.maxEnergy));
        graphics.drawString(font, energy, x, y + 39, TEXT_DIM);
        int meterX = x + font.width(energy) + 8;
        if (x + screen.contentWidth() - meterX > 20) {
            PylonReadout.renderMeter(graphics, meterX, y + 40,
                    x + screen.contentWidth() - meterX, 6, data.energy, data.maxEnergy,
                    0xFF000000 | data.beamColor);
        }
        graphics.drawString(font, Component.translatable("screen.quantumflux.pylons.limits",
                        data.effectiveRange, formatRate(data.transferLimit)),
                x, y + 69, TEXT_DIM);
        graphics.drawString(font, Component.translatable("screen.quantumflux.pylons.connections",
                data.connections.size(), data.maxConnections), x, y + 83, ACCENT);
        if (data.connections.isEmpty()) {
            drawWrappedClipped(graphics, Component.translatable("screen.quantumflux.pylons.link_help"),
                    x, screen.contentTop + 104, screen.contentWidth(),
                    screen.contentBottom - BUTTON_HEIGHT - 3, TEXT_DIM);
        }
        if (!screen.canConfigureSelectedNetwork()) {
            Component readOnly = Component.translatable("screen.quantumflux.permission.read_only");
            graphics.drawString(font, readOnly,
                    x + screen.contentWidth() - font.width(readOnly), y + 83, YELLOW);
        } else if (!screen.isNear(screen.selectedPylonPos)) {
            Component tooFar = Component.translatable("screen.quantumflux.permission.too_far");
            graphics.drawString(font, tooFar,
                    x + screen.contentWidth() - font.width(tooFar), y + 83, YELLOW);
        }
    }

    private static void renderSettings(GadgetScreen screen, GuiGraphics graphics) {
        Font font = font();
        NetworkListSyncS2CPayload.NetworkSummary network = screen.selectedNetwork();
        if (network == null) return;
        int x = screen.contentLeft();
        int y = screen.contentTop + 30;
        graphics.drawString(font, roleLabel(network), x, y, roleColor(network));
        if (screen.settingsPage == GadgetScreen.SettingsPage.GENERAL) {
            graphics.drawString(font, Component.translatable("screen.quantumflux.settings.network_name"),
                    x, screen.settingsNameY() - font.lineHeight - 3, TEXT_DIM);
            graphics.drawString(font, Component.translatable("screen.quantumflux.settings.color"),
                    x, screen.settingsColorY() - font.lineHeight - 3, TEXT_DIM);
        } else if (screen.settingsPage == GadgetScreen.SettingsPage.BEAMS) {
            graphics.drawString(font, Component.translatable("screen.quantumflux.settings.beam_style"),
                    x, screen.settingsBeamY() - font.lineHeight - 3, TEXT_DIM);
        } else if (screen.settingsPage == GadgetScreen.SettingsPage.ACCESS) {
            graphics.drawString(font, Component.translatable("screen.quantumflux.settings.access_mode"),
                    x, screen.settingsNameY() - font.lineHeight - 3, TEXT_DIM);
            if (!network.isOwner()) {
                drawWrapped(graphics, Component.translatable("screen.quantumflux.permission.owner_access"),
                        x, y + 50, screen.contentWidth(), TEXT_DIM);
            }
        }
    }

    private static void renderMembers(GadgetScreen screen, GuiGraphics graphics) {
        var network = screen.selectedNetwork();
        if (network == null) return;
        int x = screen.contentLeft();
        int y = screen.contentTop + 7;
        graphics.drawString(font(), Component.translatable("screen.quantumflux.members.heading",
                network.members().size()), x, y, ACCENT);
        graphics.drawString(font(), roleLabel(network), x, y + 14, roleColor(network));
        if (!network.isOwner()) {
            drawWrapped(graphics, Component.translatable("screen.quantumflux.permission.owner_access"),
                    x, y + 35, screen.contentWidth(), TEXT_DIM);
        } else if (network.members().isEmpty()) {
            drawWrapped(graphics, Component.translatable("screen.quantumflux.members.empty"),
                    x, y + 35, screen.contentWidth(), TEXT_DIM);
        }
    }

    private static void renderStatus(GadgetScreen screen, GuiGraphics graphics) {
        Font font = font();
        Component linking = screen.linkingStatus();
        Component message = linking != null ? linking : screen.statusMessage;
        int color = linking != null ? YELLOW : screen.statusColor;
        if (message == null && screen.selectedTab == GadgetScreen.Tab.PYLONS
                && screen.selectedPylonPos != null) {
            ClientDataCache.PylonClientData data = ClientDataCache.get(screen.selectedPylonPos);
            NetworkListSyncS2CPayload.NetworkSummary network = screen.selectedNetwork();
            int visible = screen.visibleConnectionRows();
            if (data != null && network != null && network.uuid().equals(data.networkId)
                    && visible > 0 && data.connections.size() > visible) {
                message = Component.translatable("screen.quantumflux.list.navigation",
                        screen.connectionScroll + 1,
                        Math.min(data.connections.size(), screen.connectionScroll + visible),
                        data.connections.size());
                color = TEXT_DIM;
            }
        }
        if (message == null) return;
        int messageWidth = Math.min(font.width(message), screen.panelWidth - 18);
        screen.drawText(graphics, message, screen.panelX + (screen.panelWidth - messageWidth) / 2,
                screen.panelY + screen.panelHeight - 13, messageWidth, color);
    }

    private static void drawStat(GadgetScreen screen, GuiGraphics graphics, int x, int y,
                                 String labelKey, Component value) {
        Font font = font();
        graphics.drawString(font, Component.translatable(labelKey), x, y, TEXT_DIM);
        graphics.drawString(font, value, x + Math.min(112, screen.contentWidth() / 2), y, TEXT);
    }

    private static int drawWrapped(GuiGraphics graphics, Component component,
                                   int x, int y, int width, int color) {
        Font font = font();
        int lineY = y;
        for (FormattedCharSequence line : font.split(component, width)) {
            graphics.drawString(font, line, x, lineY, color);
            lineY += font.lineHeight + 2;
        }
        return lineY;
    }

    private static void drawWrappedClipped(GuiGraphics graphics, Component component,
                                           int x, int y, int width, int bottom, int color) {
        Font font = font();
        for (FormattedCharSequence line : font.split(component, width)) {
            if (y + font.lineHeight > bottom) break;
            graphics.drawString(font, line, x, y, color);
            y += font.lineHeight + 2;
        }
    }

    private static void border(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }
}
