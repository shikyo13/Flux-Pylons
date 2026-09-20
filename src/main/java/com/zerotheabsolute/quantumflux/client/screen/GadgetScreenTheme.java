package com.zerotheabsolute.quantumflux.client.screen;

/** Shared layout and palette values for the gadget screen and its renderer. */
final class GadgetScreenTheme {
    static final int MAX_WIDTH = 440;
    static final int MAX_HEIGHT = 330;
    static final int OUTER_MARGIN = 8;
    static final int HEADER_HEIGHT = 25;
    static final int TAB_HEIGHT = 20;
    static final int CONTENT_GAP = 5;
    static final int STATUS_HEIGHT = 17;
    static final int BUTTON_HEIGHT = 20;
    static final int ROW_GAP = 2;

    static final int BG = com.zeromods.core.ui.UiTheme.FLUX.background();
    static final int HEADER_BG = 0xF02B3034;
    static final int PANEL = com.zeromods.core.ui.UiTheme.FLUX.panel();
    static final int BORDER = com.zeromods.core.ui.UiTheme.FLUX.border();
    static final int BORDER_DIM = 0xFF4D585B;
    static final int TEXT = com.zeromods.core.ui.UiTheme.FLUX.text();
    static final int TEXT_DIM = com.zeromods.core.ui.UiTheme.FLUX.muted();
    static final int ACCENT = com.zeromods.core.ui.UiTheme.FLUX.accent();
    static final int GREEN = com.zeromods.core.ui.UiTheme.FLUX.success();
    static final int RED = com.zeromods.core.ui.UiTheme.FLUX.error();
    static final int YELLOW = com.zeromods.core.ui.UiTheme.FLUX.warning();
    static final int BAR_BG = 0xFF20283A;
    static final int BAR_FG = 0xFF23C483;

    private GadgetScreenTheme() {}
}
