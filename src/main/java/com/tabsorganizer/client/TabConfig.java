package com.tabsorganizer.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tabsorganizer.TabsOrganizer;
import com.tabsorganizer.client.TabModel.Slot;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;

/**
 * Settings and tab positions, saved in {@code config/tabsorganizer.json}.
 *
 * <p>This class also holds the layout of the creative screen that is currently open: which tab sits in
 * which slot. The pure placement rules live in {@link TabModel}.
 */
public final class TabConfig {
    private TabConfig() {}

    private static boolean loaded = false;

    // Saved settings
    private static boolean locked = true;
    private static int tabsPerRow = TabModel.MAX_TABS_PER_ROW;
    private static Map<String, Slot> saved = new LinkedHashMap<>();
    /** Merged tabs: host tab id -> ids of the tabs merged into it. */
    private static Map<String, List<String>> groups = new LinkedHashMap<>();
    /** Names chosen by the player: tab id -> name. */
    private static Map<String, String> names = new LinkedHashMap<>();

    /** Number of columns of the list on the left, for each host tab id (1 when not listed). */
    private static Map<String, Integer> sidebarColumns = new LinkedHashMap<>();

    // Layout of the screen that is open right now
    private static Map<String, Slot> layout = new LinkedHashMap<>();
    private static final Map<String, CreativeModeTab> tabById = new LinkedHashMap<>();
    private static final Map<CreativeModeTab, String> idByTab = new HashMap<>();

    // Lookups rebuilt by rebuildCaches() each time the layout or the groups change, so drawing stays cheap
    private static final Set<Integer> usedRows = new HashSet<>();
    private static final Map<CreativeModeTab, List<CreativeModeTab>> membersByHost = new HashMap<>();
    private static int revision = 0;

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("tabsorganizer.json");
    }

    // ---------- Settings ----------

    public static boolean isLocked() {
        ensureLoaded();
        return locked;
    }

    public static void toggleLocked() {
        ensureLoaded();
        locked = !locked;
        save();
    }

    public static int tabsPerRow() {
        ensureLoaded();
        return tabsPerRow;
    }

    /** Forgets every position chosen by the player: the default order comes back. */
    public static void resetAll() {
        ensureLoaded();
        saved = new LinkedHashMap<>();
        groups = new LinkedHashMap<>();
        names = new LinkedHashMap<>();
        sidebarColumns = new LinkedHashMap<>();
        revision++;
        save();
    }

    // ---------- Names ----------

    /** The name to show for a tab: the one chosen by the player, or the original one. */
    public static Component displayName(CreativeModeTab tab) {
        String custom = names.get(idByTab.get(tab));
        return custom != null ? Component.literal(custom) : tab.getDisplayName();
    }

    public static boolean hasCustomName(CreativeModeTab tab) {
        return names.containsKey(idByTab.get(tab));
    }

    /** Gives a tab a new name. An empty name brings the original name back. */
    public static void setName(CreativeModeTab tab, String name) {
        ensureLoaded();
        String id = idByTab.get(tab) != null ? idByTab.get(tab) : idOf(tab);
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            names.remove(id);
        } else {
            names.put(id, clean.length() > 64 ? clean.substring(0, 64) : clean);
        }
        revision++;
        save();
    }

    // ---------- Layout of the open screen ----------

    /**
     * Builds the pages of the creative screen: one list of tabs per page.
     * When the tabs are unlocked, an empty page is added at the end so a tab can be dropped on a new page.
     */
    public static List<List<CreativeModeTab>> buildPages() {
        ensureLoaded();
        tabById.clear();
        idByTab.clear();
        for (CreativeModeTab tab : CreativeModeTabRegistry.getSortedCreativeModeTabs()) {
            if (!tab.hasAnyItems()) {
                continue;
            }
            String id = idOf(tab);
            if (tabById.putIfAbsent(id, tab) == null) {
                idByTab.put(tab, id);
            }
        }

        groups = TabModel.cleanGroups(groups, tabById.keySet());
        Set<String> members = TabModel.allMembers(groups);
        List<String> placed = new ArrayList<>();
        for (String id : tabById.keySet()) {
            if (!members.contains(id)) {
                placed.add(id);
            }
        }
        layout = TabModel.compute(placed, saved, tabsPerRow);
        rebuildCaches();

        int pageCount = Math.max(1, TabModel.pageCount(layout));
        List<List<CreativeModeTab>> pages = new ArrayList<>();
        for (int i = 0; i < pageCount; i++) {
            pages.add(new ArrayList<>());
        }
        for (Map.Entry<String, Slot> entry : layout.entrySet()) {
            pages.get(entry.getValue().page()).add(tabById.get(entry.getKey()));
        }
        if (!locked && !layout.isEmpty()) {
            pages.add(new ArrayList<>());
        }
        return pages;
    }

    /** Rebuilds the quick lookups after the layout or the groups changed. */
    private static void rebuildCaches() {
        usedRows.clear();
        for (Slot slot : layout.values()) {
            usedRows.add(rowKey(slot.page(), slot.top(), slot.row()));
        }
        membersByHost.clear();
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            CreativeModeTab host = tabById.get(entry.getKey());
            if (host == null) {
                continue;
            }
            List<CreativeModeTab> members = new ArrayList<>();
            for (String memberId : entry.getValue()) {
                CreativeModeTab member = tabById.get(memberId);
                if (member != null) {
                    members.add(member);
                }
            }
            membersByHost.put(host, List.copyOf(members));
        }
        revision++;
    }

    private static int rowKey(int page, boolean top, int row) {
        return page * 4 + (top ? 0 : 2) + row;
    }

    /** Changes each time something that the sidebar displays changes (members, order, names). */
    public static int revision() {
        return revision;
    }

    /** True if at least one tab sits on this row of this side of this page. */
    public static boolean hasTabs(int page, boolean top, int row) {
        return usedRows.contains(rowKey(page, top, row));
    }

    // ---------- Sidebar columns ----------

    public static final int MAX_SIDEBAR_COLUMNS = 6;

    /** How many columns the list on the left has for this merged tab (each merged tab has its own setting). */
    public static int sidebarColumns(CreativeModeTab host) {
        Integer value = sidebarColumns.get(idByTab.get(host));
        return value == null ? 1 : Math.max(1, Math.min(MAX_SIDEBAR_COLUMNS, value));
    }

    public static void setSidebarColumns(CreativeModeTab host, int columns) {
        String id = idByTab.get(host);
        if (id == null) {
            return;
        }
        int clean = Math.max(1, Math.min(MAX_SIDEBAR_COLUMNS, columns));
        if (clean == 1) {
            sidebarColumns.remove(id);
        } else {
            sidebarColumns.put(id, clean);
        }
        revision++;
        save();
    }

    // ---------- Merged tabs ----------

    /** The tabs merged into this host tab, in order (empty if the tab is not a host). */
    public static List<CreativeModeTab> membersOf(CreativeModeTab host) {
        List<CreativeModeTab> members = membersByHost.get(host);
        return members == null ? List.of() : members;
    }

    /** The host this tab is merged into, or null if the tab is not merged. */
    public static CreativeModeTab hostOf(CreativeModeTab tab) {
        String id = idByTab.get(tab);
        String host = id == null ? null : TabModel.hostOf(groups, id);
        return host == null ? null : tabById.get(host);
    }

    /**
     * Merges {@code source} into {@code target} (the members of the source come along).
     *
     * @return the tab that now holds the merged content, or null if nothing changed
     */
    public static CreativeModeTab mergeTab(CreativeModeTab source, CreativeModeTab target) {
        String sourceId = idByTab.get(source);
        String targetId = idByTab.get(target);
        if (sourceId == null || targetId == null || layout.get(sourceId) == null) {
            return null;
        }
        Map<String, List<String>> merged = TabModel.merge(groups, sourceId, targetId);
        if (merged == groups) {
            return null;
        }
        groups = merged;
        Map<String, Slot> next = new LinkedHashMap<>(layout);
        next.remove(sourceId);
        layout = TabModel.compactRows(TabModel.compact(next));
        rebuildCaches();
        remember();
        String hostId = TabModel.hostOf(groups, sourceId);
        return tabById.get(hostId);
    }

    /** Moves a merged tab to another position in the list of its host. */
    public static void moveMember(CreativeModeTab host, CreativeModeTab member, int index) {
        String hostId = idByTab.get(host);
        String memberId = idByTab.get(member);
        if (hostId == null || memberId == null) {
            return;
        }
        groups = TabModel.moveMember(groups, hostId, memberId, index);
        rebuildCaches();
        save();
    }

    /** Takes a tab out of its group: it comes back on the first free slot after its former host. */
    public static boolean unmergeTab(CreativeModeTab member) {
        String id = idByTab.get(member);
        String hostId = id == null ? null : TabModel.hostOf(groups, id);
        Slot hostSlot = hostId == null ? null : layout.get(hostId);
        if (hostSlot == null) {
            return false;
        }
        groups = TabModel.unmerge(groups, id);
        Map<String, Slot> next = new LinkedHashMap<>(layout);
        next.put(id, TabModel.freeSlotAfter(layout, hostSlot, tabsPerRow));
        layout = TabModel.compactRows(TabModel.compact(next));
        rebuildCaches();
        remember();
        return true;
    }

    /** The slot of a regular tab, or null for the fixed tabs (search, saved hotbars...). */
    public static Slot slotOf(CreativeModeTab tab) {
        String id = idByTab.get(tab);
        return id == null ? null : layout.get(id);
    }

    /** The tab sitting in this slot, or null if it is empty. */
    public static CreativeModeTab tabAt(Slot slot) {
        String id = TabModel.idAt(layout, slot);
        return id == null ? null : tabById.get(id);
    }

    /**
     * Moves a tab to a slot (an empty slot, or a slot whose tab then takes the old place of the moved tab).
     *
     * @return true if something changed
     */
    public static boolean moveTab(CreativeModeTab tab, Slot target) {
        String id = idByTab.get(tab);
        if (id == null) {
            return false;
        }
        Map<String, Slot> moved = TabModel.move(layout, id, target);
        if (moved.equals(layout)) {
            return false;
        }
        layout = moved;
        rebuildCaches();
        remember();
        return true;
    }

    /** Saves the current positions. Tabs of mods that are not installed right now keep their saved place if it is free. */
    private static void remember() {
        Map<String, Slot> next = new LinkedHashMap<>(layout);
        Set<Slot> taken = new HashSet<>(layout.values());
        Set<String> members = TabModel.allMembers(groups);
        for (Map.Entry<String, Slot> entry : saved.entrySet()) {
            if (!next.containsKey(entry.getKey()) && !members.contains(entry.getKey())
                    && !taken.contains(entry.getValue())) {
                next.put(entry.getKey(), entry.getValue());
            }
        }
        saved = next;
        save();
    }

    private static String idOf(CreativeModeTab tab) {
        ResourceLocation key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
        return key != null ? key.toString() : "unknown:" + tab.getDisplayName().getString();
    }

    // ---------- File ----------

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = file();
        if (!Files.exists(path)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return;
            }
            JsonObject object = root.getAsJsonObject();
            if (object.has("locked")) {
                locked = object.get("locked").getAsBoolean();
            }
            if (object.has("tabsPerRow")) {
                tabsPerRow = Math.max(1, Math.min(TabModel.MAX_TABS_PER_ROW, object.get("tabsPerRow").getAsInt()));
            }
            if (object.has("positions") && object.get("positions").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("positions").entrySet()) {
                    Slot slot = readSlot(entry.getValue());
                    if (slot != null) {
                        saved.put(entry.getKey(), slot);
                    }
                }
            }
            if (object.has("names") && object.get("names").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("names").entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        names.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
            if (object.has("sidebarColumns") && object.get("sidebarColumns").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("sidebarColumns").entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        sidebarColumns.put(entry.getKey(), entry.getValue().getAsInt());
                    }
                }
            }
            if (object.has("groups") && object.get("groups").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("groups").entrySet()) {
                    if (entry.getValue().isJsonArray()) {
                        List<String> members = new ArrayList<>();
                        for (JsonElement member : entry.getValue().getAsJsonArray()) {
                            members.add(member.getAsString());
                        }
                        groups.put(entry.getKey(), members);
                    }
                }
            }
        } catch (Exception e) {
            TabsOrganizer.LOGGER.warn("Could not read tabsorganizer.json, using the default settings", e);
            locked = true;
            tabsPerRow = TabModel.MAX_TABS_PER_ROW;
            saved = new LinkedHashMap<>();
            groups = new LinkedHashMap<>();
            names = new LinkedHashMap<>();
            sidebarColumns = new LinkedHashMap<>();
        }
    }

    private static Slot readSlot(JsonElement element) {
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject o = element.getAsJsonObject();
        try {
            return new Slot(
                    o.get("page").getAsInt(),
                    "top".equals(o.get("side").getAsString()),
                    o.get("row").getAsInt(),
                    o.get("col").getAsInt());
        } catch (Exception e) {
            return null;
        }
    }

    private static void save() {
        JsonObject root = new JsonObject();
        root.addProperty("locked", locked);
        root.addProperty("tabsPerRow", tabsPerRow);
        JsonObject positions = new JsonObject();
        for (Map.Entry<String, Slot> entry : saved.entrySet()) {
            Slot slot = entry.getValue();
            JsonObject o = new JsonObject();
            o.addProperty("page", slot.page());
            o.addProperty("side", slot.top() ? "top" : "bottom");
            o.addProperty("row", slot.row());
            o.addProperty("col", slot.col());
            positions.add(entry.getKey(), o);
        }
        root.add("positions", positions);
        JsonObject groupsJson = new JsonObject();
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            JsonArray array = new JsonArray();
            entry.getValue().forEach(array::add);
            groupsJson.add(entry.getKey(), array);
        }
        root.add("groups", groupsJson);
        JsonObject namesJson = new JsonObject();
        names.forEach(namesJson::addProperty);
        root.add("names", namesJson);
        JsonObject columnsJson = new JsonObject();
        sidebarColumns.forEach(columnsJson::addProperty);
        root.add("sidebarColumns", columnsJson);
        try (Writer writer = Files.newBufferedWriter(file())) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
        } catch (IOException e) {
            TabsOrganizer.LOGGER.warn("Could not write tabsorganizer.json", e);
        }
    }
}
