package dev.alphasucks.aea.gui.action.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.gui.GuiCodecs;
import dev.alphasucks.aea.gui.GuiMenu;
import dev.alphasucks.aea.gui.GuiModel;
import dev.alphasucks.aea.gui.GuiPowers;
import dev.alphasucks.aea.gui.GuiSlot;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.condition.context.EntityCtx;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code aea:gui_craft}: crafts one result from GUI input slots into an output slot, like a crafting table, furnace,
 * stonecutter or smithing table would. Ingredients are used up, remainders (buckets, bottles) go back.
 */
public final class GuiCraftAction implements ActionType<EntityCtx, GuiCraftAction.Cfg> {
    public enum Kind implements StringRepresentable {
        CRAFTING("crafting", RecipeType.CRAFTING), SMELTING("smelting", RecipeType.SMELTING), BLASTING("blasting", RecipeType.BLASTING),
        SMOKING("smoking", RecipeType.SMOKING), CAMPFIRE_COOKING("campfire_cooking", RecipeType.CAMPFIRE_COOKING),
        STONECUTTING("stonecutting", RecipeType.STONECUTTING), SMITHING("smithing", RecipeType.SMITHING);

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        private final String name;
        private final RecipeType<?> type;

        Kind(String name, RecipeType<?> type) {
            this.name = name;
            this.type = type;
        }

        public RecipeType<?> type() {
            return type;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public record Cfg(List<String> input, String output, Kind kind, Optional<ResourceLocation> recipe) {}

    /** The recipe input plus, per input position, the GUI slot it was read from (null: an empty grid cell). */
    record Input(RecipeInput inventory, GuiSlot[] sources, int width, int left, int top, int trimmedWidth) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            GuiCodecs.STRING_OR_LIST.fieldOf("input").forGetter(Cfg::input),
            Codec.STRING.fieldOf("output").forGetter(Cfg::output),
            Kind.CODEC.optionalFieldOf("recipe_type", Kind.CRAFTING).forGetter(Cfg::kind),
            IdCodecs.ID.optionalFieldOf("recipe").forGetter(Cfg::recipe)
        ).apply(i, Cfg::new));
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void run(Cfg cfg, EntityCtx ctx) {
        GuiMenu menu = GuiPowers.serverGui(ctx.entity());
        if (menu == null) return;
        Player player = (Player) ctx.entity();
        Level level = player.level();
        List<GuiSlot> outputs = menu.resolveSlots(cfg.output());
        if (outputs.isEmpty()) return;
        GuiSlot output = outputs.get(0);
        Input input = buildInput(menu, cfg);
        if (input == null) return;
        Recipe recipe = find(level, cfg.kind().type(), input.inventory(), cfg.recipe());
        if (recipe == null) return;
        ItemStack result = recipe.assemble(input.inventory(), level.registryAccess());
        if (result.isEmpty() || !fits(output, result)) return;
        List<ItemStack> remainders = remainders(cfg.kind(), recipe, input);
        for (int i = 0; i < input.sources().length; i++) {
            GuiSlot source = input.sources()[i];
            if (source == null || source.getItem().isEmpty()) continue;
            source.remove(1);
            ItemStack rem = i < remainders.size() ? remainders.get(i) : ItemStack.EMPTY;
            if (rem.isEmpty()) continue;
            ItemStack now = source.getItem();
            if (now.isEmpty()) {
                source.set(rem.copy());
            } else if (ItemStack.isSameItemSameComponents(now, rem) && now.getCount() + rem.getCount() <= ModifyGuiSlotAction.capacity(source, now)) {
                now.grow(rem.getCount());
                source.setChanged();
            } else {
                player.getInventory().placeItemBackInInventory(rem.copy());
            }
        }
        result.onCraftedBy(level, player, result.getCount());
        ItemStack out = output.getItem();
        if (out.isEmpty()) {
            output.set(result);
        } else {
            out.grow(result.getCount());
            output.setChanged();
        }
    }

    private static boolean fits(GuiSlot slot, ItemStack stack) {
        ItemStack in = slot.getItem();
        int cap = ModifyGuiSlotAction.capacity(slot, stack);
        if (in.isEmpty()) return stack.getCount() <= cap;
        return ItemStack.isSameItemSameComponents(in, stack) && in.getCount() + stack.getCount() <= cap;
    }

    @Nullable
    private static Input buildInput(GuiMenu menu, Cfg cfg) {
        if (cfg.kind() == Kind.CRAFTING) {
            GuiModel.Grid grid = cfg.input().size() == 1 ? menu.model.grid(cfg.input().get(0)) : null;
            if (grid != null) return craftingFromGrid(menu, grid);
            List<GuiSlot> flat = flatten(menu, cfg.input());
            int count = flat.size();
            int side = count <= 1 ? 1 : count <= 4 ? 2 : count <= 9 ? 3 : -1;
            if (side < 0 || count == 0) return null;
            GuiSlot[] sources = new GuiSlot[side * side];
            List<ItemStack> items = NonNullList.withSize(side * side, ItemStack.EMPTY);
            for (int i = 0; i < count; i++) {
                sources[i] = flat.get(i);
                items.set(i, flat.get(i).getItem().copy());
            }
            return crafting(side, side, items, sources);
        }
        List<GuiSlot> flat = flatten(menu, cfg.input());
        if (cfg.kind() == Kind.SMITHING) {
            if (flat.size() < 3) return null;
            GuiSlot[] sources = {flat.get(0), flat.get(1), flat.get(2)};
            return new Input(new SmithingRecipeInput(sources[0].getItem().copy(), sources[1].getItem().copy(), sources[2].getItem().copy()),
                sources, 3, 0, 0, 3);
        }
        for (GuiSlot slot : flat) {
            if (!slot.getItem().isEmpty()) {
                return new Input(new SingleRecipeInput(slot.getItem().copyWithCount(1)), new GuiSlot[]{slot}, 1, 0, 0, 1);
            }
        }
        return null;
    }

    @Nullable
    private static Input craftingFromGrid(GuiMenu menu, GuiModel.Grid grid) {
        int minRow = Integer.MAX_VALUE, maxRow = -1, minCol = Integer.MAX_VALUE, maxCol = -1;
        for (int r = 0; r < grid.rows(); r++) {
            for (int c = 0; c < grid.cols(); c++) {
                GuiSlot s = menu.guiSlot(grid.cells()[r][c]);
                if (s != null && !s.getItem().isEmpty()) {
                    minRow = Math.min(minRow, r);
                    maxRow = Math.max(maxRow, r);
                    minCol = Math.min(minCol, c);
                    maxCol = Math.max(maxCol, c);
                }
            }
        }
        if (maxRow < 0) return null;
        int height = maxRow - minRow + 1;
        int width = maxCol - minCol + 1;
        if (width > 3 || height > 3) return null;
        GuiSlot[] sources = new GuiSlot[width * height];
        List<ItemStack> items = NonNullList.withSize(width * height, ItemStack.EMPTY);
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                GuiSlot s = menu.guiSlot(grid.cells()[minRow + r][minCol + c]);
                if (s == null) continue;
                sources[r * width + c] = s;
                items.set(r * width + c, s.getItem().copy());
            }
        }
        return crafting(width, height, items, sources);
    }

    private static Input crafting(int width, int height, List<ItemStack> items, GuiSlot[] sources) {
        CraftingInput.Positioned positioned = CraftingInput.ofPositioned(width, height, items);
        return new Input(positioned.input(), sources, width, positioned.left(), positioned.top(), positioned.input().width());
    }

    private static List<GuiSlot> flatten(GuiMenu menu, List<String> ids) {
        List<GuiSlot> out = new ArrayList<>();
        for (String id : ids) out.addAll(menu.resolveSlots(id));
        return out;
    }

    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Recipe find(Level level, RecipeType type, RecipeInput input, Optional<ResourceLocation> id) {
        RecipeManager manager = level.getRecipeManager();
        try {
            if (id.isPresent()) {
                Optional<RecipeHolder<?>> holder = manager.byKey(id.get());
                if (holder.isPresent() && holder.get().value().getType() == type && ((Recipe) holder.get().value()).matches(input, level)) {
                    return holder.get().value();
                }
                return null;
            }
            Optional<RecipeHolder> found = (Optional) manager.getRecipeFor(type, input, level);
            return found.map(RecipeHolder::value).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Remainders aligned with {@link Input#sources()}. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<ItemStack> remainders(Kind kind, Recipe recipe, Input input) {
        if (kind == Kind.CRAFTING) {
            // 1.21 crafting inputs are trimmed to the items' bounding box: map the remainders back to slot positions.
            NonNullList<ItemStack> trimmed = recipe.getRemainingItems(input.inventory());
            List<ItemStack> out = new ArrayList<>(input.sources().length);
            for (int i = 0; i < input.sources().length; i++) out.add(ItemStack.EMPTY);
            int tw = Math.max(1, input.trimmedWidth());
            for (int t = 0; t < trimmed.size(); t++) {
                int idx = (input.top() + t / tw) * input.width() + input.left() + t % tw;
                if (idx >= 0 && idx < out.size()) out.set(idx, trimmed.get(t));
            }
            return out;
        }
        List<ItemStack> out = new ArrayList<>();
        for (GuiSlot s : input.sources()) {
            out.add(s == null || s.getItem().isEmpty() ? ItemStack.EMPTY : s.getItem().getCraftingRemainingItem());
        }
        return out;
    }
}
