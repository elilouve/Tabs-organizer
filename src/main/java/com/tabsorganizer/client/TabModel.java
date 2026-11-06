package com.tabsorganizer.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pure layout logic, with no Minecraft classes, so it is easy to reason about and to test.
 *
 * <p>Every regular creative tab owns exactly one {@link Slot}: a page, a side (top or bottom of the
 * window), a row (0 or 1) and a column. Slots can stay empty, which is what lets the player drop a tab
 * anywhere, including on an empty spot or on a brand new page.
 */
public final class TabModel {
    private TabModel() {}

    /** The window is 7 columns wide and the 2 rightmost ones are used by the fixed tabs. */
    public static final int MAX_TABS_PER_ROW = 5;

    /** One position for a tab. */
    public record Slot(int page, boolean top, int row, int col) {
        /** Position of this slot in the default filling order of its page (top rows first, then bottom rows). */
        public int indexInPage(int tabsPerRow) {
            int base = top ? 0 : 2 * tabsPerRow;
            return base + row * tabsPerRow + col;
        }

        /** Same side, row and column, on another page. */
        public Slot onPage(int newPage) {
            return new Slot(newPage, top, row, col);
        }
    }

    public static int slotsPerPage(int tabsPerRow) {
        return 4 * tabsPerRow;
    }

    public static Slot fromGlobalIndex(int globalIndex, int tabsPerRow) {
        int perPage = slotsPerPage(tabsPerRow);
        int page = globalIndex / perPage;
        int i = globalIndex % perPage;
        boolean top = i < 2 * tabsPerRow;
        int j = top ? i : i - 2 * tabsPerRow;
        return new Slot(page, top, j / tabsPerRow, j % tabsPerRow);
    }

    public static int toGlobalIndex(Slot slot, int tabsPerRow) {
        return slot.page() * slotsPerPage(tabsPerRow) + slot.indexInPage(tabsPerRow);
    }

    public static boolean isValid(Slot slot, int tabsPerRow) {
        return slot != null
                && slot.page() >= 0 && slot.page() < 1000
                && slot.row() >= 0 && slot.row() < 2
                && slot.col() >= 0 && slot.col() < tabsPerRow;
    }

    /**
     * Gives a slot to every tab id.
     *
     * <p>Tabs that have a valid saved slot keep it. The others (new tabs, or the very first launch) are put
     * in the default order, after the last slot that is already used.
     *
     * @param ids tab ids, in the default order, without duplicates
     * @param saved slots saved by the player, may contain ids that are not installed anymore
     */
    public static Map<String, Slot> compute(List<String> ids, Map<String, Slot> saved, int tabsPerRow) {
        Map<String, Slot> layout = new LinkedHashMap<>();
        Set<Slot> used = new HashSet<>();
        int maxIndex = -1;

        for (String id : ids) {
            Slot slot = saved.get(id);
            if (isValid(slot, tabsPerRow) && used.add(slot)) {
                layout.put(id, slot);
                maxIndex = Math.max(maxIndex, toGlobalIndex(slot, tabsPerRow));
            }
        }

        int next = maxIndex + 1;
        for (String id : ids) {
            if (layout.containsKey(id)) {
                continue;
            }
            Slot slot = fromGlobalIndex(next++, tabsPerRow);
            while (used.contains(slot)) {
                slot = fromGlobalIndex(next++, tabsPerRow);
            }
            used.add(slot);
            layout.put(id, slot);
        }
        return compactRows(compact(layout));
    }

    /**
     * If the first row of a side of a page is completely empty while the second row has tabs, the second row
     * moves up to the first one, so there is never a hole in front of the tabs.
     */
    public static Map<String, Slot> compactRows(Map<String, Slot> layout) {
        Set<String> hasFirstRow = new HashSet<>();
        for (Slot slot : layout.values()) {
            if (slot.row() == 0) {
                hasFirstRow.add(slot.page() + (slot.top() ? "T" : "B"));
            }
        }
        Map<String, Slot> result = new LinkedHashMap<>();
        for (Map.Entry<String, Slot> entry : layout.entrySet()) {
            Slot slot = entry.getValue();
            boolean shift = slot.row() == 1 && !hasFirstRow.contains(slot.page() + (slot.top() ? "T" : "B"));
            result.put(entry.getKey(), shift ? new Slot(slot.page(), slot.top(), 0, slot.col()) : slot);
        }
        return result;
    }

    /** Renumbers the pages so that there is no empty page between two used pages. */
    public static Map<String, Slot> compact(Map<String, Slot> layout) {
        TreeSet<Integer> usedPages = new TreeSet<>();
        for (Slot slot : layout.values()) {
            usedPages.add(slot.page());
        }
        Map<Integer, Integer> newNumber = new HashMap<>();
        int n = 0;
        for (int page : usedPages) {
            newNumber.put(page, n++);
        }
        Map<String, Slot> result = new LinkedHashMap<>();
        for (Map.Entry<String, Slot> entry : layout.entrySet()) {
            result.put(entry.getKey(), entry.getValue().onPage(newNumber.get(entry.getValue().page())));
        }
        return result;
    }

    public static int pageCount(Map<String, Slot> layout) {
        int max = -1;
        for (Slot slot : layout.values()) {
            max = Math.max(max, slot.page());
        }
        return max + 1;
    }

    /** The id sitting in this slot, or null if the slot is empty. */
    public static String idAt(Map<String, Slot> layout, Slot slot) {
        for (Map.Entry<String, Slot> entry : layout.entrySet()) {
            if (entry.getValue().equals(slot)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Moves a tab to a slot. If the slot is empty the tab simply goes there; if another tab is there, the
     * two tabs swap places. Nothing else moves.
     */
    public static Map<String, Slot> move(Map<String, Slot> layout, String id, Slot target) {
        Slot from = layout.get(id);
        if (from == null || from.equals(target)) {
            return layout;
        }
        String occupant = idAt(layout, target);
        Map<String, Slot> result = new LinkedHashMap<>(layout);
        result.put(id, target);
        if (occupant != null) {
            result.put(occupant, from);
        }
        return compactRows(compact(result));
    }

    /** The first free slot after the given one (in the default filling order), possibly on a later page. */
    public static Slot freeSlotAfter(Map<String, Slot> layout, Slot after, int tabsPerRow) {
        Set<Slot> used = new HashSet<>(layout.values());
        int index = toGlobalIndex(after, tabsPerRow) + 1;
        Slot slot = fromGlobalIndex(index, tabsPerRow);
        while (used.contains(slot)) {
            slot = fromGlobalIndex(++index, tabsPerRow);
        }
        return slot;
    }

    // ---------- Merged tabs ----------

    /**
     * Cleans the saved groups (host id -> member ids): unknown tabs are dropped, a tab can only be in one
     * place, and a host that is not installed anymore releases its members.
     */
    public static Map<String, List<String>> cleanGroups(Map<String, List<String>> groups, Set<String> present) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            String host = entry.getKey();
            if (!present.contains(host) || seen.contains(host)) {
                continue;
            }
            List<String> members = new ArrayList<>();
            for (String member : entry.getValue()) {
                if (present.contains(member) && !member.equals(host) && !groups.containsKey(member) && seen.add(member)) {
                    members.add(member);
                }
            }
            if (!members.isEmpty()) {
                seen.add(host);
                result.put(host, members);
            }
        }
        return result;
    }

    public static String hostOf(Map<String, List<String>> groups, String id) {
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            if (entry.getValue().contains(id)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static Set<String> allMembers(Map<String, List<String>> groups) {
        Set<String> members = new HashSet<>();
        for (List<String> list : groups.values()) {
            members.addAll(list);
        }
        return members;
    }

    /**
     * Merges {@code source} into {@code target}. The members of the source (if it was a host) come along.
     * If the target is itself a member, the merge goes to its host.
     */
    public static Map<String, List<String>> merge(Map<String, List<String>> groups, String source, String target) {
        String host = hostOf(groups, target);
        if (host == null) {
            host = target;
        }
        if (host.equals(source) || source.equals(target) || host.equals(hostOf(groups, source))) {
            return groups;
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        List<String> moving = new ArrayList<>();
        moving.add(source);
        List<String> carried = result.remove(source);
        if (carried != null) {
            moving.addAll(carried);
        }
        for (List<String> list : result.values()) {
            list.removeAll(moving);
        }
        result.values().removeIf(List::isEmpty);
        List<String> members = result.computeIfAbsent(host, k -> new ArrayList<>());
        for (String id : moving) {
            if (!members.contains(id)) {
                members.add(id);
            }
        }
        return result;
    }

    /** Moves a member to another position inside its group (the index is clamped). */
    public static Map<String, List<String>> moveMember(Map<String, List<String>> groups, String host,
                                                       String member, int index) {
        List<String> members = groups.get(host);
        if (members == null || !members.contains(member)) {
            return groups;
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        List<String> list = result.get(host);
        list.remove(member);
        list.add(Math.max(0, Math.min(index, list.size())), member);
        return result;
    }

    /** Takes a member out of its group. */
    public static Map<String, List<String>> unmerge(Map<String, List<String>> groups, String member) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            List<String> members = new ArrayList<>(entry.getValue());
            members.remove(member);
            if (!members.isEmpty()) {
                result.put(entry.getKey(), members);
            }
        }
        return result;
    }
}
