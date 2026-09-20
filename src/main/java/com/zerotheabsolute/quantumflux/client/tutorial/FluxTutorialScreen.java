package com.zerotheabsolute.quantumflux.client.tutorial;
import com.zeromods.core.client.TutorialScreen;
import com.zeromods.core.tutorial.TutorialLesson;
import com.zeromods.core.tutorial.TutorialScene;
import com.zeromods.core.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
public final class FluxTutorialScreen {
    static final int ACCENT = UiTheme.FLUX.accent();
    static final int COPPER = UiTheme.FLUX.border();
    static final int TEXT = UiTheme.FLUX.text();
    static final int MUTED = UiTheme.FLUX.muted();

    private FluxTutorialScreen() {}

    static Component text(String key, Object... values) { return Component.translatable("screen.quantumflux.guide." + key, values); }
    public static void open(Screen parent, int chapter) {
        var scenes = new ArrayList<TutorialScene<GuiGraphics, Component>>();
        double[] durations = FluxTutorialScenes.durations();
        for (int index = 0; index < durations.length; index++) {
            final int i = index;
            scenes.add(new TutorialScene<>() {
                public String id() { return "quantumflux:chapter_" + i; }
                public Component title() { return FluxTutorialScenes.title(i); }
                public double durationSeconds() { return durations[i]; }
                public Component caption(double time) { return FluxTutorialScenes.caption(i, time); }
                public List<Component> captions() {
                    var lines = new ArrayList<Component>();
                    for (int step = 0; step < FluxTutorialScenes.stepCount(i); step++) lines.add(caption(step * FluxTutorialScenes.STEP_SECONDS));
                    return lines;
                }
                public void render(GuiGraphics graphics, double time) { FluxTutorialScenes.render(graphics, i, time); }
            });
        }
        Minecraft.getInstance().setScreen(new TutorialScreen(parent, text("title"), text("help"),
                new TutorialLesson<>("quantumflux:guide", 1, scenes), UiTheme.FLUX, chapter));
    }
}
