package com.tabsorganizer.client;

import net.minecraft.world.item.CreativeModeTab;

/** State of the tab drag that is in progress. */
public final class TabDrag {
    private TabDrag() {}

    /** The mouse must move this many pixels before a click becomes a drag, so a normal click still selects the tab. */
    private static final double START_DISTANCE = 4.0;
    /** How long the mouse must stay on a page arrow while dragging before the page changes. */
    private static final long PAGE_FLIP_DELAY_MS = 500L;

    private static CreativeModeTab pressed;
    private static double startX;
    private static double startY;
    private static boolean dragging;
    private static int hoverDirection;
    private static long hoverSince;

    public static void press(CreativeModeTab tab, double mouseX, double mouseY) {
        pressed = tab;
        startX = mouseX;
        startY = mouseY;
        dragging = false;
        hoverDirection = 0;
    }

    public static void reset() {
        pressed = null;
        dragging = false;
        hoverDirection = 0;
    }

    public static boolean isPressed() {
        return pressed != null;
    }

    public static boolean isDragging() {
        return dragging;
    }

    public static CreativeModeTab tab() {
        return pressed;
    }

    public static void moveTo(double mouseX, double mouseY) {
        if (!dragging && Math.hypot(mouseX - startX, mouseY - startY) >= START_DISTANCE) {
            dragging = true;
        }
    }

    /**
     * Call once per frame with the arrow under the mouse (-1 = previous page, +1 = next page, 0 = none).
     * Returns -1 or +1 when the page must change now, otherwise 0.
     */
    public static int updatePageFlip(int arrowDirection) {
        long now = System.currentTimeMillis();
        if (arrowDirection == 0) {
            hoverDirection = 0;
            return 0;
        }
        if (arrowDirection != hoverDirection) {
            hoverDirection = arrowDirection;
            hoverSince = now;
            return 0;
        }
        if (now - hoverSince >= PAGE_FLIP_DELAY_MS) {
            hoverSince = now;
            return arrowDirection;
        }
        return 0;
    }
}
