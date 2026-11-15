package dev.alphasucks.aea.gui;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.AEA;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.data.TextComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** Config of an {@code aea:gui} power (AEA 1.5.4 fields and defaults). */
public final class GuiConfig {
    public static final MapCodec<GuiConfig> CODEC = RecordCodecBuilder.<GuiConfig>mapCodec(i -> i.group(
        IdCodecs.ID.optionalFieldOf("id").forGetter(GuiConfig::id),
        TextComponent.CODEC.optionalFieldOf("title").forGetter(GuiConfig::title),
        Codec.BOOL.optionalFieldOf("show_inventory", false).forGetter(GuiConfig::showInventory),
        Codec.INT.optionalFieldOf("inventory_x", 0).forGetter(GuiConfig::inventoryX),
        Codec.INT.optionalFieldOf("inventory_y", 0).forGetter(GuiConfig::inventoryY),
        Codec.BOOL.optionalFieldOf("show_vanilla_background", true).forGetter(GuiConfig::showVanillaBackground),
        Codec.intRange(1, 4096).optionalFieldOf("width").forGetter(GuiConfig::width),
        Codec.intRange(1, 4096).optionalFieldOf("height").forGetter(GuiConfig::height),
        Codec.BOOL.optionalFieldOf("memory", false).forGetter(GuiConfig::memory),
        Codec.BOOL.optionalFieldOf("pause_game", false).forGetter(GuiConfig::pauseGame),
        GuiElement.CODEC.listOf().optionalFieldOf("elements", List.of()).forGetter(GuiConfig::elements)
    ).apply(i, GuiConfig::new)).flatXmap(GuiConfig::validate, DataResult::success);

    private final Optional<ResourceLocation> id;
    private final Optional<Component> title;
    private final boolean showInventory;
    private final int inventoryX;
    private final int inventoryY;
    private final boolean showVanillaBackground;
    private final Optional<Integer> width;
    private final Optional<Integer> height;
    private final boolean memory;
    private final boolean pauseGame;
    private final List<GuiElement> elements;
    private final GuiModel model;
    private final String error;

    public GuiConfig(Optional<ResourceLocation> id, Optional<Component> title, boolean showInventory, int inventoryX, int inventoryY,
                     boolean showVanillaBackground, Optional<Integer> width, Optional<Integer> height, boolean memory,
                     boolean pauseGame, List<GuiElement> elements) {
        this.id = id;
        this.title = title;
        this.showInventory = showInventory;
        this.inventoryX = inventoryX;
        this.inventoryY = inventoryY;
        this.showVanillaBackground = showVanillaBackground;
        this.width = width;
        this.height = height;
        this.memory = memory;
        this.pauseGame = pauseGame;
        this.elements = List.copyOf(elements);
        GuiModel m = null;
        String err = null;
        try {
            m = new GuiModel(this.elements, showInventory);
        } catch (IllegalArgumentException e) {
            err = e.getMessage();
        }
        this.model = m;
        this.error = err;
    }

    private static DataResult<GuiConfig> validate(GuiConfig c) {
        if (c.error != null) return DataResult.error(() -> "Invalid aea:gui: " + c.error);
        if (c.pauseGame && c.model.hasInteractive()) {
            AEA.LOGGER.warn("[AEA] An aea:gui has \"pause_game\": true but contains slots, buttons, sliders or the player inventory; it will NOT pause the game (a paused singleplayer server would stop handling their clicks).");
        }
        return DataResult.success(c);
    }

    public Optional<ResourceLocation> id() { return id; }

    public Optional<Component> title() { return title; }

    public boolean showInventory() { return showInventory; }

    public int inventoryX() { return inventoryX; }

    public int inventoryY() { return inventoryY; }

    public boolean showVanillaBackground() { return showVanillaBackground; }

    public Optional<Integer> width() { return width; }

    public Optional<Integer> height() { return height; }

    public boolean memory() { return memory; }

    public boolean pauseGame() { return pauseGame; }

    public boolean pausesGame() { return pauseGame && !model.hasInteractive(); }

    public List<GuiElement> elements() { return elements; }

    public GuiModel model() { return model; }

    /** The GUI's id: {@code id} if set, otherwise the power's id. */
    public ResourceLocation guiId(ResourceLocation powerId) { return id.orElse(powerId); }
}
