package com.tabsorganizer.client;

import net.minecraft.client.gui.GuiGraphics;

/** The two small buttons above the creative window: the lock and the "reset all" button. */
public final class TabControls {
    private TabControls() {}

    private static final int SIZE = 14;
    private static final int Y = -68;
    /** After a first click on "reset all", the player has this long to click again to confirm. */
    private static final long CONFIRM_WINDOW_MS = 3000L;

    private static long resetArmedUntil = 0L;

    // The icon of the reset button, one character per pixel ("X" = painted).
    private static final String[] RESET_ICON = {
        "..XXXX.X.",
        ".X....XX.",
        "X....XXXX",
        "X.......X",
        "X........",
        "X.......X",
        ".X.....X.",
        "..X...X..",
        "...XXX...",
    };

    private static int lockX(int imageWidth) {
        return imageWidth - 16;
    }

    private static int resetX(int imageWidth) {
        return imageWidth - 32;
    }

    private static boolean inside(double relX, double relY, int x) {
        return relX >= x && relX < x + SIZE && relY >= Y && relY < Y + SIZE;
    }

    public static boolean isOverLock(double relX, double relY, int imageWidth) {
        return inside(relX, relY, lockX(imageWidth));
    }

    public static boolean isOverReset(double relX, double relY, int imageWidth) {
        return inside(relX, relY, resetX(imageWidth));
    }

    // ---------- Reset confirmation ----------

    public static boolean isResetArmed() {
        return System.currentTimeMillis() < resetArmedUntil;
    }

    public static void armReset() {
        resetArmedUntil = System.currentTimeMillis() + CONFIRM_WINDOW_MS;
    }

    public static void disarmReset() {
        resetArmedUntil = 0L;
    }

    // ---------- Drawing ----------

    public static void drawLock(GuiGraphics g, int leftPos, int topPos, int imageWidth, boolean locked, boolean hovered) {
        int px = leftPos + lockX(imageWidth);
        int py = topPos + Y;
        g.fill(px, py, px + SIZE, py + SIZE, hovered ? 0xCC505050 : 0xAA000000);

        int gx = px + 2;
        int gy = py + 2;
        int bodyColor = locked ? 0xFFD9A441 : 0xFF7CCB5A;
        int metalColor = 0xFFC8C8C8;
        g.fill(gx + 1, gy + 5, gx + 9, gy + 10, bodyColor);      // body
        g.fill(gx + 2, gy, gx + 8, gy + 1, metalColor);          // top of the shackle
        g.fill(gx + 2, gy + 1, gx + 3, gy + 5, metalColor);      // left side
        if (locked) {
            g.fill(gx + 7, gy + 1, gx + 8, gy + 5, metalColor);  // right side: the shackle is closed
        }
    }

    /**
     * Grey when the tabs are locked (the button cannot be used), white when it can be used,
     * red while it waits for the confirmation click.
     */
    public static void drawReset(GuiGraphics g, int leftPos, int topPos, int imageWidth,
                                 boolean enabled, boolean armed, boolean hovered) {
        int px = leftPos + resetX(imageWidth);
        int py = topPos + Y;
        int background = armed ? 0xDD8B1A1A : hovered && enabled ? 0xCC505050 : 0xAA000000;
        g.fill(px, py, px + SIZE, py + SIZE, background);

        int color = !enabled ? 0xFF6A6A6A : armed ? 0xFFFFFFFF : 0xFFE0E0E0;
        int ox = px + (SIZE - 9) / 2;
        int oy = py + (SIZE - 9) / 2;
        for (int row = 0; row < RESET_ICON.length; row++) {
            for (int col = 0; col < RESET_ICON[row].length(); col++) {
                if (RESET_ICON[row].charAt(col) == 'X') {
                    g.fill(ox + col, oy + row, ox + col + 1, oy + row + 1, color);
                }
            }
        }
    }
}
