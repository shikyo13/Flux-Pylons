package com.zerotheabsolute.quantumflux.client.tutorial;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** A seek control that pauses on interaction so a lesson stays at the chosen step. */
final class TutorialSeekBar extends AbstractWidget {
    private final TutorialPlaybackController playback;
    private final int accent;

    TutorialSeekBar(int x, int y, int width, TutorialPlaybackController playback, int accent) {
        super(x, y, width, 12, Component.translatable("screen.quantumflux.guide.timeline"));
        this.playback = playback;
        this.accent = accent;
        setTooltip(Tooltip.create(Component.translatable("screen.quantumflux.guide.timeline.help")));
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int y = getY() + 4;
        graphics.fill(getX(), y, getX() + width, y + 4, 0xFF454B50);
        int filled = (int) Math.round(playback.progress() * width);
        graphics.fill(getX(), y, getX() + filled, y + 4, accent);
        for (int index = 1; index < 4; index++) {
            int x = getX() + width * index / 4;
            graphics.fill(x, y, x + 1, y + 4, 0xFF939C9F);
        }
        int thumb = getX() + Math.max(1, Math.min(width - 2, filled));
        graphics.fill(thumb - 1, getY() + 1, thumb + 2, getY() + 11, 0xFFE7F0EE);
        if (isFocused()) graphics.renderOutline(getX() - 1, getY(), width + 2, height, accent);
    }

    private void seek(double mouseX) {
        playback.seek((mouseX - getX()) / width * playback.duration());
        playback.setPaused(true);
    }

    @Override public void onClick(double mouseX, double mouseY) { seek(mouseX); }
    @Override protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) { seek(mouseX); }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        double destination;
        if (keyCode == GLFW.GLFW_KEY_LEFT) destination = playback.elapsedSeconds() - 5;
        else if (keyCode == GLFW.GLFW_KEY_RIGHT) destination = playback.elapsedSeconds() + 5;
        else if (keyCode == GLFW.GLFW_KEY_HOME) destination = 0;
        else if (keyCode == GLFW.GLFW_KEY_END) destination = playback.duration();
        else return super.keyPressed(keyCode, scanCode, modifiers);
        playback.seek(destination);
        playback.setPaused(true);
        return true;
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        output.add(NarratedElementType.POSITION, Component.translatable("screen.quantumflux.guide.time",
                (int) playback.elapsedSeconds(), (int) playback.duration()));
        output.add(NarratedElementType.USAGE, Component.translatable("screen.quantumflux.guide.timeline.help"));
    }
}
