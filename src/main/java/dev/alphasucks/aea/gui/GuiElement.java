package dev.alphasucks.aea.gui;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.action.EntityAction;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.codec.LoggedOptionalField;
import dev.overgrown.apoli.condition.EntityCondition;
import dev.overgrown.apoli.condition.ItemCondition;
import dev.overgrown.apoli.data.ColorCodecs;
import dev.overgrown.apoli.data.Expression;
import dev.overgrown.apoli.data.Nbt;
import dev.overgrown.apoli.data.TextComponent;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/** One element of an {@code aea:gui} (AEA 1.5.4 element set: image, text, button, slider, entity, slot, slot_grid). */
public interface GuiElement {
    Common common();

    String typeName();

    Codec<String> TYPE_KEY = Codec.STRING.comapFlatMap(GuiElement::normalize, s -> "aea:" + s);
    Codec<GuiElement> CODEC = TYPE_KEY.partialDispatch("type", e -> DataResult.success(e.typeName()), GuiElement::codecFor);

    private static MapCodec<? extends GuiElement> byName(String name) {
        return switch (name) {
            case "image" -> Image.CODEC;
            case "text" -> Text.CODEC;
            case "button" -> Button.CODEC;
            case "slider" -> Slider.CODEC;
            case "entity" -> EntityView.CODEC;
            case "slot" -> Slot.CODEC;
            case "slot_grid" -> SlotGrid.CODEC;
            default -> null;
        };
    }

    private static DataResult<String> normalize(String raw) {
        String name = raw.startsWith("aea:") ? raw.substring(4) : raw;
        return byName(name) == null
            ? DataResult.error(() -> "Unknown aea:gui element type '" + raw + "' (expected aea:image, aea:text, aea:button, aea:slider, aea:entity, aea:slot or aea:slot_grid)")
            : DataResult.success(name);
    }

    private static DataResult<MapCodec<? extends GuiElement>> codecFor(String name) {
        MapCodec<? extends GuiElement> c = byName(name);
        return c == null ? DataResult.error(() -> "Unknown aea:gui element type '" + name + "'") : DataResult.success(c);
    }

    // ------------------------------------------------------------------ shared fields

    enum Anchor implements StringRepresentable {
        CENTER("center", 0.5F, 0.5F), TOP_LEFT("top_left", 0F, 0F), TOP("top", 0.5F, 0F), TOP_RIGHT("top_right", 1F, 0F),
        LEFT("left", 0F, 0.5F), RIGHT("right", 1F, 0.5F), BOTTOM_LEFT("bottom_left", 0F, 1F), BOTTOM("bottom", 0.5F, 1F),
        BOTTOM_RIGHT("bottom_right", 1F, 1F);

        public static final Codec<Anchor> CODEC = StringRepresentable.fromEnum(Anchor::values);
        private final String name;
        public final float fx;
        public final float fy;

        Anchor(String name, float fx, float fy) {
            this.name = name;
            this.fx = fx;
            this.fy = fy;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    enum ProgressDirection implements StringRepresentable {
        HORIZONTAL("horizontal"), VERTICAL("vertical");

        public static final Codec<ProgressDirection> CODEC = StringRepresentable.fromEnum(ProgressDirection::values);
        private final String name;

        ProgressDirection(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    enum SlotType implements StringRepresentable {
        INPUT("input"), OUTPUT("output"), STORAGE("storage"), DISPLAY("display");

        public static final Codec<SlotType> CODEC = StringRepresentable.fromEnum(SlotType::values);
        private final String name;

        SlotType(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    record Common(int x, int y, Anchor anchor, Optional<Integer> width, Optional<Integer> height,
                  Optional<EntityCondition> condition, Optional<Integer> guiScaleLock) {
        public static final MapCodec<Common> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("x", 0).forGetter(Common::x),
            Codec.INT.optionalFieldOf("y", 0).forGetter(Common::y),
            Anchor.CODEC.optionalFieldOf("anchor", Anchor.CENTER).forGetter(Common::anchor),
            Codec.intRange(0, 4096).optionalFieldOf("width").forGetter(Common::width),
            Codec.intRange(0, 4096).optionalFieldOf("height").forGetter(Common::height),
            LoggedOptionalField.strict("condition", EntityCondition.CODEC).forGetter(Common::condition),
            Codec.intRange(1, 6).optionalFieldOf("gui_scale_lock").forGetter(Common::guiScaleLock)
        ).apply(i, Common::new));
    }

    // ------------------------------------------------------------------ aea:image

    record Image(Common common, ResourceLocation texture, Optional<Integer> textureWidth, Optional<Integer> textureHeight,
                 int u, int v, Optional<Expression> progress, ProgressDirection direction, boolean invert) implements GuiElement {
        static final MapCodec<Image> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Common.MAP_CODEC.forGetter(Image::common),
            ResourceLocation.CODEC.fieldOf("texture_location").forGetter(Image::texture),
            Codec.intRange(1, 8192).optionalFieldOf("texture_width").forGetter(Image::textureWidth),
            Codec.intRange(1, 8192).optionalFieldOf("texture_height").forGetter(Image::textureHeight),
            Codec.INT.optionalFieldOf("u", 0).forGetter(Image::u),
            Codec.INT.optionalFieldOf("v", 0).forGetter(Image::v),
            Expression.DOUBLE_OR_EXPR.optionalFieldOf("progress").forGetter(Image::progress),
            ProgressDirection.CODEC.optionalFieldOf("progress_direction", ProgressDirection.HORIZONTAL).forGetter(Image::direction),
            Codec.BOOL.optionalFieldOf("progress_invert", false).forGetter(Image::invert)
        ).apply(i, Image::new));

        @Override public String typeName() { return "image"; }

        public int width() { return common.width().orElse(textureWidth.orElse(16)); }

        public int height() { return common.height().orElse(textureHeight.orElse(16)); }

        public int texWidth() { return textureWidth.orElse(width()); }

        public int texHeight() { return textureHeight.orElse(height()); }
    }

    // ------------------------------------------------------------------ aea:text

    record Text(Common common, Component text, int color, boolean shadow, boolean centered) implements GuiElement {
        static final MapCodec<Text> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Common.MAP_CODEC.forGetter(Text::common),
            TextComponent.CODEC.fieldOf("text").forGetter(Text::text),
            ColorCodecs.ARGB.optionalFieldOf("color", -1).forGetter(Text::color),
            Codec.BOOL.optionalFieldOf("shadow", true).forGetter(Text::shadow),
            Codec.BOOL.optionalFieldOf("centered", false).forGetter(Text::centered)
        ).apply(i, Text::new));

        @Override public String typeName() { return "text"; }
    }

    // ------------------------------------------------------------------ aea:button

    record Button(Common common, Optional<ResourceLocation> texture, Optional<ResourceLocation> hoverTexture,
                  Optional<ResourceLocation> pressedTexture, Optional<Component> text, int textColor,
                  Optional<EntityAction> onPress, Optional<EntityAction> whilePressed, Optional<EntityAction> onRelease,
                  Optional<EntityCondition> pressCondition, int cooldown) implements GuiElement {
        static final MapCodec<Button> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Common.MAP_CODEC.forGetter(Button::common),
            ResourceLocation.CODEC.optionalFieldOf("texture_location").forGetter(Button::texture),
            ResourceLocation.CODEC.optionalFieldOf("hover_texture_location").forGetter(Button::hoverTexture),
            ResourceLocation.CODEC.optionalFieldOf("pressed_texture_location").forGetter(Button::pressedTexture),
            TextComponent.CODEC.optionalFieldOf("text").forGetter(Button::text),
            ColorCodecs.ARGB.optionalFieldOf("text_color", -1).forGetter(Button::textColor),
            LoggedOptionalField.strict("action_on_press", EntityAction.CODEC).forGetter(Button::onPress),
            LoggedOptionalField.strict("action_while_pressed", EntityAction.CODEC).forGetter(Button::whilePressed),
            LoggedOptionalField.strict("action_on_release", EntityAction.CODEC).forGetter(Button::onRelease),
            LoggedOptionalField.strict("press_condition", EntityCondition.CODEC).forGetter(Button::pressCondition),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("cooldown", 0).forGetter(Button::cooldown)
        ).apply(i, Button::new));

        @Override public String typeName() { return "button"; }

        public int width() { return common.width().orElse(80); }

        public int height() { return common.height().orElse(20); }
    }

    // ------------------------------------------------------------------ aea:slider

    record Slider(Common common, String id, double min, double max, Optional<Double> startValue, double step,
                  Optional<ResourceLocation> backgroundTexture, Optional<ResourceLocation> handleTexture, int handleWidth,
                  Optional<Component> text, int textColor, Optional<EntityAction> onSlide, Optional<EntityAction> onRelease,
                  Optional<EntityCondition> slideCondition) implements GuiElement {
        static final MapCodec<Slider> CODEC = RecordCodecBuilder.<Slider>mapCodec(i -> i.group(
            Common.MAP_CODEC.forGetter(Slider::common),
            Codec.STRING.fieldOf("id").forGetter(Slider::id),
            Codec.DOUBLE.optionalFieldOf("min", 0.0).forGetter(Slider::min),
            Codec.DOUBLE.optionalFieldOf("max", 100.0).forGetter(Slider::max),
            Codec.DOUBLE.optionalFieldOf("start_value").forGetter(Slider::startValue),
            Codec.doubleRange(0.0, Double.MAX_VALUE).optionalFieldOf("step", 1.0).forGetter(Slider::step),
            ResourceLocation.CODEC.optionalFieldOf("background_texture_location").forGetter(Slider::backgroundTexture),
            ResourceLocation.CODEC.optionalFieldOf("slider_texture_location").forGetter(Slider::handleTexture),
            Codec.intRange(1, 4096).optionalFieldOf("slider_width", 8).forGetter(Slider::handleWidth),
            TextComponent.CODEC.optionalFieldOf("text").forGetter(Slider::text),
            ColorCodecs.ARGB.optionalFieldOf("text_color", -1).forGetter(Slider::textColor),
            LoggedOptionalField.strict("action_on_slide", EntityAction.CODEC).forGetter(Slider::onSlide),
            LoggedOptionalField.strict("action_on_release", EntityAction.CODEC).forGetter(Slider::onRelease),
            LoggedOptionalField.strict("slide_condition", EntityCondition.CODEC).forGetter(Slider::slideCondition)
        ).apply(i, Slider::new)).flatXmap(Slider::validate, DataResult::success);

        private static DataResult<Slider> validate(Slider s) {
            if (s.id.isBlank()) return DataResult.error(() -> "An aea:slider needs a non-empty \"id\"");
            if (!(s.max > s.min) || Double.isNaN(s.min) || Double.isInfinite(s.min) || Double.isInfinite(s.max)) {
                return DataResult.error(() -> "aea:slider '" + s.id + "': \"max\" must be greater than \"min\"");
            }
            return DataResult.success(s);
        }

        @Override public String typeName() { return "slider"; }

        public int width() { return common.width().orElse(120); }

        public int height() { return common.height().orElse(20); }

        public double start() { return clamp(startValue.orElse(min)); }

        public double clamp(double value) {
            if (Double.isNaN(value)) return min;
            double v = Math.max(min, Math.min(max, value));
            if (step > 0.0) {
                v = min + (double) Math.round((v - min) / step) * step;
                v = Math.max(min, Math.min(max, v));
                v = (double) Math.round(v * 1.0E9) / 1.0E9;
            }
            return v;
        }
    }

    // ------------------------------------------------------------------ aea:entity

    record Equipment(EquipmentSlot slot, ItemStack item) {
        static final Codec<Equipment> CODEC = RecordCodecBuilder.create(i -> i.group(
            GuiCodecs.EQUIPMENT_SLOT.fieldOf("slot").forGetter(Equipment::slot),
            GuiCodecs.ITEM_STACK.fieldOf("item").forGetter(Equipment::item)
        ).apply(i, Equipment::new));
    }

    record EntityView(Common common, Optional<ResourceLocation> entityType, List<Equipment> equipment, Optional<Nbt> nbt,
                      Optional<String> selector, Optional<ResourceLocation> set, boolean followMouse, float scale,
                      boolean showScale, float fitScale) implements GuiElement {
        static final MapCodec<EntityView> CODEC = RecordCodecBuilder.<EntityView>mapCodec(i -> i.group(
            Common.MAP_CODEC.forGetter(EntityView::common),
            IdCodecs.ID.optionalFieldOf("entity_type").forGetter(EntityView::entityType),
            Equipment.CODEC.listOf().optionalFieldOf("equipment", List.of()).forGetter(EntityView::equipment),
            Nbt.CODEC.optionalFieldOf("nbt").forGetter(EntityView::nbt),
            Codec.STRING.optionalFieldOf("selector").forGetter(EntityView::selector),
            IdCodecs.ID.optionalFieldOf("set").forGetter(EntityView::set),
            Codec.BOOL.optionalFieldOf("follow_mouse", false).forGetter(EntityView::followMouse),
            Codec.floatRange(0.01F, 100.0F).optionalFieldOf("scale", 1.0F).forGetter(EntityView::scale),
            Codec.BOOL.optionalFieldOf("show_scale", false).forGetter(EntityView::showScale),
            Codec.floatRange(0.01F, 100.0F).optionalFieldOf("fit_scale", 1.0F).forGetter(EntityView::fitScale)
        ).apply(i, EntityView::new)).flatXmap(EntityView::validate, DataResult::success);

        private static DataResult<EntityView> validate(EntityView e) {
            int sources = (e.entityType.isPresent() ? 1 : 0) + (e.selector.isPresent() ? 1 : 0) + (e.set.isPresent() ? 1 : 0);
            if (sources != 1) {
                return DataResult.error(() -> "An aea:entity element needs exactly one of \"entity_type\", \"selector\" or \"set\"");
            }
            if (e.entityType.isPresent() && BuiltInRegistries.ENTITY_TYPE.getOptional(e.entityType.get()).isEmpty()) {
                return DataResult.error(() -> "aea:entity: unknown entity type '" + e.entityType.get() + "'");
            }
            if (e.selector.isPresent() && !e.isSelf()) {
                try {
                    new EntitySelectorParser(new StringReader(e.selector.get()), true).parse();
                } catch (CommandSyntaxException ex) {
                    return DataResult.error(() -> "aea:entity: invalid selector '" + e.selector.get() + "': " + ex.getMessage());
                }
            }
            return DataResult.success(e);
        }

        public boolean isSelf() {
            return selector.isPresent() && selector.get().trim().equals("@s");
        }

        public boolean resolvedOnServer() {
            return selector.isPresent() && !isSelf();
        }

        @Override public String typeName() { return "entity"; }

        public int width() { return common.width().orElse(50); }

        public int height() { return common.height().orElse(70); }
    }

    // ------------------------------------------------------------------ aea:slot / aea:slot_grid

    record Slot(Common common, SlotType slotType, Optional<String> id, Optional<ItemCondition> itemCondition,
                Optional<Integer> maxStack, Optional<ItemStack> item, boolean background) implements GuiElement {
        static final MapCodec<Slot> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Common.MAP_CODEC.forGetter(Slot::common),
            SlotType.CODEC.optionalFieldOf("slot_type", SlotType.STORAGE).forGetter(Slot::slotType),
            Codec.STRING.optionalFieldOf("id").forGetter(Slot::id),
            LoggedOptionalField.strict("item_condition", ItemCondition.CODEC).forGetter(Slot::itemCondition),
            Codec.intRange(1, 64).optionalFieldOf("max_stack").forGetter(Slot::maxStack),
            GuiCodecs.ITEM_STACK.optionalFieldOf("item").forGetter(Slot::item),
            Codec.BOOL.optionalFieldOf("background", true).forGetter(Slot::background)
        ).apply(i, Slot::new));

        @Override public String typeName() { return "slot"; }
    }

    record SlotGrid(Common common, int rows, int columns, List<String> visible, boolean trim, int cellSize, SlotType slotType,
                    Optional<String> id, Optional<ItemCondition> itemCondition, Optional<Integer> maxStack,
                    Optional<ItemStack> item, boolean background) implements GuiElement {
        static final MapCodec<SlotGrid> CODEC = RecordCodecBuilder.<SlotGrid>mapCodec(i -> i.group(
            Common.MAP_CODEC.forGetter(SlotGrid::common),
            Codec.intRange(1, 32).fieldOf("rows").forGetter(SlotGrid::rows),
            Codec.intRange(1, 32).fieldOf("columns").forGetter(SlotGrid::columns),
            GuiCodecs.STRING_OR_LIST.optionalFieldOf("visible", List.of()).forGetter(SlotGrid::visible),
            Codec.BOOL.optionalFieldOf("trim", true).forGetter(SlotGrid::trim),
            Codec.intRange(16, 256).optionalFieldOf("cell_size", 18).forGetter(SlotGrid::cellSize),
            SlotType.CODEC.optionalFieldOf("slot_type", SlotType.STORAGE).forGetter(SlotGrid::slotType),
            Codec.STRING.optionalFieldOf("id").forGetter(SlotGrid::id),
            LoggedOptionalField.strict("item_condition", ItemCondition.CODEC).forGetter(SlotGrid::itemCondition),
            Codec.intRange(1, 64).optionalFieldOf("max_stack").forGetter(SlotGrid::maxStack),
            GuiCodecs.ITEM_STACK.optionalFieldOf("item").forGetter(SlotGrid::item),
            Codec.BOOL.optionalFieldOf("background", true).forGetter(SlotGrid::background)
        ).apply(i, SlotGrid::new)).flatXmap(SlotGrid::validate, DataResult::success);

        private static DataResult<SlotGrid> validate(SlotGrid g) {
            for (String cell : g.visible) {
                DataResult<int[]> r = GuiCodecs.parseCellRange(cell, g.rows, g.columns);
                if (r.error().isPresent()) return DataResult.error(() -> r.error().get().message());
            }
            return DataResult.success(g);
        }

        public boolean[][] cells() {
            boolean[][] cells = new boolean[rows][columns];
            if (visible.isEmpty()) {
                for (boolean[] row : cells) java.util.Arrays.fill(row, true);
                return cells;
            }
            for (String cell : visible) {
                int[] r = GuiCodecs.parseCellRange(cell, rows, columns).result().orElse(null);
                if (r == null) continue;
                for (int row = r[0]; row <= r[1]; row++) {
                    for (int col = r[2]; col <= r[3]; col++) cells[row - 1][col - 1] = true;
                }
            }
            return cells;
        }

        @Override public String typeName() { return "slot_grid"; }
    }
}
