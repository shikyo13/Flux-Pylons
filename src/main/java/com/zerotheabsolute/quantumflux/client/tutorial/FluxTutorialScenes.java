package com.zerotheabsolute.quantumflux.client.tutorial;

import com.zerotheabsolute.quantumflux.client.PylonStatus;
import com.zerotheabsolute.quantumflux.init.QFDataComponents;
import com.zerotheabsolute.quantumflux.init.QFItems;
import com.zerotheabsolute.quantumflux.item.UpgradeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import static com.zerotheabsolute.quantumflux.client.tutorial.FluxTutorialScreen.*;

final class FluxTutorialScenes {
    private static final String[] CHAPTERS = {"setup", "linking", "power", "status", "upgrades"};
    private static final ResourceLocation BACKDROP = new ResourceLocation("quantumflux", "textures/gui/tutorial_forest.png");
    private static final PylonStatus[] STATES = {PylonStatus.UNLINKED, PylonStatus.NO_POWER,
            PylonStatus.TRANSFERRING, PylonStatus.STANDBY, PylonStatus.PAUSED, PylonStatus.NETWORK_BUFFER};

    private FluxTutorialScenes() {}

    static double[] durations() { return new double[] {24, 24, 24, 36, 24}; }
    static Component title(int chapter) { return text(CHAPTERS[chapter] + ".title"); }
    static int stepCount(int chapter) { return chapter == 3 ? 6 : 4; }
    static int step(int chapter, double time) { return Math.min(stepCount(chapter) - 1, (int) (time / 6)); }
    static Component caption(int chapter, double time) {
        return text(CHAPTERS[chapter] + ".step" + (step(chapter, time) + 1));
    }

    static void render(GuiGraphics graphics, int chapter, double time) {
        // A diagram uses the real item models; the generic FE nodes do not imply a vanilla machine accepts FE.
        graphics.pose().pushPose();
        graphics.pose().scale(480f / 3440, 180f / 1290, 1);
        graphics.blit(BACKDROP, 0, 0, 0, 0, 3440, 1290, 3440, 1440);
        graphics.pose().popPose();
        graphics.fill(0, 0, 480, 180, 0x77071319);
        int step = step(chapter, time);
        switch (chapter) {
            case 0 -> setup(graphics, step, time);
            case 1 -> linking(graphics, step, time);
            case 2 -> power(graphics, step, time);
            case 3 -> status(graphics, step, time);
            case 4 -> upgrades(graphics, step, time);
            default -> throw new IllegalArgumentException("Unknown tutorial chapter");
        }
        int markersLeft = (480 - (stepCount(chapter) * 10 - 4)) / 2;
        for (int index = 0; index < stepCount(chapter); index++) {
            graphics.fill(markersLeft + index * 10, 171, markersLeft + 6 + index * 10, 174, index == step ? ACCENT : 0xFF40565C);
        }
    }

    private static void setup(GuiGraphics graphics, int step, double time) {
        model(graphics, step == 0 && time < 2 ? Assets.GADGET_OFF : Assets.GADGET, 72, 96, 76);
        label(graphics, Assets.GADGET.getHoverName(), 72, 150, TEXT);
        if (step >= 2) {
            model(graphics, Assets.PYLON, 228, 88 + 8 * (1 - TutorialTimeline.transition(time, 12, 13)), 108);
            label(graphics, Assets.PYLON.getHoverName(), 228, 150, TEXT);
        }
        if (step >= 1) {
            panel(graphics, 295, 26, 174, 117);
            graphics.fill(296, 27, 468, 46, 0xFF2B3034);
            label(graphics, Component.translatable("screen.quantumflux.create.title"), 382, 33, ACCENT);
            graphics.drawString(Minecraft.getInstance().font, Component.translatable("screen.quantumflux.create.name"), 305, 54, TEXT, false);
            graphics.fill(305, 68, 459, 85, 0xFF060B0D);
            graphics.renderOutline(305, 68, 154, 17, step == 1 ? ACCENT : 0xFF4D585B);
            String name = text("example_network").getString();
            int letters = step == 1 ? com.zerotheabsolute.quantumflux.util.Numbers.clamp((int) ((time - 6) * 4), 0, name.length()) : name.length();
            graphics.drawString(Minecraft.getInstance().font, name.substring(0, letters), 310, 73, TEXT, false);
            demoButton(graphics, 305, 112, 154, Component.translatable("screen.quantumflux.action.create"));
            for (int color = 0; color < 8; color++) graphics.fill(306 + color * 19, 93, 320 + color * 19, 104,
                    new int[] {ACCENT, 0xFF00CED1, 0xFF4169E1, 0xFF9B59B6, 0xFFFF69B4, 0xFFFF4444, COPPER, TEXT}[color]);
            if (step == 1) cursor(graphics, 342, 77 + (int) (41 * TutorialTimeline.transition(time, 9, 10)), time >= 10 && time < 11);
        }
        if (step == 3) {
            node(graphics, 164, 50, text("source"), 1, COPPER);
            beam(graphics, 190, 50, 219, 97, 1, time, COPPER);
        }
        control(graphics, step == 0, 24, 18);
        if (step == 0) cursor(graphics, 87, 88, time >= 2 && time < 3);
        if (step == 2) cursor(graphics, 229, 103, time >= 14 && time < 15);
    }

    private static void linking(GuiGraphics graphics, int step, double time) {
        int[][] nodes = {{340, 39}, {382, 95}, {324, 145}};
        for (int index = 0; index < nodes.length; index++) {
            boolean linked = step >= 1 && (index == 0 || step >= 2);
            double progress = index == 0 ? TutorialTimeline.transition(time, 7, 9)
                    : TutorialTimeline.transition(time, 13 + index, 14 + index);
            if (linked) beam(graphics, 180, 89, nodes[index][0] - 26, nodes[index][1], progress, -1, ACCENT);
            node(graphics, nodes[index][0], nodes[index][1], text("machine"), 0, linked ? ACCENT : MUTED);
        }
        model(graphics, Assets.PYLON, 176, 91, 126);
        model(graphics, Assets.GADGET, 66, 119, 56);
        label(graphics, text(step == 3 ? "linking_finished" : "linking_active"), 70, 154, step == 3 ? MUTED : ACCENT);
        if (step == 0) graphics.renderOutline(129, 27, 94, 112, COPPER);
        control(graphics, step == 0, 24, 18);
        int targetX = step == 0 ? 177 : step == 1 ? 341 : step == 2 ? 325 : 269;
        int targetY = step == 0 ? 90 : step == 1 ? 39 : step == 2 ? 145 : 23;
        double travel = TutorialTimeline.transition(time % 6, 0, 1.3);
        cursor(graphics, (int) (176 + (targetX - 176) * travel), (int) (90 + (targetY - 90) * travel), time % 6 > 1.3 && time % 6 < 2.3);
    }

    private static void power(GuiGraphics graphics, int step, double time) {
        boolean powered = step > 0;
        double flowTime = powered ? time : -1;
        beam(graphics, 71, 97, 147, 97, 1, flowTime, COPPER);
        beam(graphics, 177, 90, 280, 90, 1, flowTime, ACCENT);
        beam(graphics, 306, 83, 394, 44, 1, step >= 2 ? flowTime : -1, ACCENT);
        beam(graphics, 306, 99, 394, 111, 1, step >= 2 ? flowTime : -1, ACCENT);
        node(graphics, 45, 97, text("source"), powered ? 1 : 0, COPPER);
        model(graphics, Assets.PYLON, 161, 91, 108);
        model(graphics, Assets.PYLON, 291, 91, 108);
        node(graphics, 420, 44, text("machine"), step >= 2 ? .35 + .15 * Math.sin(time) : 0, ACCENT);
        node(graphics, 420, 111, text("machine"), step >= 2 ? .7 + .1 * Math.sin(time) : 0, ACCENT);
        label(graphics, text("input"), 102, 77, COPPER);
        label(graphics, text(step == 3 ? "loaded_only" : "wireless"), 244, 155, ACCENT);
        label(graphics, text("example"), 79, 18, MUTED);
    }

    private static void status(GuiGraphics graphics, int step, double time) {
        PylonStatus state = STATES[step];
        model(graphics, Assets.PYLON, 110, 92, 134);
        panel(graphics, 215, 25, 231, 134);
        graphics.fill(229, 46, 234, 51, state.color());
        graphics.drawString(Minecraft.getInstance().font, state.label(), 241, 44, state.color(), false);
        var font = Minecraft.getInstance().font;
        var lines = font.split(state.hint(), 202);
        int hintY = 60;
        for (var line : lines) {
            graphics.drawString(font, line, 229, hintY, TEXT, false);
            hintY += font.lineHeight;
        }
        int barY = Math.max(119, hintY + 6);
        graphics.fill(229, barY, 431, barY + 7, 0xFF25363B);
        double fill = step == 0 || step == 1 ? 0 : step == 2 ? .45 + Math.sin(time) * .1 : .8;
        graphics.fill(229, barY, 229 + (int) (202 * fill), barY + 7, state.color());
        label(graphics, text("example"), 330, barY + 16, MUTED);
        if (state == PylonStatus.TRANSFERRING) beam(graphics, 141, 88, 203, 88, 1, time, state.color());
    }

    private static void upgrades(GuiGraphics graphics, int step, double time) {
        model(graphics, Assets.PYLON, 92, 92, 130);
        graphics.pose().pushPose();
        graphics.pose().translate(221, 5, 0);
        graphics.pose().scale(.74f, .74f, 1);
        panel(graphics, 0, 0, 244, 218);
        graphics.fill(1, 1, 243, 22, 0xFF2B3034);
        graphics.drawString(Minecraft.getInstance().font, Component.translatable("screen.quantumflux.upgrades.title"), 8, 7, TEXT, false);
        demoButton(graphics, 194, 3, 44, Component.translatable("gui.back"));
        for (int index = 0; index < 4; index++) {
            int x = 27 + index * 60;
            label(graphics, Component.translatable("screen.quantumflux.upgrade_slot." + UpgradeType.values()[index].key()), x, 26, MUTED);
            panel(graphics, x - 9, 38, 18, 18);
            if (index <= step && (index < step || time % 6 >= 2)) {
                graphics.renderItem(Assets.UPGRADES[index], x - 8, 39);
            }
            if (index == step) graphics.renderOutline(x - 9, 38, 18, 18, ACCENT);
        }
        graphics.drawWordWrap(Minecraft.getInstance().font, Component.translatable("tooltip.quantumflux.upgrade." + UpgradeType.values()[step].key()), 8, 67, 228, TEXT);
        graphics.drawString(Minecraft.getInstance().font, Component.translatable("container.inventory"), 41, 121, MUTED, false);
        for (int row = 0; row < 4; row++) for (int col = 0; col < 9; col++) {
            int y = row < 3 ? 132 + row * 18 : 190;
            panel(graphics, 40 + col * 18, y, 18, 18);
        }
        double progress = TutorialTimeline.transition(time % 6, .7, 2);
        int itemX = (int) (49 + step * 18 + (27 + step * 60 - 49 - step * 18) * progress);
        int itemY = (int) (141 - 94 * progress);
        if (time % 6 < 2) graphics.renderItem(Assets.UPGRADES[step], itemX - 8, itemY - 8);
        cursor(graphics, itemX + 4, itemY + 2, time % 6 < .7 || time % 6 >= 2 && time % 6 < 2.8);
        graphics.pose().popPose();
    }

    private static void model(GuiGraphics graphics, ItemStack stack, double x, double y, double size) {
        graphics.pose().pushPose();
        graphics.pose().translate(x - size / 2, y - size / 2, 0);
        graphics.pose().scale((float) size / 16, (float) size / 16, 1);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    private static void demoButton(GuiGraphics graphics, int x, int y, int width, Component label) {
        Button.builder(label, ignored -> {}).bounds(x, y, width, 17).build().render(graphics, -1, -1, 0);
    }

    private static void cursor(GuiGraphics graphics, int x, int y, boolean pressed) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.fill(x, y, x + 2, y + 12, 0xFF202020);
        graphics.fill(x + 2, y + 2, x + 4, y + 10, 0xFF202020);
        graphics.fill(x + 4, y + 4, x + 7, y + 8, 0xFF202020);
        graphics.fill(x + 1, y + 1, x + 2, y + 10, TEXT);
        graphics.fill(x + 2, y + 2, x + 3, y + 8, TEXT);
        graphics.fill(x + 3, y + 4, x + 5, y + 7, TEXT);
        if (pressed) graphics.renderOutline(x - 4, y - 4, 18, 18, COPPER);
        graphics.pose().popPose();
    }

    private static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF18262C);
        graphics.renderOutline(x, y, width, height, 0xFF40565C);
    }

    private static void node(GuiGraphics graphics, int x, int y, Component label, double stored, int color) {
        panel(graphics, x - 26, y - 17, 52, 34);
        graphics.renderOutline(x - 26, y - 17, 52, 34, color);
        label(graphics, Component.literal("FE"), x, y - 10, color);
        graphics.fill(x - 18, y + 4, x + 18, y + 8, 0xFF35464C);
        graphics.fill(x - 18, y + 4, x - 18 + (int) (36 * stored), y + 8, color);
        label(graphics, label, x, y + 21, MUTED);
    }

    private static void label(GuiGraphics graphics, Component value, int x, int y, int color) {
        graphics.drawCenteredString(Minecraft.getInstance().font, value, x, y, color);
    }

    private static void control(GuiGraphics graphics, boolean sneak, int x, int y) {
        var options = Minecraft.getInstance().options;
        Component keys = sneak ? text("sneak_use", options.keyShift.getTranslatedKeyMessage(), options.keyUse.getTranslatedKeyMessage())
                : text("use", options.keyUse.getTranslatedKeyMessage());
        graphics.drawString(Minecraft.getInstance().font, keys, x, y, COPPER, false);
    }

    private static void beam(GuiGraphics graphics, int x1, int y1, int x2, int y2, double progress, double time, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int step = 0; step <= steps * progress; step++) {
            double fraction = step / (double) Math.max(1, steps);
            int x = (int) Math.round(x1 + (x2 - x1) * fraction);
            int y = (int) Math.round(y1 + (y2 - y1) * fraction);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
        if (time < 0) return;
        for (int pulse = 0; pulse < 3; pulse++) {
            double fraction = (time * .45 + pulse / 3.0) % 1;
            if (fraction > progress) continue;
            int x = (int) Math.round(x1 + (x2 - x1) * fraction);
            int y = (int) Math.round(y1 + (y2 - y1) * fraction);
            graphics.fill(x - 1, y - 1, x + 2, y + 2, TEXT);
        }
    }

    private static final class Assets {
        static final ItemStack PYLON = new ItemStack(QFItems.QUANTUM_PYLON.get());
        static final ItemStack GADGET_OFF = new ItemStack(QFItems.QUANTUM_GADGET.get());
        static final ItemStack GADGET = GADGET_OFF.copy();
        static final ItemStack[] UPGRADES = {new ItemStack(QFItems.RANGE_UPGRADE.get()),
                new ItemStack(QFItems.CAPACITY_UPGRADE.get()), new ItemStack(QFItems.THROUGHPUT_UPGRADE.get()),
                new ItemStack(QFItems.BUFFER_UPGRADE.get())};
        static { QFDataComponents.GADGET_ACTIVE.set(GADGET, true); }
    }
}
