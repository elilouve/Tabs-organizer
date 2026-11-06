package com.tabsorganizer.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tabsorganizer.client.RenameScreen;
import com.tabsorganizer.client.TabConfig;
import com.tabsorganizer.client.TabControls;
import com.tabsorganizer.client.TabDrag;
import com.tabsorganizer.client.TabLayout;
import com.tabsorganizer.client.TabMerge;
import com.tabsorganizer.client.TabModel.Slot;
import com.tabsorganizer.client.TabSidebar;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.client.gui.CreativeTabsScreenPage;
import net.minecraftforge.common.CreativeModeTabRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin
        extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu> {

    @Shadow
    private CreativeTabsScreenPage currentPage;

    @Shadow
    @Final
    private List<CreativeTabsScreenPage> pages;

    @Shadow
    private static CreativeModeTab selectedTab;

    @Shadow
    private float scrollOffs;

    /** Set when we handled a click, so the matching button release is not handled a second time by the game. */
    private int tabsorganizer$suppressedButton = -1;

    // A press on an entry of the list on the left (a click selects it, a drag moves it)
    private boolean tabsorganizer$listPressed = false;
    private CreativeModeTab tabsorganizer$listTab = null;
    private boolean tabsorganizer$listDragging = false;
    private double tabsorganizer$listX;
    private double tabsorganizer$listY;

    @Shadow
    public abstract void setCurrentPage(CreativeTabsScreenPage page);

    protected CreativeModeInventoryScreenMixin(CreativeModeInventoryScreen.ItemPickerMenu menu,
                                               Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    // ---------- Pages ----------

    // While the screen is being built, tab selections come from the game, not from the player
    @Inject(method = "init()V", at = @At("HEAD"))
    private void tabsorganizer$initStart(CallbackInfo ci) {
        TabLayout.setInitializing(true);
    }

    // Vanilla has just built its own pages: right before it looks at them, replace them with our own layout
    @Inject(method = "init()V", at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z", ordinal = 0))
    private void tabsorganizer$buildPages(CallbackInfo ci) {
        TabSidebar.clearCache();
        this.pages.clear();
        List<CreativeModeTab> fixedTabs = CreativeModeTabRegistry.getDefaultTabs();
        for (List<CreativeModeTab> tabs : TabConfig.buildPages()) {
            CreativeTabsScreenPage page = new CreativeTabsScreenPage(tabs);
            // The fixed tabs (search, hotbars, inventory) must be on every page: add them if Forge did not
            if (!page.getVisibleTabs().containsAll(fixedTabs)) {
                List<CreativeModeTab> all = new ArrayList<>(tabs);
                for (CreativeModeTab fixed : fixedTabs) {
                    if (!all.contains(fixed)) {
                        all.add(fixed);
                    }
                }
                page = new CreativeTabsScreenPage(all);
            }
            this.pages.add(page);
        }
        // The selected tab can be one that is now merged into another tab: show its host instead
        CreativeModeTab host = TabConfig.hostOf(selectedTab);
        if (host != null) {
            TabSidebar.showTab(selectedTab);
            selectedTab = host;
        }
    }

    // The page number moves up, above the tab rows
    @ModifyConstant(method = "render", constant = @Constant(intValue = 44))
    private int tabsorganizer$labelY(int original) {
        return TabLayout.PAGE_LABEL_OFFSET_Y;
    }

    // The < > arrows go to the free corner at the top right
    @Inject(method = "init()V", at = @At("TAIL"))
    private void tabsorganizer$moveArrows(CallbackInfo ci) {
        TabLayout.setInitializing(false);
        for (GuiEventListener child : this.children()) {
            if (child instanceof Button button) {
                String text = button.getMessage().getString();
                if (text.equals("<")) {
                    button.setPosition(this.leftPos + TabLayout.arrowPrevX(this.imageWidth),
                            this.topPos + TabLayout.ARROW_Y);
                } else if (text.equals(">")) {
                    button.setPosition(this.leftPos + TabLayout.arrowNextX(this.imageWidth),
                            this.topPos + TabLayout.ARROW_Y);
                }
            }
        }
    }

    /**
     * Rebuilds the screen (after a move, a merge, a reset or a lock change).
     * Goes back to the given page, or lets the game pick the page of the selected tab if the index is negative.
     */
    private void tabsorganizer$rebuild(int keepPageIndex) {
        this.init(this.minecraft, this.width, this.height);
        if (keepPageIndex >= 0 && !this.pages.isEmpty()) {
            this.setCurrentPage(this.pages.get(Math.min(keepPageIndex, this.pages.size() - 1)));
        }
    }

    // ---------- Drawing the tabs ----------

    // When the player selects a tab, its row comes to the front
    @Inject(method = "selectTab", at = @At("HEAD"))
    private void tabsorganizer$onSelectTab(CreativeModeTab tab, CallbackInfo ci) {
        TabLayout.onTabSelected(tab);
    }

    // ---------- Merged tabs ----------

    // Selecting a tab that is merged into another one selects the host and shows that tab in the list
    @ModifyVariable(method = "selectTab", at = @At("HEAD"), argsOnly = true)
    private CreativeModeTab tabsorganizer$redirectMember(CreativeModeTab tab) {
        CreativeModeTab host = TabConfig.hostOf(tab);
        if (host == null) {
            return tab;
        }
        TabSidebar.showTab(tab);
        return host;
    }

    // The game has filled the window with the items of the selected tab: for a merged tab, replace them
    @Inject(method = "selectTab", at = @At("TAIL"))
    private void tabsorganizer$applyView(CreativeModeTab tab, CallbackInfo ci) {
        this.tabsorganizer$showViewItems();
    }

    private boolean tabsorganizer$sidebarActive() {
        return !TabConfig.membersOf(selectedTab).isEmpty();
    }

    private void tabsorganizer$showViewItems() {
        if (selectedTab.getType() != CreativeModeTab.Type.CATEGORY || !this.tabsorganizer$sidebarActive()) {
            return;
        }
        this.menu.items.clear();
        this.menu.items.addAll(TabSidebar.currentItems(selectedTab));
        this.scrollOffs = 0.0F;
        this.menu.scrollTo(0.0F);
    }

    private void tabsorganizer$merge(CreativeModeTab target) {
        CreativeModeTab host = TabConfig.mergeTab(TabMerge.source(), target);
        TabMerge.cancel();
        if (host != null) {
            selectedTab = host;
            TabSidebar.showGeneral();
            this.tabsorganizer$rebuild(-1);
        }
    }

    private boolean tabsorganizer$consume(int button, CallbackInfoReturnable<Boolean> cir) {
        this.tabsorganizer$suppressedButton = button;
        cir.setReturnValue(true);
        return true;
    }

    // The title in the window: the name chosen by the player, or the name of the entry shown in the list
    @Inject(method = "renderLabels", at = @At("HEAD"), cancellable = true)
    private void tabsorganizer$renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        CreativeModeTab shown = selectedTab;
        if (this.tabsorganizer$sidebarActive()) {
            CreativeModeTab view = TabSidebar.currentView(selectedTab);
            shown = view != null ? view : selectedTab;
        }
        if (shown == selectedTab && !TabConfig.hasCustomName(shown)) {
            return; // nothing special: the game draws the title itself
        }
        if (selectedTab.showTitle()) {
            RenderSystem.disableBlend();
            guiGraphics.drawString(this.font, TabConfig.displayName(shown), 8, 6, selectedTab.getLabelColor(), false);
        }
        ci.cancel();
    }

    /** Opens the small screen where the player types a new name. */
    private void tabsorganizer$openRename(CreativeModeTab tab) {
        TabMerge.cancel();
        String current = TabConfig.displayName(tab).getString();
        this.minecraft.setScreen(new RenameScreen(this, current, name -> TabConfig.setName(tab, name)));
    }

    // The mouse wheel scrolls the list on the left
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void tabsorganizer$mouseScrolled(double mouseX, double mouseY, double scrollY,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (this.tabsorganizer$sidebarActive()
                && TabSidebar.isInside(mouseX - this.leftPos, mouseY - this.topPos, this.leftPos, this.imageHeight, selectedTab)) {
            TabSidebar.scrollBy(scrollY > 0 ? -1 : 1, selectedTab, this.leftPos, this.imageHeight);
            cir.setReturnValue(true);
        }
    }

    // Escape cancels the merge mode instead of closing the screen
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void tabsorganizer$keyPressed(int keyCode, int scanCode, int modifiers,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (keyCode == 256 && TabMerge.isActive()) {
            TabMerge.cancel();
            cir.setReturnValue(true);
        }
    }

    // We draw the unselected tabs ourselves, in the right order
    @Inject(method = "renderBg", at = @At("HEAD"))
    private void tabsorganizer$drawTabs(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY,
                                        CallbackInfo ci) {
        TabLayout.drawUnselectedTabs(guiGraphics, this.font, this.currentPage, selectedTab,
                this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }

    // Vanilla only asks for the selected tab to be drawn (after the window background): we draw it ourselves
    @Inject(method = "renderTabButton", at = @At("HEAD"), cancellable = true)
    private void tabsorganizer$renderTabButton(GuiGraphics guiGraphics, CreativeModeTab tab, CallbackInfo ci) {
        if (tab == selectedTab) {
            TabLayout.draw(guiGraphics, this.font, this.currentPage, tab, true,
                    this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
        }
        ci.cancel();
    }

    @Inject(method = "checkTabClicked", at = @At("HEAD"), cancellable = true)
    private void tabsorganizer$checkTabClicked(CreativeModeTab tab, double relX, double relY,
                                               CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(TabLayout.isInside(this.currentPage, tab, relX, relY,
                this.imageWidth, this.imageHeight));
    }

    @Inject(method = "checkTabHovering", at = @At("HEAD"), cancellable = true)
    private void tabsorganizer$checkTabHovering(GuiGraphics guiGraphics, CreativeModeTab tab,
                                                int mouseX, int mouseY, CallbackInfoReturnable<Boolean> cir) {
        if (TabDrag.isDragging()) {
            cir.setReturnValue(false);
            return;
        }
        boolean inside = TabLayout.isInside(this.currentPage, tab, mouseX - this.leftPos,
                mouseY - this.topPos, this.imageWidth, this.imageHeight);
        if (inside) {
            guiGraphics.renderTooltip(this.font, TabConfig.displayName(tab), mouseX, mouseY);
        }
        cir.setReturnValue(inside);
    }

    // ---------- Buttons and drag and drop ----------

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void tabsorganizer$mouseClicked(double mouseX, double mouseY, int button,
                                            CallbackInfoReturnable<Boolean> cir) {
        double relX = mouseX - this.leftPos;
        double relY = mouseY - this.topPos;
        int pageIndex = this.pages.indexOf(this.currentPage);
        this.tabsorganizer$suppressedButton = -1;
        TabDrag.reset();

        // The list on the left (only there for a merged tab)
        if (this.tabsorganizer$sidebarActive()
                && TabSidebar.isInside(relX, relY, this.leftPos, this.imageHeight, selectedTab)) {
            int control = button == 0
                    ? TabSidebar.controlAt(relX, relY, this.leftPos, this.imageHeight, selectedTab) : 0;
            TabSidebar.Entry entry = TabSidebar.entryAt(relX, relY, this.leftPos, this.imageHeight, selectedTab);
            if (control != 0) {
                // "-" / "+": the number of columns of this list only
                TabConfig.setSidebarColumns(selectedTab, TabSidebar.columnsAfter(selectedTab, this.leftPos, control));
            } else if (entry != null && button == 0) {
                // The click is handled when the button is released (a drag moves the entry instead)
                this.tabsorganizer$listPressed = true;
                this.tabsorganizer$listTab = entry.tab();
                this.tabsorganizer$listDragging = false;
                this.tabsorganizer$listX = mouseX;
                this.tabsorganizer$listY = mouseY;
            } else if (entry != null && button == 1 && !TabConfig.isLocked() && entry.tab() != null) {
                if (Screen.hasShiftDown()) {
                    this.tabsorganizer$openRename(entry.tab());
                } else if (entry.tab() != selectedTab && TabConfig.unmergeTab(entry.tab())) {
                    TabSidebar.showGeneral();
                    this.tabsorganizer$rebuild(-1);
                }
            }
            this.tabsorganizer$consume(button, cir);
            return;
        }

        // Right click on a tab: pick it up to merge it, or drop it into the tab that was picked up
        if (button == 1) {
            if (!TabConfig.isLocked()) {
                CreativeModeTab tab = TabLayout.regularTabAt(this.currentPage, relX, relY,
                        this.imageWidth, this.imageHeight);
                if (tab != null && Screen.hasShiftDown()) {
                    this.tabsorganizer$openRename(tab);
                    this.tabsorganizer$consume(button, cir);
                } else if (tab != null) {
                    if (!TabMerge.isActive()) {
                        TabMerge.start(tab);
                    } else if (TabMerge.source() == tab) {
                        TabMerge.cancel();
                    } else {
                        this.tabsorganizer$merge(tab);
                    }
                    this.tabsorganizer$consume(button, cir);
                } else {
                    TabMerge.cancel();
                }
            }
            return;
        }

        if (button != 0) {
            return;
        }

        // Left click while a tab is picked up: merge into the clicked tab, or cancel
        if (TabMerge.isActive()) {
            CreativeModeTab tab = TabLayout.regularTabAt(this.currentPage, relX, relY,
                    this.imageWidth, this.imageHeight);
            if (tab != null && tab != TabMerge.source()) {
                this.tabsorganizer$merge(tab);
                this.tabsorganizer$consume(button, cir);
                return;
            }
            // Turning the page keeps the merge mode, so a tab can be merged into a tab of another page
            if (TabLayout.arrowAt(relX, relY, this.imageWidth) == 0) {
                TabMerge.cancel();
            }
        }

        boolean overReset = TabControls.isOverReset(relX, relY, this.imageWidth);
        if (!overReset) {
            TabControls.disarmReset(); // a click anywhere else cancels a pending reset
        }

        if (TabControls.isOverLock(relX, relY, this.imageWidth)) {
            TabConfig.toggleLocked();
            TabMerge.cancel();
            this.tabsorganizer$rebuild(pageIndex);
            cir.setReturnValue(true);
            return;
        }

        if (overReset) {
            if (!TabConfig.isLocked()) {
                if (TabControls.isResetArmed()) {
                    TabControls.disarmReset();
                    TabConfig.resetAll();
                    TabLayout.resetFrontRows();
                    TabSidebar.showGeneral();
                    this.tabsorganizer$rebuild(0);
                } else {
                    TabControls.armReset();
                }
            }
            cir.setReturnValue(true);
            return;
        }

        if (!TabConfig.isLocked()) {
            CreativeModeTab tab = TabLayout.regularTabAt(this.currentPage, relX, relY,
                    this.imageWidth, this.imageHeight);
            if (tab != null) {
                TabDrag.press(tab, mouseX, mouseY);
            }
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void tabsorganizer$mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (button == 0 && this.tabsorganizer$listPressed) {
            double dx = mouseX - this.tabsorganizer$listX;
            double dy = mouseY - this.tabsorganizer$listY;
            if (!this.tabsorganizer$listDragging && dx * dx + dy * dy > 16.0 && !TabConfig.isLocked()
                    && this.tabsorganizer$listTab != null && this.tabsorganizer$listTab != selectedTab) {
                this.tabsorganizer$listDragging = true;
            }
            cir.setReturnValue(true);
            return;
        }
        if (button == 0 && TabDrag.isPressed()) {
            TabDrag.moveTo(mouseX, mouseY);
            if (TabDrag.isDragging()) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void tabsorganizer$mouseReleased(double mouseX, double mouseY, int button,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (button == 0 && this.tabsorganizer$listPressed) {
            CreativeModeTab pressed = this.tabsorganizer$listTab;
            boolean dragging = this.tabsorganizer$listDragging;
            this.tabsorganizer$listPressed = false;
            this.tabsorganizer$listDragging = false;
            this.tabsorganizer$listTab = null;
            this.tabsorganizer$suppressedButton = -1;
            if (this.tabsorganizer$sidebarActive()) {
                int index = TabSidebar.indexAt(mouseX - this.leftPos, mouseY - this.topPos,
                        this.leftPos, this.imageHeight, selectedTab);
                if (dragging) {
                    // Entries 0 and 1 are "General" and the host itself: members go from position 2
                    if (index >= 0) {
                        TabConfig.moveMember(selectedTab, pressed, Math.max(0, index - 2));
                        TabSidebar.clearCache();
                    }
                } else if (pressed == null) {
                    TabSidebar.showGeneral();
                    this.tabsorganizer$showViewItems();
                } else {
                    TabSidebar.showTab(pressed);
                    this.tabsorganizer$showViewItems();
                }
            }
            cir.setReturnValue(true);
            return;
        }
        if (button == this.tabsorganizer$suppressedButton) {
            this.tabsorganizer$suppressedButton = -1;
            cir.setReturnValue(true);
            return;
        }
        if (button != 0 || !TabDrag.isPressed()) {
            return;
        }
        boolean wasDragging = TabDrag.isDragging();
        CreativeModeTab dragged = TabDrag.tab();
        TabDrag.reset();
        if (!wasDragging) {
            return; // a simple click: vanilla selects the tab as usual
        }

        int pageIndex = this.pages.indexOf(this.currentPage);
        Slot target = TabLayout.slotAt(mouseX - this.leftPos, mouseY - this.topPos, pageIndex, this.imageHeight);
        if (target != null && TabConfig.moveTab(dragged, target)) {
            this.tabsorganizer$rebuild(pageIndex);
        }
        cir.setReturnValue(true);
    }

    // The two buttons, the dragged tab, the slot hints, and the page that turns when dragging onto an arrow
    @Inject(method = "render", at = @At("TAIL"))
    private void tabsorganizer$renderOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                             float partialTick, CallbackInfo ci) {
        double relX = mouseX - this.leftPos;
        double relY = mouseY - this.topPos;
        boolean locked = TabConfig.isLocked();
        boolean dragging = TabDrag.isDragging();

        boolean overLock = TabControls.isOverLock(relX, relY, this.imageWidth);
        boolean overReset = TabControls.isOverReset(relX, relY, this.imageWidth);
        boolean armed = TabControls.isResetArmed() && !locked;

        TabControls.drawLock(guiGraphics, this.leftPos, this.topPos, this.imageWidth, locked, overLock);
        TabControls.drawReset(guiGraphics, this.leftPos, this.topPos, this.imageWidth, !locked, armed, overReset);

        if (this.tabsorganizer$sidebarActive()) {
            TabSidebar.draw(guiGraphics, this.leftPos, this.topPos, this.imageHeight,
                    mouseX, mouseY, selectedTab, this.tabsorganizer$listDragging ? this.tabsorganizer$listTab : null);
            if (this.tabsorganizer$listDragging && this.tabsorganizer$listTab != null) {
                TabSidebar.drawGhost(guiGraphics, this.tabsorganizer$listTab.getIconItem(), mouseX, mouseY);
            }
        }
        if (TabMerge.isActive()) {
            guiGraphics.drawString(this.font,
                    Component.translatable("tabsorganizer.merge.pick", TabConfig.displayName(TabMerge.source())),
                    this.leftPos, Math.max(2, this.topPos - 78), 0xFFFFD040, true);
        }

        if (!dragging) {
            int listControl = this.tabsorganizer$sidebarActive() && !this.tabsorganizer$listPressed
                    ? TabSidebar.controlAt(relX, relY, this.leftPos, this.imageHeight, selectedTab) : 0;
            if (listControl != 0) {
                String key = listControl < 0 ? "tabsorganizer.sidebar.fewer" : "tabsorganizer.sidebar.more";
                guiGraphics.renderTooltip(this.font, Component.translatable(key), mouseX, mouseY);
                return;
            }
            TabSidebar.Entry listed = this.tabsorganizer$sidebarActive() && !this.tabsorganizer$listPressed
                    ? TabSidebar.entryAt(relX, relY, this.leftPos, this.imageHeight, selectedTab) : null;
            if (listed != null) {
                // The name of the entry, and what can be done with it
                List<Component> lines = new ArrayList<>();
                lines.add(listed.label());
                if (!locked && listed.tab() != null) {
                    lines.add(Component.translatable("tabsorganizer.sidebar.rename").withStyle(ChatFormatting.GRAY));
                    if (listed.tab() != selectedTab) {
                        lines.add(Component.translatable("tabsorganizer.sidebar.unmerge").withStyle(ChatFormatting.GRAY));
                        lines.add(Component.translatable("tabsorganizer.sidebar.reorder").withStyle(ChatFormatting.GRAY));
                    }
                }
                guiGraphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
                return;
            }
            if (overLock) {
                String key = locked ? "tabsorganizer.lock.locked" : "tabsorganizer.lock.unlocked";
                guiGraphics.renderTooltip(this.font, Component.translatable(key), mouseX, mouseY);
            } else if (overReset) {
                String key = locked ? "tabsorganizer.reset.disabled"
                        : armed ? "tabsorganizer.reset.confirm" : "tabsorganizer.reset.ready";
                guiGraphics.renderTooltip(this.font, Component.translatable(key), mouseX, mouseY);
            }
            return;
        }

        int flip = TabDrag.updatePageFlip(TabLayout.arrowAt(relX, relY, this.imageWidth));
        int index = this.pages.indexOf(this.currentPage);
        if (flip != 0 && index >= 0) {
            int newIndex = Mth.clamp(index + flip, 0, this.pages.size() - 1);
            if (newIndex != index) {
                this.setCurrentPage(this.pages.get(newIndex));
            }
        }

        int pageIndex = this.pages.indexOf(this.currentPage);
        Slot hovered = TabLayout.slotAt(relX, relY, pageIndex, this.imageHeight);
        TabLayout.drawSlotHints(guiGraphics, this.currentPage, pageIndex, hovered, TabDrag.tab(),
                this.leftPos, this.topPos, this.imageHeight);
        TabLayout.drawGhost(guiGraphics, TabDrag.tab(), mouseX, mouseY);
    }
}
