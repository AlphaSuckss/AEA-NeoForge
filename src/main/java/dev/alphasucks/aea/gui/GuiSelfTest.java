package dev.alphasucks.aea.gui;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import dev.alphasucks.aea.AEA;
import dev.overgrown.apoli.PowerContainerAttachment;
import dev.overgrown.apoli.action.EntityAction;
import dev.overgrown.apoli.condition.EntityCondition;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.PowerContainer;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Debug-only exercise of the GUI feature on a fake player (only reachable with -Daea.selftest=true). */
public final class GuiSelfTest {
    private GuiSelfTest() {}

    private static final List<String> log = new ArrayList<>();
    private static int pass;
    private static int fail;

    /** A fake player whose openMenu behaves like a real ServerPlayer's (FakePlayer refuses menus). */
    static final class TestPlayer extends FakePlayer {
        RegistryFriendlyByteBuf lastOpenData;
        private int counter;

        TestPlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider, java.util.function.Consumer<RegistryFriendlyByteBuf> extra) {
            if (provider == null) return java.util.OptionalInt.empty();
            if (containerMenu != inventoryMenu) closeContainer();
            counter = counter % 100 + 1;
            net.minecraft.world.inventory.AbstractContainerMenu m = provider.createMenu(counter, getInventory(), this);
            if (m == null) return java.util.OptionalInt.empty();
            lastOpenData = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess());
            if (extra != null) extra.accept(lastOpenData);

            containerMenu = m;
            return java.util.OptionalInt.of(counter);
        }
    }

    private static void check(String what, boolean ok) {
        if (ok) pass++;
        else fail++;
        String line = (ok ? "PASS " : "FAIL ") + what;
        log.add(line);
        AEA.LOGGER.info("[AEA guitest] {}", line);
    }

    private static EntityAction action(ServerLevel level, String json) {
        return EntityAction.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), JsonParser.parseString(json)).getOrThrow();
    }

    private static boolean cond(FakePlayer p, String json) {
        EntityCondition c = EntityCondition.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, p.level().registryAccess()), JsonParser.parseString(json)).getOrThrow();
        return c.test(EntityCtx.of(p, p.level()));
    }

    private static void run(FakePlayer p, String json) {
        action((ServerLevel) p.level(), json).run(EntityCtx.of(p, p.level()));
    }

    private static int score(FakePlayer p, String objective) {
        var sb = p.getServer().getScoreboard();
        var obj = sb.getObjective(objective);
        if (obj == null) return Integer.MIN_VALUE;
        var s = sb.getPlayerScoreInfo(p, obj);
        return s == null ? Integer.MIN_VALUE : s.value();
    }

    /** Runs on the next tick, outside the /aea command, so nested commands execute immediately as in play. */
    public static int schedule(CommandSourceStack src) {
        var server = src.getServer();
        server.tell(new net.minecraft.server.TickTask(server.getTickCount(), () -> {
            try {
                run(src);
            } catch (Throwable t) {
                AEA.LOGGER.error("[AEA guitest] crashed", t);
            }
        }));
        return 1;
    }

    public static int run(CommandSourceStack src) {
        log.clear();
        pass = 0;
        fail = 0;
        ServerLevel level = src.getLevel();
        var server = src.getServer();
        server.getCommands().performPrefixedCommand(src.withSuppressedOutput(), "scoreboard players reset AEA_GuiTest");
        server.getCommands().performPrefixedCommand(src.withSuppressedOutput(), "scoreboard objectives add guitest dummy");
        server.getCommands().performPrefixedCommand(src.withSuppressedOutput(), "scoreboard objectives add guitest_hold dummy");
        server.getCommands().performPrefixedCommand(src.withSuppressedOutput(), "scoreboard objectives add guitest_vol dummy");
        TestPlayer fp = new TestPlayer(level, new GameProfile(UUID.nameUUIDFromBytes("aea_guitest".getBytes()), "AEA_GuiTest"));
        fp.moveTo(src.getPosition());
        for (int i = 0; i < 2; i++) {
            var z = EntityType.ZOMBIE.create(level);
            z.moveTo(src.getPosition().add(i, 0, 2));
            z.setNoAi(true);
            level.addFreshEntity(z);
        }
        PowerContainer c = PowerContainerAttachment.getOrCreate(fp);
        ResourceLocation src0 = AEA.id("guitest");
        ResourceLocation legacy = ResourceLocation.parse("guitest:legacy_gui");
        ResourceLocation bench = ResourceLocation.parse("guitest2:workbench");
        check("legacy gui power loaded", ApoliPowers.get(legacy) != null && ApoliPowers.get(legacy).config() instanceof GuiConfig);
        check("modern gui power loaded", ApoliPowers.get(bench) != null && ApoliPowers.get(bench).config() instanceof GuiConfig);
        check("broken gui rejected (duplicate slot id)", ApoliPowers.get(ResourceLocation.parse("guitest2:broken")) == null);
        var broken2 = ApoliPowers.get(ResourceLocation.parse("guitest2:broken2"));
        check("invalid elements dropped (loader keeps the partial power)", broken2 == null
            || (broken2.config() instanceof GuiConfig bc2 && bc2.elements().isEmpty()));
        if (fail > 0) return report(src);

        GuiConfig lc = (GuiConfig) ApoliPowers.get(legacy).config();
        GuiModel lm = lc.model();
        check("legacy model: 13 elements", lm.elementCount() == 13);
        check("legacy model: slot count 3+9+3", lm.slotCount() == 15);
        // visible (2,2),(3,1),(3,2) of 4x4: trim keeps one cell of margin -> rows 1-4, cols 1-3.
        check("partial grid trimmed (with margin)", lm.grid("partial").shownRows() == 4 && lm.grid("partial").shownCols() == 3
            && lm.grid("partial").rowStart() == 0 && lm.grid("partial").colStart() == 0);
        check("grid cell id resolves", lm.resolveSlots("partial[3,2]").size() == 1 && lm.resolveSlots("partial").size() == 3);
        GuiModel.SlotDef show = lm.slots().get(lm.resolveSlots("show").get(0));
        ItemStack shown = show.displayItem();
        check("legacy item nbt -> damage component", shown != null && shown.getDamageValue() == 5);
        check("legacy item nbt -> custom_name component", shown != null && shown.has(DataComponents.CUSTOM_NAME)
            && shown.get(DataComponents.CUSTOM_NAME).getString().equals("Shown"));
        GuiElement.EntityView zombieView = (GuiElement.EntityView) lm.element(4);
        check("legacy equipment item nbt upgraded", zombieView.equipment().get(0).item().has(DataComponents.CUSTOM_NAME));
        check("legacy entity nbt upgraded (HandItems kept)", zombieView.nbt().isPresent() && zombieView.nbt().get().tag().contains("HandItems"));
        GuiConfig bc = (GuiConfig) ApoliPowers.get(bench).config();
        GuiModel.SlotDef named = bc.model().slots().get(bc.model().resolveSlots("named").get(0));
        check("1.21 components item spec", named.displayItem().getCount() == 3 && named.displayItem().has(DataComponents.CUSTOM_NAME));
        GuiModel.SlotDef tagged = bc.model().slots().get(bc.model().resolveSlots("tagged").get(0));
        check("1.20.1 'tag' alias on a 1.21 pack runs through the data fixer", tagged.displayItem().getDamageValue() == 3
            && tagged.displayItem().has(DataComponents.CUSTOM_NAME));
        check("title text fix (bold string)", lc.title().isPresent() && lc.title().get().getStyle().isBold());

        // Not held: cannot open.
        check("open without power fails", !AeaGui.open(fp, legacy));
        c.addPower(legacy, src0);
        c.addPower(ResourceLocation.parse("guitest:opener"), src0);
        c.addPower(ResourceLocation.parse("guitest:watch"), src0);
        c.addPower(bench, src0);
        check("gui_open false before open", !cond(fp, "{\"type\":\"aea:gui_open\"}"));
        check("gui_value falls back to start value", cond(fp, "{\"type\":\"aea:gui_value\",\"gui\":\"guitest:legacy\",\"slider\":\"vol\",\"comparison\":\"==\",\"compare_to\":5}"));

        // Open through the key power's action.
        run(fp, "{\"type\":\"aea:open_gui\",\"power\":\"guitest:legacy_gui\"}");
        check("open_gui opens the menu", fp.containerMenu instanceof GuiMenu);
        if (!(fp.containerMenu instanceof GuiMenu menu)) return report(src);
        GuiSession session = menu.session();
        check("server session present", session != null && !menu.broken);
        check("menu slots = gui + inventory", menu.slots.size() == 15 + 36);
        check("gui_open true", cond(fp, "{\"type\":\"aea:gui_open\",\"gui\":\"guitest:legacy\"}"));
        check("gui_open other id false", !cond(fp, "{\"type\":\"aea:gui_open\",\"gui\":\"guitest:nope\"}"));
        check("sneak-only text hidden", !menu.isVisible(12));
        check("display slot filled", menu.container.getItem(show.index()).is(Items.DIAMOND_SWORD));
        check("progress evaluated", Math.abs(menu.progress[0] - 0.5F) < 1e-4);
        check("selector entity ids resolved", menu.entityIds[5].length >= 1);

        // Open data round trip -> client menu.
        RegistryFriendlyByteBuf buf = fp.lastOpenData;
        check("open data written by openMenu", buf != null && buf.readableBytes() > 0);
        GuiMenu client = GuiMenu.createClient(menu.containerId + 1000, fp.getInventory(), buf);
        check("client menu builds, not broken", !client.broken && client.slots.size() == menu.slots.size());
        check("client menu got slider state", client.sliders[3] == 5.0);
        check("client menu got entity ids", client.entityIds[5].length == menu.entityIds[5].length);

        // Slider.
        session.moveSlider(3, 7.4, false);
        session.tick();
        check("slider clamped to step", menu.sliders[3] == 7.0);
        check("action_on_slide with {value}", score(fp, "guitest_vol") == 7);
        session.moveSlider(3, 99, true);
        session.tick();
        check("slider max + release action", menu.sliders[3] == 10.0 && fp.getTags().contains("guitest_slid"));
        check("gui_value reads open slider", cond(fp, "{\"type\":\"aea:gui_value\",\"slider\":\"vol\",\"comparison\":\"==\",\"compare_to\":10}"));

        // Button.
        session.pressButton(2);
        check("button press runs gui_command with {slider:vol}", score(fp, "guitest") == 10);
        for (int i = 0; i < 3; i++) session.tick();
        check("action_while_pressed ran each tick", score(fp, "guitest_hold") >= 3);
        session.releaseButton(2);
        check("action_on_release ran", fp.getTags().contains("guitest_released"));
        run(fp, "{\"type\":\"aea:gui_command\",\"command\":\"scoreboard players set @s guitest 0\"}");
        session.pressButton(2);
        check("cooldown blocks immediate re-press", score(fp, "guitest") == 0);
        session.releaseButton(2);

        // Slots: item condition, modify_gui_slot, slot conditions.
        GuiSlot in = menu.resolveSlots("in").get(0);
        check("input slot rejects wrong item", !in.mayPlace(new ItemStack(Items.DIRT)));
        check("input slot accepts raw_iron", in.mayPlace(new ItemStack(Items.RAW_IRON)));
        check("display slot not pickable", !menu.resolveSlots("show").get(0).mayPickup(fp));
        run(fp, "{\"type\":\"aea:modify_gui_slot\",\"slot\":\"partial\",\"operation\":\"add\",\"item\":\"minecraft:apple\",\"count\":40}");
        int apples = 0;
        for (GuiSlot s : menu.resolveSlots("partial")) apples += s.getItem().getCount();
        check("modify_gui_slot add respects max_stack 16 (40 fits in 3x16)", apples == 40);
        run(fp, "{\"type\":\"aea:modify_gui_slot\",\"slot\":\"partial\",\"operation\":\"add\",\"item\":\"minecraft:apple\",\"count\":9}");
        apples = 0;
        for (GuiSlot s : menu.resolveSlots("partial")) apples += s.getItem().getCount();
        check("modify_gui_slot add all-or-nothing", apples == 40);
        check("gui_slot comparison count", cond(fp, "{\"type\":\"aea:gui_slot\",\"slot\":\"partial\",\"comparison\":\"==\",\"compare_to\":40}"));
        run(fp, "{\"type\":\"aea:modify_gui_slot\",\"slot\":\"partial\",\"operation\":\"consume\",\"item\":\"minecraft:apple\",\"count\":25}");
        apples = 0;
        for (GuiSlot s : menu.resolveSlots("partial")) apples += s.getItem().getCount();
        check("modify_gui_slot consume", apples == 15);
        run(fp, "{\"type\":\"aea:modify_gui_slot\",\"slot\":\"in\",\"operation\":\"set\",\"item\":{\"item\":\"minecraft:raw_iron\",\"count\":4}}");
        check("modify_gui_slot set", in.getItem().is(Items.RAW_IRON) && in.getItem().getCount() == 4);
        check("gui_slot item_condition", cond(fp, "{\"type\":\"aea:gui_slot\",\"slot\":\"in\",\"item_condition\":{\"type\":\"apoli:ingredient\",\"ingredient\":{\"item\":\"minecraft:raw_iron\"}}}"));
        run(fp, "{\"type\":\"aea:gui_slot_action\",\"slot\":\"in\",\"item_action\":{\"type\":\"apoli:consume\",\"amount\":1}}");
        check("gui_slot_action runs item action on the slot", in.getItem().getCount() == 3);
        run(fp, "{\"type\":\"aea:gui_craft\",\"input\":\"in\",\"output\":\"out\",\"recipe_type\":\"smelting\"}");
        check("gui_craft smelting raw_iron -> iron_ingot", menu.resolveSlots("out").get(0).getItem().is(Items.IRON_INGOT) && in.getItem().getCount() == 2);
        // 3x3 grid shaped craft (planks in the bottom-right 2x2 -> crafting table: tests 1.21 input trimming).
        List<GuiSlot> craft = menu.resolveSlots("craft");
        GuiSlot out = menu.resolveSlots("out").get(0);
        run(fp, "{\"type\":\"aea:modify_gui_slot\",\"slot\":\"out\",\"operation\":\"clear\"}");
        check("modify_gui_slot clear", out.getItem().isEmpty());
        craft.get(4).set(new ItemStack(Items.OAK_PLANKS));
        craft.get(5).set(new ItemStack(Items.OAK_PLANKS));
        craft.get(7).set(new ItemStack(Items.OAK_PLANKS));
        craft.get(8).set(new ItemStack(Items.OAK_PLANKS));
        run(fp, "{\"type\":\"aea:gui_craft\",\"input\":\"craft\",\"output\":\"out\"}");
        check("gui_craft crafting from a grid (trimmed 2x2)", out.getItem().is(Items.CRAFTING_TABLE));
        check("gui_craft consumed grid ingredients", craft.get(4).getItem().isEmpty() && craft.get(8).getItem().isEmpty());
        run(fp, "{\"type\":\"aea:modify_gui_slot\",\"slot\":\"out\",\"operation\":\"clear\"}");
        // Remainder lands back in the slot it came from: honey bottle in cell (3,3) -> sugar + glass bottle in (3,3).
        craft.get(8).set(new ItemStack(Items.HONEY_BOTTLE));
        run(fp, "{\"type\":\"aea:gui_craft\",\"input\":\"craft\",\"output\":\"out\"}");
        check("gui_craft shapeless honey -> sugar", out.getItem().is(Items.SUGAR) && out.getItem().getCount() == 3);
        check("gui_craft remainder mapped back to its cell", craft.get(8).getItem().is(Items.GLASS_BOTTLE));
        run(fp, "{\"type\":\"aea:modify_gui_slot\",\"slot\":\"craft\",\"operation\":\"clear\"}");

        // Visibility condition flips on sneak.
        fp.setShiftKeyDown(true);
        for (int i = 0; i < 5; i++) session.tick();
        check("condition element shown while sneaking", menu.isVisible(12));
        session.pressButton(2);
        check("press_condition blocks press while sneaking", score(fp, "guitest") == 0);
        fp.setShiftKeyDown(false);

        // State payload round trip.
        GuiNetwork.Builder b = new GuiNetwork.Builder();
        b.visible(menu.visible).progress(0, 0.25F).slider(3, 4.0).entities(5, new int[]{1, 2, 3});
        RegistryFriendlyByteBuf sbuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        GuiNetwork.StatePayload.CODEC.encode(sbuf, new GuiNetwork.StatePayload(7, b.build()));
        GuiNetwork.StatePayload back = GuiNetwork.StatePayload.CODEC.decode(sbuf);
        back.state().applyTo(client);
        check("state payload round trip", back.syncId() == 7 && client.progress[0] == 0.25F && client.sliders[3] == 4.0 && client.entityIds[5].length == 3);

        // Close: memory saves slots + slider; reopening restores them.
        fp.closeContainer();
        check("menu closed", !(fp.containerMenu instanceof GuiMenu) && menu.isClosed());
        check("gui_open false after close", !cond(fp, "{\"type\":\"aea:gui_open\"}"));
        check("memory saved", GuiMemory.get(fp, ResourceLocation.parse("guitest:legacy")) != null);
        check("gui_value reads remembered slider", cond(fp, "{\"type\":\"aea:gui_value\",\"gui\":\"guitest:legacy\",\"slider\":\"vol\",\"comparison\":\"==\",\"compare_to\":10}"));
        check("items stay in memory, not returned", fp.getInventory().countItem(Items.RAW_IRON) == 0);
        AeaGui.open(fp, legacy);
        GuiMenu again = fp.containerMenu instanceof GuiMenu m ? m : null;
        check("reopen restores slots", again != null && again.resolveSlots("in").get(0).getItem().getCount() == 2);
        check("reopen restores slider", again != null && again.sliders[3] == 10.0);
        int apples2 = 0;
        if (again != null) for (GuiSlot s : again.resolveSlots("partial")) apples2 += s.getItem().getCount();
        check("reopen restores grid", apples2 == 15);
        run(fp, "{\"type\":\"aea:close_gui\",\"gui\":\"guitest:other\"}");
        check("close_gui with other id keeps it open", fp.containerMenu instanceof GuiMenu);
        run(fp, "{\"type\":\"aea:close_gui\",\"gui\":\"guitest:legacy\"}");
        check("close_gui closes", !(fp.containerMenu instanceof GuiMenu));

        // Non-memory GUI returns items on close; crafting with explicit recipe ids.
        AeaGui.open(fp, bench);
        GuiMenu wb = fp.containerMenu instanceof GuiMenu m2 ? m2 : null;
        check("workbench opens (no inventory)", wb != null && wb.slots.size() == wb.model.slotCount() && wb.playerSlotStart < 0);
        if (wb != null) {
            wb.resolveSlots("grid").get(0).set(new ItemStack(Items.OAK_LOG));
            wb.resolveSlots("furnace_in").get(0).set(new ItemStack(Items.RAW_GOLD, 2));
            wb.resolveSlots("cut_in").get(0).set(new ItemStack(Items.STONE, 3));
            wb.session().pressButton(7);
            check("craft button: log -> 4 planks", wb.resolveSlots("result").get(0).getItem().is(Items.OAK_PLANKS)
                && wb.resolveSlots("result").get(0).getItem().getCount() == 4);
            check("craft button: smelting raw_gold", wb.resolveSlots("furnace_out").get(0).getItem().is(Items.GOLD_INGOT));
            check("craft button: stonecutting by recipe id", wb.resolveSlots("cut_out").get(0).getItem().is(Items.STONE_SLAB)
                && wb.resolveSlots("cut_out").get(0).getItem().getCount() == 2);
            wb.session().releaseButton(7);
            wb.session().moveSlider(8, 0.123456, true);
            wb.session().tick();
            check("step 0 slider keeps raw value", wb.sliders[8] == 0.123456);
            fp.closeContainer();
            check("non-memory GUI returns items", fp.getInventory().countItem(Items.RAW_GOLD) == 1 && fp.getInventory().countItem(Items.OAK_PLANKS) == 4
                && fp.getInventory().countItem(Items.STICK) == 0);
        }

        // Power removed while open closes the GUI on the next refresh.
        AeaGui.open(fp, legacy);
        GuiMenu third = fp.containerMenu instanceof GuiMenu m3 ? m3 : null;
        c.removePower(legacy, src0);
        if (third != null) for (int i = 0; i < 6; i++) if (third.session() != null) third.session().tick();
        check("losing the power closes the GUI", !(fp.containerMenu instanceof GuiMenu));
        // Death copies memory (copyOnDeath) is an attachment flag; logout closes via event.
        c.removeAllFromSource(src0);
        return report(src);
    }

    private static int report(CommandSourceStack src) {
        int p = pass, f = fail;
        src.sendSuccess(() -> Component.literal("[AEA guitest] " + p + " passed, " + f + " failed"), false);
        AEA.LOGGER.info("[AEA guitest] {} passed, {} failed", p, f);
        return f == 0 ? 1 : 0;
    }
}
