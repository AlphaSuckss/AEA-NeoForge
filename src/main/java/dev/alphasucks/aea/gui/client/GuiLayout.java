package dev.alphasucks.aea.gui.client;

import dev.alphasucks.aea.gui.GuiConfig;
import dev.alphasucks.aea.gui.GuiElement;
import dev.alphasucks.aea.gui.GuiModel;

import java.util.function.ToIntFunction;

/** Element boxes of an {@code aea:gui}, relative to the screen's top-left corner. */
final class GuiLayout {
    static final int INVENTORY_WIDTH = 162;
    static final int INVENTORY_HEIGHT = 76;

    int width;
    int height;
    Box[] boxes;
    int inventoryX;
    int inventoryY;

    interface Scales {
        float of(int guiScaleLock);
    }

    static final class Box {
        int x;
        int y;
        int w;
        int h;
        /** Anchor point (scale pivot). */
        float px;
        float py;
        float scale = 1.0F;

        boolean scaled() {
            return scale != 1.0F;
        }

        double ux(double mx) {
            return px + (mx - px) / scale;
        }

        double uy(double my) {
            return py + (my - py) / scale;
        }

        boolean contains(double mx, double my) {
            double x = ux(mx);
            double y = uy(my);
            return x >= this.x && x < this.x + w && y >= this.y && y < this.y + h;
        }
    }

    static GuiLayout compute(GuiConfig config, ToIntFunction<GuiElement> textWidth, Scales scales) {
        GuiModel model = config.model();
        int n = model.elementCount();
        GuiLayout layout = new GuiLayout();
        layout.boxes = new Box[n];
        boolean vanilla = config.showVanillaBackground();
        int fixedW = config.width().orElse(vanilla ? 176 : -1);
        int fixedH = config.height().orElse(vanilla ? 166 : -1);
        int w = Math.max(fixedW, 0);
        int h = Math.max(fixedH, 0);
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            GuiElement e = model.element(i);
            GuiElement.Common c = e.common();
            Box box = new Box();
            int[] size = size(e, model, i, textWidth);
            box.w = size[0];
            box.h = size[1];
            box.scale = c.guiScaleLock().isPresent() ? scales.of(c.guiScaleLock().get()) : 1.0F;
            GuiElement.Anchor a = c.anchor();
            int ax = (int) Math.floor(a.fx * w) + c.x();
            int ay = (int) Math.floor(a.fy * h) + c.y();
            box.px = ax;
            box.py = ay;
            box.x = ax - (int) Math.floor(a.fx * box.w);
            box.y = ay - (int) Math.floor(a.fy * box.h);
            layout.boxes[i] = box;
            float left = box.px - a.fx * box.w * box.scale;
            float top = box.py - a.fy * box.h * box.scale;
            minX = Math.min(minX, left);
            minY = Math.min(minY, top);
            maxX = Math.max(maxX, left + box.w * box.scale);
            maxY = Math.max(maxY, top + box.h * box.scale);
        }
        if (n == 0) minX = minY = maxX = maxY = 0.0F;
        if (config.showInventory()) {
            layout.inventoryX = fixedW >= 0 ? (w - INVENTORY_WIDTH) / 2 + config.inventoryX() : Math.round((minX + maxX) / 2.0F) - 81 + config.inventoryX();
            layout.inventoryY = fixedH >= 0 ? h - 83 + config.inventoryY() : Math.round(maxY) + (n == 0 ? 0 : 4) + config.inventoryY();
            minX = Math.min(minX, layout.inventoryX);
            minY = Math.min(minY, layout.inventoryY);
            maxX = Math.max(maxX, layout.inventoryX + INVENTORY_WIDTH);
            maxY = Math.max(maxY, layout.inventoryY + INVENTORY_HEIGHT);
        }
        int shiftX = fixedW >= 0 ? 0 : -(int) Math.floor(minX);
        int shiftY = fixedH >= 0 ? 0 : -(int) Math.floor(minY);
        layout.width = fixedW >= 0 ? fixedW : Math.max(1, (int) Math.ceil(maxX) + shiftX);
        layout.height = fixedH >= 0 ? fixedH : Math.max(1, (int) Math.ceil(maxY) + shiftY);
        for (Box box : layout.boxes) {
            box.x += shiftX;
            box.y += shiftY;
            box.px += shiftX;
            box.py += shiftY;
        }
        layout.inventoryX += shiftX;
        layout.inventoryY += shiftY;
        return layout;
    }

    private static int[] size(GuiElement e, GuiModel model, int index, ToIntFunction<GuiElement> textWidth) {
        GuiElement.Common c = e.common();
        if (e instanceof GuiElement.Image image) return new int[]{image.width(), image.height()};
        if (e instanceof GuiElement.Text) return new int[]{c.width().orElseGet(() -> textWidth.applyAsInt(e)), c.height().orElse(9)};
        if (e instanceof GuiElement.Button button) return new int[]{button.width(), button.height()};
        if (e instanceof GuiElement.Slider slider) return new int[]{slider.width(), slider.height()};
        if (e instanceof GuiElement.EntityView view) return new int[]{view.width(), view.height()};
        if (e instanceof GuiElement.SlotGrid grid) {
            GuiModel.Grid g = model.gridOfElement(index);
            int cols = g == null ? grid.columns() : g.shownCols();
            int rows = g == null ? grid.rows() : g.shownRows();
            return new int[]{cols * grid.cellSize(), rows * grid.cellSize()};
        }
        return new int[]{18, 18};
    }
}
