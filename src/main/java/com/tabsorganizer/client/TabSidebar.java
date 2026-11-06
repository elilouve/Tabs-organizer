package com.tabsorganizer.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The panel on the left of the window that is shown when the selected tab is a merged tab.
 * It lists "General" (all the items of all the merged tabs, sorted A-Z), the host tab itself, then every tab
 * merged into it, as a grid of icons. Clicking an entry shows the items of that tab. Two small buttons at the
 * top change the number of columns, separately for each merged tab.
 */
public final class TabSidebar {
    private TabSidebar() {}

    private static final int ROW_HEIGHT = 22;
    private static final int PADDING = 4;
    /** Height of the strip at the top of the panel that holds the "-" and "+" buttons. */
    private static final int HEADER = 14;
    private static final int BUTTON = 10;

    /** One square of the grid. {@code tab} is null for the "General" entry. */
    public record Entry(CreativeModeTab tab, Component label, ItemStack icon) {}

    /** The tab whose items are shown, or null for "General". */
    private static CreativeModeTab view = null;
    private static int scroll = 0;
    private static CreativeModeTab scrollOwner = null;
    private static final Map<CreativeModeTab, List<ItemStack>> generalCache = new HashMap<>();

    // The list of entries is kept until something changes (members, order, names)
    private static ItemStack generalIcon = null;
    private static CreativeModeTab cachedHost = null;
    private static int cachedRevision = -1;
    private static List<Entry> cachedEntries = List.of();

    /** Forget the sorted lists (the content of the tabs may have changed). */
    public static void clearCache() {
        generalCache.clear();
        cachedHost = null;
    }

    public static void showTab(CreativeModeTab tab) {
        view = tab;
    }

    public static void showGeneral() {
        view = null;
    }

    // ---------- Entries ----------

    public static List<Entry> entries(CreativeModeTab host) {
        if (host == cachedHost && cachedRevision == TabConfig.revision()) {
            return cachedEntries;
        }
        if (generalIcon == null) {
            generalIcon = new ItemStack(Items.COMPASS);
        }
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(null, Component.translatable("tabsorganizer.sidebar.general"), generalIcon));
        entries.add(new Entry(host, TabConfig.displayName(host), host.getIconItem()));
        for (CreativeModeTab member : TabConfig.membersOf(host)) {
            entries.add(new Entry(member, TabConfig.displayName(member), member.getIconItem()));
        }
        cachedHost = host;
        cachedRevision = TabConfig.revision();
        cachedEntries = entries;
        return entries;
    }

    /** The view that is really shown for this host (a view that does not belong to the host falls back to General). */
    public static CreativeModeTab currentView(CreativeModeTab host) {
        if (view != null && view != host && !TabConfig.membersOf(host).contains(view)) {
            view = null;
        }
        return view;
    }

    // ---------- Items ----------

    /** The items to show in the creative window for this host and the current view. */
    public static List<ItemStack> currentItems(CreativeModeTab host) {
        CreativeModeTab shown = currentView(host);
        if (shown != null) {
            return new ArrayList<>(shown.getDisplayItems());
        }
        return generalCache.computeIfAbsent(host, TabSidebar::buildGeneral);
    }

    private record Sortable(ItemStack stack, String name, String id) {}

    /** Every item of the host and of its merged tabs, once each, sorted A-Z by name. */
    private static List<ItemStack> buildGeneral(CreativeModeTab host) {
        List<CreativeModeTab> sources = new ArrayList<>();
        sources.add(host);
        sources.addAll(TabConfig.membersOf(host));

        Map<net.minecraft.world.item.Item, List<ItemStack>> seen = new HashMap<>();
        List<Sortable> all = new ArrayList<>();
        for (CreativeModeTab source : sources) {
            for (ItemStack stack : source.getDisplayItems()) {
                List<ItemStack> sameItem = seen.computeIfAbsent(stack.getItem(), k -> new ArrayList<>());
                boolean duplicate = false;
                for (ItemStack other : sameItem) {
                    if (ItemStack.matches(other, stack)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    sameItem.add(stack);
                    all.add(new Sortable(stack, stack.getHoverName().getString().toLowerCase(Locale.ROOT),
                            String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()))));
                }
            }
        }
        all.sort(Comparator.comparing(Sortable::name).thenComparing(Sortable::id));
        List<ItemStack> result = new ArrayList<>(all.size());
        for (Sortable s : all) {
            result.add(s.stack());
        }
        return result;
    }

    // ---------- Geometry ----------

    /** Columns really used: the chosen number, limited by the room there is on the left of the window. */
    private static int columns(CreativeModeTab host, int leftPos) {
        int room = Math.max(1, (leftPos - 4 - 2 * PADDING) / ROW_HEIGHT);
        return Math.max(1, Math.min(TabConfig.sidebarColumns(host), room));
    }

    private static int width(int columns) {
        return columns * ROW_HEIGHT + 2 * PADDING;
    }

    /** Left edge of the panel, relative to the window. */
    private static int left(int columns) {
        return -width(columns) - 3;
    }

    private static int totalRows(int entryCount, int columns) {
        return (entryCount + columns - 1) / columns;
    }

    private static int visibleRows(int imageHeight) {
        return Math.max(1, (imageHeight - HEADER - 2 * PADDING) / ROW_HEIGHT);
    }

    /** The panel grows with the number of entries, up to the height of the creative window (then it scrolls). */
    private static int panelHeight(int entryCount, int columns, int imageHeight) {
        return Math.min(imageHeight, HEADER + totalRows(entryCount, columns) * ROW_HEIGHT + 2 * PADDING);
    }

    private static int clampScroll(CreativeModeTab host, int totalRows, int imageHeight) {
        if (scrollOwner != host) {
            scrollOwner = host;
            scroll = 0;
        }
        scroll = Mth.clamp(scroll, 0, Math.max(0, totalRows - visibleRows(imageHeight)));
        return scroll;
    }

    /** True if the mouse (relative to the window) is anywhere on the panel. */
    public static boolean isInside(double relX, double relY, int leftPos, int imageHeight, CreativeModeTab host) {
        int cols = columns(host, leftPos);
        int x = left(cols);
        int h = panelHeight(entries(host).size(), cols, imageHeight);
        return relX >= x && relX < x + width(cols) && relY >= 0 && relY < h;
    }

    /** -1 on the "-" button, +1 on the "+" button, 0 elsewhere. */
    public static int controlAt(double relX, double relY, int leftPos, int imageHeight, CreativeModeTab host) {
        if (!isInside(relX, relY, leftPos, imageHeight, host)) {
            return 0;
        }
        int x = left(columns(host, leftPos)) + PADDING;
        int y = (HEADER - BUTTON) / 2;
        if (relY >= y && relY < y + BUTTON) {
            if (relX >= x && relX < x + BUTTON) {
                return -1;
            }
            if (relX >= x + BUTTON + 2 && relX < x + 2 * BUTTON + 2) {
                return 1;
            }
        }
        return 0;
    }

    /** The number of columns this merged tab would have after pressing "-" (-1) or "+" (+1). */
    public static int columnsAfter(CreativeModeTab host, int leftPos, int delta) {
        int room = Math.max(1, (leftPos - 4 - 2 * PADDING) / ROW_HEIGHT);
        return Math.max(1, Math.min(Math.min(TabConfig.MAX_SIDEBAR_COLUMNS, room), columns(host, leftPos) + delta));
    }

    /** Position (in {@link #entries}) of the entry under the mouse, or -1. */
    public static int indexAt(double relX, double relY, int leftPos, int imageHeight, CreativeModeTab host) {
        if (!isInside(relX, relY, leftPos, imageHeight, host)) {
            return -1;
        }
        int cols = columns(host, leftPos);
        List<Entry> entries = entries(host);
        int first = clampScroll(host, totalRows(entries.size(), cols), imageHeight);
        double insideX = relX - left(cols) - PADDING;
        double insideY = relY - HEADER - PADDING;
        if (insideX < 0 || insideY < 0) {
            return -1;
        }
        int col = (int) (insideX / ROW_HEIGHT);
        int row = (int) (insideY / ROW_HEIGHT);
        int index = (first + row) * cols + col;
        if (col >= cols || row >= visibleRows(imageHeight) || index >= entries.size()) {
            return -1;
        }
        return index;
    }

    /** The entry under the mouse, or null. */
    public static Entry entryAt(double relX, double relY, int leftPos, int imageHeight, CreativeModeTab host) {
        int index = indexAt(relX, relY, leftPos, imageHeight, host);
        return index < 0 ? null : entries(host).get(index);
    }

    /** Scrolls the grid with the mouse wheel, one row at a time. */
    public static void scrollBy(int rows, CreativeModeTab host, int leftPos, int imageHeight) {
        int total = totalRows(entries(host).size(), columns(host, leftPos));
        clampScroll(host, total, imageHeight);
        scroll += rows;
        clampScroll(host, total, imageHeight);
    }

    // ---------- Drawing ----------

    /**
     * Draws the panel in the style of the creative window: a grey bevelled panel with one slot-like square per
     * entry. Only the icons are shown; the names are in the tooltips, like for the tabs.
     *
     * @param dragged the tab being dragged inside the panel, or null
     */
    public static void draw(GuiGraphics g, int leftPos, int topPos, int imageHeight,
                            int mouseX, int mouseY, CreativeModeTab host, CreativeModeTab dragged) {
        int cols = columns(host, leftPos);
        List<Entry> entries = entries(host);
        int totalRows = totalRows(entries.size(), cols);
        int first = clampScroll(host, totalRows, imageHeight);
        int rows = visibleRows(imageHeight);
        CreativeModeTab shown = currentView(host);
        int hovered = indexAt(mouseX - leftPos, mouseY - topPos, leftPos, imageHeight, host);
        int control = controlAt(mouseX - leftPos, mouseY - topPos, leftPos, imageHeight, host);

        int w = width(cols);
        int x = leftPos + left(cols);
        int y = topPos;
        int h = panelHeight(entries.size(), cols, imageHeight);

        // Window frame: black outline, light top/left edge, dark bottom/right edge
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + w, y + h, 0xFFC6C6C6);
        g.fill(x, y, x + w, y + 2, 0xFFFFFFFF);
        g.fill(x, y, x + 2, y + h, 0xFFFFFFFF);
        g.fill(x, y + h - 2, x + w, y + h, 0xFF555555);
        g.fill(x + w - 2, y, x + w, y + h, 0xFF555555);

        // The "-" and "+" buttons
        int buttonY = y + (HEADER - BUTTON) / 2;
        drawButton(g, x + PADDING, buttonY, false, control == -1, columnsAfter(host, leftPos, -1) != cols);
        drawButton(g, x + PADDING + BUTTON + 2, buttonY, true, control == 1, columnsAfter(host, leftPos, 1) != cols);

        int slot = ROW_HEIGHT - 2;
        int gridY = y + HEADER + PADDING;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int i = (first + row) * cols + col;
                if (i >= entries.size()) {
                    break;
                }
                Entry entry = entries.get(i);
                int slotX = x + PADDING + col * ROW_HEIGHT + 1;
                int slotY = gridY + row * ROW_HEIGHT + 1;
                boolean selected = entry.tab() == shown;

                // Slot: dark top/left edge, light bottom/right edge, grey inside (like a vanilla inventory slot)
                g.fill(slotX, slotY, slotX + slot, slotY + slot, 0xFF373737);
                g.fill(slotX + 1, slotY + 1, slotX + slot, slotY + slot, 0xFFFFFFFF);
                g.fill(slotX + 1, slotY + 1, slotX + slot - 1, slotY + slot - 1, selected ? 0xFFD8C27A : 0xFF8B8B8B);
                if (selected) {
                    outline(g, slotX - 1, slotY - 1, slot + 2, slot + 2, 0xFFFFD040);
                }
                g.renderItem(entry.icon(), slotX + (slot - 16) / 2, slotY + (slot - 16) / 2);
                if (i == hovered || (dragged != null && entry.tab() == dragged)) {
                    g.fill(slotX + 1, slotY + 1, slotX + slot - 1, slotY + slot - 1, 0x80FFFFFF);
                }
            }
        }

        // A thin bar on the right edge shows that the grid can be scrolled
        if (totalRows > rows) {
            int trackY = y + HEADER + PADDING;
            int trackHeight = h - HEADER - 2 * PADDING;
            int barHeight = Math.max(6, trackHeight * rows / totalRows);
            int barY = trackY + (trackHeight - barHeight) * first / (totalRows - rows);
            g.fill(x + w - 4, barY, x + w - 2, barY + barHeight, 0xFF707070);
        }
    }

    /** A small bevelled button with a "-" or a "+" drawn in it. Greyed out when it can do nothing. */
    private static void drawButton(GuiGraphics g, int x, int y, boolean plus, boolean hovered, boolean enabled) {
        g.fill(x, y, x + BUTTON, y + BUTTON, 0xFF373737);
        g.fill(x + 1, y + 1, x + BUTTON, y + BUTTON, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + BUTTON - 1, y + BUTTON - 1, hovered && enabled ? 0xFFA8A8A8 : 0xFF8B8B8B);
        int color = enabled ? 0xFF202020 : 0xFF6A6A6A;
        g.fill(x + 2, y + BUTTON / 2 - 1, x + BUTTON - 2, y + BUTTON / 2, color);
        if (plus) {
            g.fill(x + BUTTON / 2 - 1, y + 2, x + BUTTON / 2, y + BUTTON - 2, color);
        }
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** The icon that follows the mouse while an entry is dragged. */
    public static void drawGhost(GuiGraphics g, ItemStack icon, int mouseX, int mouseY) {
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 400.0F);
        g.renderItem(icon, mouseX - 8, mouseY - 8);
        g.pose().popPose();
    }
}
