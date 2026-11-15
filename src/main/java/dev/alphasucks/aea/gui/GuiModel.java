package dev.alphasucks.aea.gui;

import dev.overgrown.apoli.condition.ItemCondition;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Flattened view of a GUI's elements: menu slots, slot grids, slider ids and what needs server updates. */
public final class GuiModel {
    public record SlotDef(int index, int element, String id, GuiElement.SlotType type, Optional<ItemCondition> itemCondition,
                          Optional<Integer> maxStack, ItemStack displayItem, int localX, int localY, boolean background,
                          int row, int col) {
        public boolean isDisplay() {
            return type == GuiElement.SlotType.DISPLAY;
        }

        public boolean acceptsInsert() {
            return type == GuiElement.SlotType.INPUT || type == GuiElement.SlotType.STORAGE;
        }
    }

    public record Grid(String id, int element, int rows, int cols, int[][] cells, int rowStart, int colStart,
                       int shownRows, int shownCols) {
        public List<Integer> slotIndices() {
            List<Integer> out = new ArrayList<>();
            for (int[] row : cells) {
                for (int idx : row) if (idx >= 0) out.add(idx);
            }
            return out;
        }
    }

    private final List<GuiElement> elements;
    private final List<SlotDef> slots;
    private final Map<String, Integer> slotById;
    private final Map<String, Grid> gridById;
    private final Map<Integer, Grid> gridByElement;
    private final Map<String, Integer> sliderById;
    private final int[] firstSlot;
    private final int[] slotCount;
    private final boolean hasInteractive;
    private final boolean hasProgress;
    private final boolean hasServerEntities;
    private final boolean hasConditions;

    GuiModel(List<GuiElement> elements, boolean showInventory) {
        this.elements = elements;
        List<SlotDef> slots = new ArrayList<>();
        Map<String, Integer> slotById = new LinkedHashMap<>();
        Map<String, Grid> gridById = new LinkedHashMap<>();
        Map<Integer, Grid> gridByElement = new HashMap<>();
        Map<String, Integer> sliderById = new LinkedHashMap<>();
        int[] first = new int[elements.size()];
        int[] count = new int[elements.size()];
        boolean interactive = showInventory;
        boolean progress = false;
        boolean serverEntities = false;
        boolean conditions = false;

        for (int e = 0; e < elements.size(); e++) {
            GuiElement element = elements.get(e);
            first[e] = slots.size();
            conditions |= element.common().condition().isPresent();
            if (element instanceof GuiElement.Slot slot) {
                interactive = true;
                String id = slot.id().orElse("slot_" + e);
                if (slotById.containsKey(id) || gridById.containsKey(id)) throw new IllegalArgumentException("duplicate slot id '" + id + "'");
                SlotDef def = new SlotDef(slots.size(), e, id, slot.slotType(), slot.itemCondition(), slot.maxStack(),
                    slot.item().orElse(null), 1, 1, slot.background(), 0, 0);
                slotById.put(id, def.index());
                slots.add(def);
            } else if (element instanceof GuiElement.SlotGrid grid) {
                interactive = true;
                String id = grid.id().orElse("grid_" + e);
                if (slotById.containsKey(id) || gridById.containsKey(id)) throw new IllegalArgumentException("duplicate slot id '" + id + "'");
                boolean[][] cells = grid.cells();
                int minRow = Integer.MAX_VALUE, maxRow = -1, minCol = Integer.MAX_VALUE, maxCol = -1;
                for (int r = 0; r < grid.rows(); r++) {
                    for (int c = 0; c < grid.columns(); c++) {
                        if (cells[r][c]) {
                            minRow = Math.min(minRow, r);
                            maxRow = Math.max(maxRow, r);
                            minCol = Math.min(minCol, c);
                            maxCol = Math.max(maxCol, c);
                        }
                    }
                }
                int rowStart = 0, colStart = 0, shownRows = grid.rows(), shownCols = grid.columns();
                if (grid.trim() && maxRow >= 0) {
                    rowStart = Math.max(0, minRow - 1);
                    colStart = Math.max(0, minCol - 1);
                    shownRows = Math.min(grid.rows() - 1, maxRow + 1) - rowStart + 1;
                    shownCols = Math.min(grid.columns() - 1, maxCol + 1) - colStart + 1;
                }
                int[][] indices = new int[grid.rows()][grid.columns()];
                int pad = (grid.cellSize() - 16) / 2;
                for (int r = 0; r < grid.rows(); r++) {
                    for (int c = 0; c < grid.columns(); c++) {
                        indices[r][c] = -1;
                        if (!cells[r][c]) continue;
                        String cellId = id + "[" + (r + 1) + "," + (c + 1) + "]";
                        SlotDef def = new SlotDef(slots.size(), e, cellId, grid.slotType(), grid.itemCondition(), grid.maxStack(),
                            grid.item().orElse(null), (c - colStart) * grid.cellSize() + pad, (r - rowStart) * grid.cellSize() + pad,
                            grid.background(), r + 1, c + 1);
                        indices[r][c] = def.index();
                        slotById.put(cellId, def.index());
                        slots.add(def);
                    }
                }
                Grid g = new Grid(id, e, grid.rows(), grid.columns(), indices, rowStart, colStart, shownRows, shownCols);
                gridById.put(id, g);
                gridByElement.put(e, g);
            } else if (element instanceof GuiElement.Slider slider) {
                interactive = true;
                if (sliderById.containsKey(slider.id())) throw new IllegalArgumentException("duplicate slider id '" + slider.id() + "'");
                sliderById.put(slider.id(), e);
            } else if (element instanceof GuiElement.Button) {
                interactive = true;
            } else if (element instanceof GuiElement.Image image) {
                progress |= image.progress().isPresent();
            } else if (element instanceof GuiElement.EntityView view) {
                serverEntities |= view.resolvedOnServer();
            }
            count[e] = slots.size() - first[e];
        }

        this.slots = List.copyOf(slots);
        this.slotById = Collections.unmodifiableMap(slotById);
        this.gridById = Collections.unmodifiableMap(gridById);
        this.gridByElement = Collections.unmodifiableMap(gridByElement);
        this.sliderById = Collections.unmodifiableMap(sliderById);
        this.firstSlot = first;
        this.slotCount = count;
        this.hasInteractive = interactive;
        this.hasProgress = progress;
        this.hasServerEntities = serverEntities;
        this.hasConditions = conditions;
    }

    public List<GuiElement> elements() { return elements; }

    public int elementCount() { return elements.size(); }

    public GuiElement element(int i) { return i >= 0 && i < elements.size() ? elements.get(i) : null; }

    public List<SlotDef> slots() { return slots; }

    public int slotCount() { return slots.size(); }

    public int firstSlotOf(int element) { return firstSlot[element]; }

    public int slotCountOf(int element) { return slotCount[element]; }

    public Grid gridOfElement(int element) { return gridByElement.get(element); }

    public Grid grid(String id) { return gridById.get(id); }

    /** A slot id, a grid cell id ({@code grid[row,col]}) or a whole grid id. */
    public List<Integer> resolveSlots(String id) {
        Integer single = slotById.get(id);
        if (single != null) return List.of(single);
        Grid grid = gridById.get(id);
        return grid == null ? List.of() : grid.slotIndices();
    }

    public int sliderElement(String id) {
        Integer e = sliderById.get(id);
        return e == null ? -1 : e;
    }

    public Map<String, Integer> sliders() { return sliderById; }

    public boolean hasInteractive() { return hasInteractive; }

    public boolean hasProgress() { return hasProgress; }

    public boolean hasServerEntities() { return hasServerEntities; }

    public boolean hasConditions() { return hasConditions; }
}
