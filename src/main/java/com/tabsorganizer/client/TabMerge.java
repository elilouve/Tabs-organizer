package com.tabsorganizer.client;

import net.minecraft.world.item.CreativeModeTab;

/**
 * The "merge mode". A right click on a tab picks it up; the next click on another tab merges the first one
 * into it. Escape, or a click anywhere else, cancels.
 */
public final class TabMerge {
    private TabMerge() {}

    private static CreativeModeTab source = null;

    public static CreativeModeTab source() {
        return source;
    }

    public static boolean isActive() {
        return source != null;
    }

    public static void start(CreativeModeTab tab) {
        source = tab;
    }

    public static void cancel() {
        source = null;
    }
}
