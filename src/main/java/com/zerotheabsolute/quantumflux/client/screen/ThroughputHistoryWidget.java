package com.zerotheabsolute.quantumflux.client.screen;

import com.zerotheabsolute.quantumflux.client.ClientNetworkCache;
import com.zerotheabsolute.quantumflux.client.PylonReadout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;

/** One minute of actual samples; absent samples leave gaps instead of invented zeroes. */
final class ThroughputHistoryWidget extends AbstractWidget {
    private final UUID networkId;
    private int offset;

    ThroughputHistoryWidget(int x, int y, int width, int height, UUID networkId) {
        super(x, y, width, height, Component.translatable("screen.quantumflux.history.title"));
        this.networkId = networkId;
        setTooltip(Tooltip.create(Component.translatable("screen.quantumflux.history.help")));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        List<ClientNetworkCache.HistoryPoint> points = ClientNetworkCache.history(networkId);
        int x = getX(), y = getY();
        graphics.fill(x, y, x + width, y + height, GadgetScreenTheme.PANEL);
        graphics.renderOutline(x, y, width, height,
                isFocused() ? GadgetScreenTheme.ACCENT : GadgetScreenTheme.BORDER_DIM);
        graphics.enableScissor(x + 2, y + 2, x + width - 2, y + height - 2);
        graphics.drawString(font, getMessage(), x + 4, y + 4, GadgetScreenTheme.TEXT_DIM);
        if (!points.isEmpty()) {
            int chosen = selectedIndex(points.size());
            if (isHovered() && mouseY >= y + 15) chosen = nearestPoint(points, mouseX);
            Component reading = reading(points, chosen);
            graphics.drawString(font, reading, x + width - font.width(reading) - 4, y + 4, GadgetScreenTheme.TEXT);
            int bottom = y + height - 4;
            int plotHeight = Math.max(1, height - 20);
            graphics.fill(x + 4, bottom, x + width - 4, bottom + 1, GadgetScreenTheme.BORDER_DIM);
            double maximum = points.stream().mapToDouble(ClientNetworkCache.HistoryPoint::throughput).max().orElse(0);
            long newest = points.getLast().sampleTick();
            for (int index = 0; index < points.size(); index++) {
                var point = points.get(index);
                int px = plotX(point.sampleTick(), newest);
                int barHeight = maximum > 0 ? Math.max(1, (int) (point.throughput() / maximum * plotHeight)) : 1;
                int color = index == chosen ? GadgetScreenTheme.TEXT : GadgetScreenTheme.ACCENT;
                graphics.fill(px, bottom - barHeight, px + Math.max(1, (width - 8) / 80), bottom, color);
            }
        }
        graphics.disableScissor();
    }

    private int plotX(long tick, long newest) {
        return getX() + 4 + (int) ((width - 10) * Math.clamp(1.0 - (newest - tick) / 1_200.0, 0, 1));
    }

    private int selectedIndex(int count) {
        return Math.clamp(count - 1 - offset, 0, count - 1);
    }

    private int nearestPoint(List<ClientNetworkCache.HistoryPoint> points, double mouseX) {
        int chosen = points.size() - 1;
        double distance = Double.MAX_VALUE;
        long newest = points.getLast().sampleTick();
        for (int index = 0; index < points.size(); index++) {
            double next = Math.abs(plotX(points.get(index).sampleTick(), newest) - mouseX);
            if (next < distance) { distance = next; chosen = index; }
        }
        return chosen;
    }

    private Component reading(List<ClientNetworkCache.HistoryPoint> points, int index) {
        var point = points.get(index);
        long secondsAgo = (points.getLast().sampleTick() - point.sampleTick()) / 20;
        return Component.translatable("screen.quantumflux.history.reading",
                secondsAgo, PylonReadout.formatRate(point.throughput()));
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        var points = ClientNetworkCache.history(networkId);
        if (!points.isEmpty()) offset = points.size() - 1 - nearestPoint(points, mouseX);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int size = ClientNetworkCache.history(networkId).size();
        if (size > 0 && (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT)) {
            offset = Math.clamp(offset + (keyCode == GLFW.GLFW_KEY_LEFT ? 1 : -1), 0, size - 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        var points = ClientNetworkCache.history(networkId);
        narration.add(NarratedElementType.TITLE, getMessage());
        narration.add(NarratedElementType.POSITION, points.isEmpty()
                ? Component.translatable("screen.quantumflux.flow.no_telemetry")
                : reading(points, selectedIndex(points.size())));
        narration.add(NarratedElementType.USAGE, Component.translatable("screen.quantumflux.history.help"));
    }
}
