package com.tabsorganizer.client;

import com.tabsorganizer.client.TabModel.Slot;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.gui.CreativeTabsScreenPage;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;

/**
 * Where tabs are drawn and where they can be clicked: two stacked rows above the window and two below.
 * The row that holds the selected tab of a side is the "front" row, the other one sticks out behind it.
 */
public final class TabLayout {
    private TabLayout() {}

    public static final int PAGE_LABEL_OFFSET_Y = 66;
    public static final int ARROW_SIZE = 20;
    public static final int ARROW_Y = -51;

    private static final int ROW_OFFSET = 20;
    private static final int TAB_WIDTH = 26;
    private static final int TAB_HEIGHT = 32;

    // The row (0 or 1) the player last brought to the front, for the top side and for the bottom side.
    // It only changes when the player really selects a tab, never when the screen is rebuilt after a move.
    private static int preferredFrontTop = 0;
    private static int preferredFrontBottom = 0;
    private static boolean initializing = false;

    /** Where a tab is drawn, relative to the top left corner of the window. */
    private record Placement(int x, int y, boolean top, int col, boolean back) {}

    // ---------- Page arrows ----------

    public static int arrowPrevX(int imageWidth) {
        return imageWidth - 52;
    }

    public static int arrowNextX(int imageWidth) {
        return imageWidth - 26;
    }

    /** -1 if the mouse is on the "<" arrow, +1 on the ">" arrow, 0 otherwise. */
    public static int arrowAt(double relX, double relY, int imageWidth) {
        if (relY >= ARROW_Y && relY < ARROW_Y + ARROW_SIZE) {
            int prevX = arrowPrevX(imageWidth);
            int nextX = arrowNextX(imageWidth);
            if (relX >= prevX && relX < prevX + ARROW_SIZE) {
                return -1;
            }
            if (relX >= nextX && relX < nextX + ARROW_SIZE) {
                return 1;
            }
        }
        return 0;
    }

    // ---------- Placement ----------

    /** Fixed tabs (search, saved hotbars, survival inventory...) stay on the right of every page. */
    private static boolean isFixed(CreativeModeTab tab) {
        return CreativeModeTabRegistry.getDefaultTabs().contains(tab);
    }

    /**
     * The row that is in front on one side of one page: the preferred one, unless it is empty
     * (then the other one, so a row of tabs is never hidden behind an empty row).
     */
    private static int frontRow(int page, boolean top) {
        int preferred = top ? preferredFrontTop : preferredFrontBottom;
        if (TabConfig.hasTabs(page, top, preferred)) {
            return preferred;
        }
        return TabConfig.hasTabs(page, top, 1 - preferred) ? 1 - preferred : preferred;
    }

    private static boolean isBack(int page, boolean top, int row) {
        return row != frontRow(page, top);
    }

    private static int slotY(int page, boolean top, int row, int imageHeight) {
        int y = top ? -28 : imageHeight - 4;
        if (isBack(page, top, row)) {
            y += top ? -ROW_OFFSET : ROW_OFFSET;
        }
        return y;
    }

    private static Placement slotPlacement(int page, boolean top, int row, int col, int imageHeight) {
        return new Placement(27 * col, slotY(page, top, row, imageHeight), top, col, isBack(page, top, row));
    }

    private static Placement placement(CreativeTabsScreenPage page, CreativeModeTab tab, int imageWidth, int imageHeight) {
        if (isFixed(tab)) {
            boolean top = page.isTop(tab);
            int col = page.getColumn(tab);
            int x = tab.isAlignedRight() ? imageWidth - 27 * (7 - col) + 1 : 27 * col;
            return new Placement(x, top ? -28 : imageHeight - 4, top, col, false);
        }
        Slot slot = TabConfig.slotOf(tab);
        if (slot == null) {
            return null;
        }
        return slotPlacement(slot.page(), slot.top(), slot.row(), slot.col(), imageHeight);
    }

    /** The part of a tab that is really visible and clickable: x, y, width, height. */
    private static int[] hitRect(Placement p) {
        int hitY = p.y();
        int hitH = TAB_HEIGHT;
        if (p.back()) {
            hitH = ROW_OFFSET;
            if (!p.top()) {
                hitY = p.y() + TAB_HEIGHT - ROW_OFFSET;
            }
        }
        return new int[] {p.x(), hitY, TAB_WIDTH, hitH};
    }

    private static boolean contains(int[] rect, double x, double y) {
        return x >= rect[0] && x < rect[0] + rect[2] && y >= rect[1] && y < rect[1] + rect[3];
    }

    /** True if the mouse (relative to the window) is on the visible part of the tab. */
    public static boolean isInside(CreativeTabsScreenPage page, CreativeModeTab tab,
                                   double relX, double relY, int imageWidth, int imageHeight) {
        Placement p = placement(page, tab, imageWidth, imageHeight);
        return p != null && contains(hitRect(p), relX, relY);
    }

    /** The movable tab (not a fixed one) under the mouse, or null. */
    public static CreativeModeTab regularTabAt(CreativeTabsScreenPage page, double relX, double relY,
                                               int imageWidth, int imageHeight) {
        for (CreativeModeTab tab : page.getVisibleTabs()) {
            if (!isFixed(tab) && isInside(page, tab, relX, relY, imageWidth, imageHeight)) {
                return tab;
            }
        }
        return null;
    }

    /** The slot (empty or not) under the mouse on the given page, or null. */
    public static Slot slotAt(double relX, double relY, int pageIndex, int imageHeight) {
        int tabsPerRow = TabConfig.tabsPerRow();
        for (int side = 0; side < 2; side++) {
            boolean top = side == 0;
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < tabsPerRow; col++) {
                    if (contains(hitRect(slotPlacement(pageIndex, top, row, col, imageHeight)), relX, relY)) {
                        return new Slot(pageIndex, top, row, col);
                    }
                }
            }
        }
        return null;
    }

    /** True while the screen is being (re)built: tab selections made by the game itself are not the player's. */
    public static void setInitializing(boolean value) {
        initializing = value;
    }

    /** Called when a tab is selected by the player: its row comes to the front. */
    public static void onTabSelected(CreativeModeTab tab) {
        if (initializing) {
            return;
        }
        Slot slot = TabConfig.slotOf(tab);
        if (slot == null) {
            return;
        }
        if (slot.top()) {
            preferredFrontTop = slot.row();
        } else {
            preferredFrontBottom = slot.row();
        }
    }

    /** Back to the default: the first row is in front. */
    public static void resetFrontRows() {
        preferredFrontTop = 0;
        preferredFrontBottom = 0;
    }

    // ---------- Drawing the tabs ----------

    /** Draws every tab except the selected one: first the tabs in the back rows, then the front ones. */
    public static void drawUnselectedTabs(GuiGraphics g, Font font, CreativeTabsScreenPage page,
                                          CreativeModeTab selected, int leftPos, int topPos,
                                          int imageWidth, int imageHeight) {
        List<CreativeModeTab> tabs = page.getVisibleTabs();
        for (int pass = 0; pass < 2; pass++) {
            boolean backPass = pass == 0;
            for (CreativeModeTab tab : tabs) {
                Placement p = placement(page, tab, imageWidth, imageHeight);
                if (p == null) {
                    continue;
                }
                boolean isSelected = tab == selected;
                // The selected tab in the front row is drawn later by the game itself; one in the back row is drawn here
                if (isSelected && !p.back()) {
                    continue;
                }
                if (p.back() == backPass) {
                    drawAt(g, font, tab, p, isSelected, leftPos, topPos, imageHeight);
                }
            }
        }
    }

    public static void draw(GuiGraphics g, Font font, CreativeTabsScreenPage page, CreativeModeTab tab,
                            boolean selected, int leftPos, int topPos, int imageWidth, int imageHeight) {
        Placement p = placement(page, tab, imageWidth, imageHeight);
        if (p != null && !p.back()) {
            drawAt(g, font, tab, p, selected, leftPos, topPos, imageHeight);
        }
    }

    private static void drawAt(GuiGraphics g, Font font, CreativeModeTab tab, Placement p,
                               boolean selected, int leftPos, int topPos, int imageHeight) {
        int x = leftPos + p.x();
        int y = topPos + p.y();
        int spriteNumber = Mth.clamp(p.col(), 0, 6) + 1;
        ResourceLocation sprite = ResourceLocation.withDefaultNamespace(
                "container/creative_inventory/tab_" + (p.top() ? "top" : "bottom") + "_"
                        + (selected ? "selected" : "unselected") + "_" + spriteNumber);

        // Depth: a tab of the front row is drawn above everything of the back row, icons included.
        // (Item icons are drawn about 150 above the pose they are given, so the front row starts at 160.)
        float base = p.back() ? 0.0F : 160.0F;
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, base);

        // An unselected tab of the front row is partly tucked behind the window (in the game the window is drawn
        // over its edge). Since the tab is now drawn above the window, we cut it at the window's edge instead.
        boolean clipped = !selected && !p.back();
        if (clipped) {
            if (p.top()) {
                g.enableScissor(x - 1, y - 1, x + TAB_WIDTH + 1, topPos);
            } else {
                g.enableScissor(x - 1, topPos + imageHeight, x + TAB_WIDTH + 1, y + TAB_HEIGHT + 1);
            }
        }

        if (p.back() && !selected) {
            g.setColor(0.75F, 0.75F, 0.75F, 1.0F);
        }
        g.blitSprite(sprite, x, y, TAB_WIDTH, TAB_HEIGHT);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        int iconX = x + 5;
        int iconY = y + 8 + (p.top() ? 1 : -1);
        ItemStack icon = tab.getIconItem();
        g.renderItem(icon, iconX, iconY);
        g.renderItemDecorations(font, icon, iconX, iconY);
        if (clipped) {
            g.disableScissor();
        }

        // A merged tab shows how many tabs are inside it
        int merged = TabConfig.membersOf(tab).size();
        if (merged > 0) {
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, p.back() ? 155.0F : 200.0F);
            g.drawString(font, "+" + merged, x + 14, iconY + 9, 0xFFFFD040, true);
            g.pose().popPose();
        }
        // The tab waiting to be merged is outlined
        if (TabMerge.source() == tab) {
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, p.back() ? 155.0F : 200.0F);
            outline(g, x, y, TAB_WIDTH, TAB_HEIGHT, 0xFFFFD040);
            g.pose().popPose();
        }
        g.pose().popPose();
    }

    // ---------- Drag and drop overlay ----------

    /**
     * While a tab is dragged: outlines every empty slot of the page, and lights up the slot under the mouse
     * (an empty slot, or a tab that will swap places with the dragged one).
     */
    public static void drawSlotHints(GuiGraphics g, CreativeTabsScreenPage page, int pageIndex, Slot hovered,
                                     CreativeModeTab dragged, int leftPos, int topPos, int imageHeight) {
        int tabsPerRow = TabConfig.tabsPerRow();
        boolean[][][] occupied = new boolean[2][2][TabModel.MAX_TABS_PER_ROW];
        for (CreativeModeTab tab : page.getVisibleTabs()) {
            Slot slot = TabConfig.slotOf(tab);
            if (slot != null) {
                occupied[slot.top() ? 0 : 1][slot.row()][slot.col()] = true;
            }
        }
        Slot draggedSlot = page.getVisibleTabs().contains(dragged) ? TabConfig.slotOf(dragged) : null;

        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);
        for (int side = 0; side < 2; side++) {
            boolean top = side == 0;
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < tabsPerRow; col++) {
                    if (draggedSlot != null && draggedSlot.top() == top && draggedSlot.row() == row && draggedSlot.col() == col) {
                        continue; // the slot the tab comes from
                    }
                    boolean isHovered = hovered != null && hovered.top() == top && hovered.row() == row && hovered.col() == col;
                    int[] r = hitRect(slotPlacement(pageIndex, top, row, col, imageHeight));
                    int x = leftPos + r[0];
                    int y = topPos + r[1];
                    if (isHovered) {
                        g.fill(x, y, x + r[2], y + r[3], 0x70FFFFFF);
                    } else if (!occupied[side][row][col]) {
                        g.fill(x, y, x + r[2], y + r[3], 0x25FFFFFF);
                        outline(g, x, y, r[2], r[3], 0xA0FFFFFF);
                    }
                }
            }
        }
        g.pose().popPose();
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** The tab that follows the mouse while it is dragged. */
    public static void drawGhost(GuiGraphics g, CreativeModeTab tab, int mouseX, int mouseY) {
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 400.0F);
        ResourceLocation sprite =
                ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_1");
        g.blitSprite(sprite, mouseX - 13, mouseY - 16, TAB_WIDTH, TAB_HEIGHT);
        g.renderItem(tab.getIconItem(), mouseX - 8, mouseY - 7);
        g.pose().popPose();
    }
}
