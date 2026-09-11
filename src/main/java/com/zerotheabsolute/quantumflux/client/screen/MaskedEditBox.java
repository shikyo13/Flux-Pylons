package com.zerotheabsolute.quantumflux.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Edit box that never renders or narrates the entered password. */
final class MaskedEditBox extends EditBox {

    MaskedEditBox(Font font, int x, int y, int width, int height, Component label) {
        super(font, x, y, width, height, label);
        setFormatter((value, offset) -> FormattedCharSequence.forward("*".repeat(value.length()), Style.EMPTY));
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        return Component.translatable("screen.quantumflux.password.narration", getValue().length());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.isCopy(keyCode) || Screen.isCut(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
