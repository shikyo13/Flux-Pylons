package com.zerotheabsolute.quantumflux.client.screen;

import com.zerotheabsolute.quantumflux.client.PylonReadout;
import com.zerotheabsolute.quantumflux.item.UpgradeType;
import com.zerotheabsolute.quantumflux.menu.PylonUpgradeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;

import static com.zerotheabsolute.quantumflux.client.screen.GadgetScreenTheme.*;

/** A real inventory screen with the same instrument palette as the controller. */
public final class PylonUpgradeScreen extends AbstractContainerScreen<PylonUpgradeMenu> {
    public PylonUpgradeScreen(PylonUpgradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 244;
        imageHeight = 218;
        inventoryLabelX = 41;
        inventoryLabelY = 121;
        titleLabelX = 8;
        titleLabelY = 7;
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(leftPos + imageWidth - 50, topPos + 3, 44, 16).build());
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BG);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 22, HEADER_BG);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, BORDER_DIM);
        for (var slot : menu.slots) {
            graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1,
                    leftPos + slot.x + 17, topPos + slot.y + 17, PANEL);
            graphics.renderOutline(leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18, BORDER_DIM);
        }
        if (!menu.canRemoveBuffers()) graphics.renderOutline(leftPos + 198, topPos + 38, 18, 18, YELLOW);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        for (UpgradeType type : UpgradeType.values()) {
            graphics.drawCenteredString(font, Component.translatable("screen.quantumflux.upgrade_slot." + type.key()),
                    27 + type.ordinal() * 60, 26, TEXT_DIM);
        }
        graphics.drawString(font, Component.translatable("screen.quantumflux.upgrades.range", menu.range()), 8, 65, TEXT);
        graphics.drawString(font, Component.translatable("screen.quantumflux.upgrades.links", menu.connections(), menu.maxConnections()), 128, 65, TEXT);
        graphics.drawString(font, Component.translatable("screen.quantumflux.upgrades.output", PylonReadout.formatRate(menu.transferLimit())), 8, 80, TEXT);
        graphics.drawString(font, Component.translatable("screen.quantumflux.upgrades.buffer", PylonReadout.formatEnergy(menu.bufferLimit())), 128, 80, TEXT);
        boolean excess = menu.storedEnergy() > menu.bufferLimit();
        graphics.drawString(font, Component.translatable(excess ? "screen.quantumflux.upgrades.excess" : "screen.quantumflux.upgrades.stored",
                PylonReadout.formatEnergy(menu.storedEnergy())), 8, 94, excess ? YELLOW : TEXT_DIM);
        graphics.drawString(font, menu.canRemoveBuffers()
                ? Component.translatable("screen.quantumflux.upgrades.help")
                : Component.translatable("screen.quantumflux.upgrades.buffer_locked", PylonReadout.formatEnergy(menu.baseBufferLimit())),
                8, 106, menu.canRemoveBuffers() ? TEXT_DIM : YELLOW);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_DIM, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int slot = hoveredSlot == null ? -1 : menu.slots.indexOf(hoveredSlot);
        if (slot >= 0 && slot < UpgradeType.values().length) {
            UpgradeType type = UpgradeType.values()[slot];
            var lines = new ArrayList<Component>();
            lines.add(Component.translatable("screen.quantumflux.upgrade_slot." + type.key()));
            lines.add(Component.translatable("tooltip.quantumflux.upgrade." + type.key()));
            lines.add(Component.translatable("screen.quantumflux.upgrades.preview",
                    PylonReadout.formatEnergy(menu.previewValue(type, 0)),
                    PylonReadout.formatEnergy(menu.previewValue(type, 1))));
            if (type == UpgradeType.BUFFER && !menu.canRemoveBuffers()) lines.add(Component.translatable(
                    "screen.quantumflux.upgrades.buffer_locked", PylonReadout.formatEnergy(menu.baseBufferLimit())));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else renderTooltip(graphics, mouseX, mouseY);
    }

    @Override public void onClose() {
        super.onClose();
        if (minecraft != null) minecraft.setScreen(new GadgetScreen(menu.pylonPos()));
    }
}
