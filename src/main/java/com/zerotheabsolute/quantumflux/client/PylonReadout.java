package com.zerotheabsolute.quantumflux.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Locale;

/** Compact instrument readout shared by the world HUD and gadget screen. */
public final class PylonReadout {
    public static final int HUD_TOP = 10;
    private static final int HUD_LEFT = 8;
    private static final int HUD_GAP = 4;

    private PylonReadout() {}

    /** Draw the readout and return the top of the next panel in the same stack. */
    public static int renderHud(GuiGraphics graphics, Minecraft minecraft,
                                 ClientDataCache.PylonClientData data) {
        var font = minecraft.font;
        int x = HUD_LEFT;
        int y = HUD_TOP;
        int width = hudWidth(minecraft);
        int right = x + width - 10;
        int lineHeight = font.lineHeight + 1;
        int accent = 0xFF000000 | data.beamColor;
        PylonStatus status = PylonStatus.of(data);
        Component title = Component.translatable("block.quantumflux.quantum_pylon");
        Component output = Component.translatable("screen.quantumflux.unit.fe_per_tick",
                formatRate(data.throughput));
        Component energy = Component.translatable("screen.quantumflux.readout.energy",
                formatEnergy(data.energy), formatEnergy(data.maxEnergy));
        Component links = Component.translatable("screen.quantumflux.readout.links",
                data.connections.size(), data.maxConnections);
        var titleLines = font.split(title, width - 20);
        var outputLines = font.split(output, width - 20);
        var statusLines = font.split(status.label(), width - 20);
        var energyLines = font.split(energy, width - 20);
        var linkLines = font.split(links, width - 20);
        boolean inlineOutput = font.width(title) + font.width(output) + 30 <= width;
        int headerHeight = 8 + lineHeight * (titleLines.size()
                + (inlineOutput ? 0 : outputLines.size()));
        int statusY = y + headerHeight + 6;
        int energyY = statusY + statusLines.size() * lineHeight + 5;
        int meterY = energyY + energyLines.size() * lineHeight + 2;
        int linksY = meterY + 12;
        int bottom = linksY + linkLines.size() * lineHeight + 3;

        graphics.fill(x, y, x + width, bottom, 0xEB141624);
        graphics.fill(x, y, x + width, y + headerHeight, 0xF0252C35);
        graphics.fill(x, y, x + 2, bottom, accent);
        drawLines(graphics, font, titleLines, x + 10, y + 6, 0xFFE4ECF2);
        if (inlineOutput) {
            graphics.drawString(font, output, right - font.width(output), y + 6, 0xFFE4ECF2);
        } else {
            drawLines(graphics, font, outputLines, x + 10,
                    y + 6 + titleLines.size() * lineHeight, 0xFFE4ECF2);
        }
        drawLines(graphics, font, statusLines, x + 10, statusY, status.color());
        drawLines(graphics, font, energyLines, x + 10, energyY, 0xFF96A2B3);
        renderMeter(graphics, x + 10, meterY, width - 20, 5, data.energy, data.maxEnergy, accent);
        drawLines(graphics, font, linkLines, x + 10, linksY, 0xFFE4ECF2);
        return bottom + HUD_GAP;
    }

    /** Keep linking and receiver feedback beside the readout, away from block tooltips. */
    public static int renderHudMessage(GuiGraphics graphics, Minecraft minecraft,
                                       Component message, int y, int color) {
        int width = hudWidth(minecraft);
        var lines = minecraft.font.split(message, width - 20);
        int bottom = y + 12 + lines.size() * (minecraft.font.lineHeight + 1);
        graphics.fill(HUD_LEFT, y, HUD_LEFT + width, bottom, 0xEB141624);
        graphics.fill(HUD_LEFT, y, HUD_LEFT + 2, bottom, color);
        drawLines(graphics, minecraft.font, lines, HUD_LEFT + 10, y + 6, color);
        return bottom + HUD_GAP;
    }

    private static int hudWidth(Minecraft minecraft) {
        // Keep the stack in the left third even at larger GUI scales. Narrow panels
        // wrap their labels and put the measured rate on its own header line.
        return Math.min(210, minecraft.getWindow().getGuiScaledWidth() / 3 - 16);
    }

    private static void drawLines(GuiGraphics graphics, Font font,
                                  List<FormattedCharSequence> lines, int x, int y, int color) {
        for (var line : lines) {
            graphics.drawString(font, line, x, y, color);
            y += font.lineHeight + 1;
        }
    }

    public static void renderMeter(GuiGraphics graphics, int x, int y, int width, int height,
                                   long energy, long capacity, int color) {
        graphics.fill(x, y, x + width, y + height, 0xFF303846);
        double fraction = capacity > 0 ? com.zerotheabsolute.quantumflux.util.Numbers.clamp((double) energy / capacity, 0.0, 1.0) : 0.0;
        int filled = energy > 0 ? Math.max(1, (int) (width * fraction)) : 0;
        graphics.fill(x, y, x + filled, y + height, color);
        for (int segment = 1; segment < 10; segment++) {
            int sx = x + width * segment / 10;
            graphics.fill(sx, y, sx + 1, y + height, 0xFF141624);
        }
    }

    public static String formatRate(double rate) {
        if (!Double.isFinite(rate) || rate <= 0) return "0";
        if (rate >= 1_000) return formatEnergy(Math.round(rate));
        return java.math.BigDecimal.valueOf(rate).setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    public static String formatEnergy(long energy) {
        if (energy >= 1_000_000_000L) return String.format(Locale.ROOT, "%.1fG", energy / 1_000_000_000.0);
        if (energy >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM", energy / 1_000_000.0);
        if (energy >= 1_000L) return String.format(Locale.ROOT, "%.1fK", energy / 1_000.0);
        return Long.toString(energy);
    }
}
