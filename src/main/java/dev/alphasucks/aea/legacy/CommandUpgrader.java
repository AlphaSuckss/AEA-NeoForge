package dev.alphasucks.aea.legacy;

import dev.alphasucks.aea.AEA;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Rewrites 1.20.1-and-older command syntax to 1.21.1: item NBT → item components, legacy particle
 * parameters, entity NBT (items, effects, attributes, Cardinal Components powers), NBT paths into item tags,
 * {@code replaceitem}. Anything it does not recognise is left untouched.
 */
public final class CommandUpgrader {
    private CommandUpgrader() {}

    public static String upgradeFunction(String text, int dataVersion) {
        StringBuilder out = new StringBuilder(text.length() + 64);
        String[] raw = text.split("\r?\n", -1);
        // Join "\" line continuations (1.20.2+ syntax, backported to 1.20.1 by AEA's function macros) so the whole command is upgraded at once.
        java.util.List<String> lines = new java.util.ArrayList<>(raw.length);
        for (int n = 0; n < raw.length; n++) {
            String line = raw[n];
            String t = line.stripLeading();
            if (!t.startsWith("#") && line.endsWith("\\")) {
                StringBuilder joined = new StringBuilder(line.substring(0, line.length() - 1));
                while (n + 1 < raw.length) {
                    String next = raw[++n].stripLeading();
                    if (next.endsWith("\\")) joined.append(next, 0, next.length() - 1);
                    else { joined.append(next); break; }
                }
                line = joined.toString();
            }
            lines.add(line);
        }
        for (int n = 0; n < lines.size(); n++) {
            String line = lines.get(n);
            String trimmed = line.stripLeading();
            String indent = line.substring(0, line.length() - trimmed.length());
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                out.append(line);
            } else if (trimmed.startsWith("$")) {
                // Macro line: upgrade the template; anything containing $(...) that can't be parsed is left alone.
                String body = trimmed.substring(1);
                String up = upgrade(body, dataVersion);
                if (!macroVars(up).equals(macroVars(body))) up = body;
                out.append(indent).append('$').append(up);
            } else {
                out.append(indent).append(upgrade(trimmed, dataVersion));
            }
            if (n < lines.size() - 1) out.append('\n');
        }
        return out.toString();
    }

    private static final java.util.regex.Pattern MACRO_VAR = java.util.regex.Pattern.compile("\\$\\(([A-Za-z0-9_]+)\\)");

    private static java.util.List<String> macroVars(String s) {
        java.util.List<String> vars = new java.util.ArrayList<>();
        java.util.regex.Matcher m = MACRO_VAR.matcher(s);
        while (m.find()) vars.add(m.group(1));
        return vars;
    }

    public static String upgrade(String command, int dv) {
        if (command == null || command.isBlank()) return command;
        try {
            // Fabric v1 conventional tags (#c:iron_ingots → #c:ingots/iron).
            command = ConventionalTags.inCommand(command);
            command = ApoliCommandUpgrader.upgrade(command);
            boolean slash = command.startsWith("/");
            String body = slash ? command.substring(1) : command;
            List<String> t = split(body);
            if (t.isEmpty()) return command;
            List<String> up = upgradeTokens(t, 0, dv);
            if (up.equals(t)) return command;
            String result = String.join(" ", up);
            return slash ? "/" + result : result;
        } catch (Throwable e) {
            AEA.LOGGER.debug("[AEA] Could not upgrade command '{}': {}", command, e.toString());
            return command;
        }
    }

    // ---------------------------------------------------------------- tokenizing

    static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        boolean inStr = false;
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                cur.append(c);
                if (c == '\\' && i + 1 < s.length()) { cur.append(s.charAt(++i)); continue; }
                if (c == quote) inStr = false;
                continue;
            }
            if (c == '"' || c == '\'') {
                // A quote only opens a string inside brackets or at token start (e.g. "text").
                inStr = true; quote = c; cur.append(c); continue;
            }
            if (c == '{' || c == '[' || c == '(') depth++;
            if (c == '}' || c == ']' || c == ')') depth = Math.max(0, depth - 1);
            if (c == ' ' && depth == 0) {
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
                continue;
            }
            cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static String root(String s) {
        s = s.toLowerCase(Locale.ROOT);
        return s.startsWith("minecraft:") ? s.substring(10) : s;
    }

    // ---------------------------------------------------------------- dispatch

    private static List<String> upgradeTokens(List<String> t, int start, int dv) {
        List<String> out = new ArrayList<>(t.subList(0, start));
        List<String> cmd = new ArrayList<>(t.subList(start, t.size()));
        for (int i = 0; i < cmd.size(); i++) cmd.set(i, fixSelector(cmd.get(i), dv));
        String r = root(cmd.get(0));
        switch (r) {
            case "execute" -> { out.addAll(execute(cmd, dv)); return out; }
            case "give" -> { if (cmd.size() > 2) cmd.set(2, fixItemArg(cmd.get(2), false, dv)); }
            case "clear" -> { if (cmd.size() > 2) cmd.set(2, fixItemArg(cmd.get(2), true, dv)); }
            case "item" -> {
                for (int i = 1; i < cmd.size() - 1; i++) {
                    if (cmd.get(i).equals("with")) { cmd.set(i + 1, fixItemArg(cmd.get(i + 1), false, dv)); break; }
                }
            }
            case "replaceitem" -> cmd = replaceItem(cmd, dv);
            case "loot" -> {
                for (int i = 1; i < cmd.size(); i++) {
                    String k = cmd.get(i);
                    if ((k.equals("mine") || k.equals("fish")) && i + 4 < cmd.size()) {
                        int toolIdx = k.equals("fish") ? i + 5 : i + 4;
                        if (toolIdx < cmd.size()) {
                            String tool = cmd.get(toolIdx);
                            if (!tool.equals("mainhand") && !tool.equals("offhand")) cmd.set(toolIdx, fixItemArg(tool, false, dv));
                        }
                    }
                }
            }
            case "summon" -> {
                if (cmd.size() > 5 && cmd.get(5).startsWith("{")) {
                    CompoundTag nbt = NbtUpgrader.parse(cmd.get(5));
                    if (nbt != null) cmd.set(5, NbtUpgrader.fixEntity(nbt, cmd.get(1), dv).toString());
                }
            }
            case "data" -> cmd = data(cmd, dv);
            case "particle" -> cmd = particle(cmd, dv);
            case "attribute" -> cmd = attribute(cmd);
            case "scoreboard" -> {
                // "scoreboard objectives remove <obj> <junk>" was always invalid, but trimming it lets the rest of the function load.
                if (cmd.size() > 4 && cmd.get(1).equals("objectives") && cmd.get(2).equals("remove")) cmd = new ArrayList<>(cmd.subList(0, 4));
            }
            case "setblock" -> { if (cmd.size() > 4) cmd.set(4, fixBlockArg(cmd.get(4), dv)); }
            case "fill" -> { if (cmd.size() > 7) cmd.set(7, fixBlockArg(cmd.get(7), dv)); }
            case "enchant" -> {
                // 1.20.5: sweeping → sweeping_edge (namespaced ids are renamed at text level).
                if (cmd.size() > 2 && cmd.get(2).equals("sweeping")) cmd.set(2, "minecraft:sweeping_edge");
            }
            case "tellraw" -> cmd = textArg(cmd, 2, dv);
            case "title" -> {
                if (cmd.size() > 3 && Set.of("title", "subtitle", "actionbar").contains(cmd.get(2))) cmd = textArg(cmd, 3, dv);
            }
            case "bossbar" -> {
                if (cmd.size() > 3 && cmd.get(1).equals("add")) cmd = textArg(cmd, 3, dv);
                else if (cmd.size() > 4 && cmd.get(1).equals("set") && cmd.get(3).equals("name")) cmd = textArg(cmd, 4, dv);
            }
            case "team" -> {
                if (cmd.size() > 3 && cmd.get(1).equals("add")) cmd = textArg(cmd, 3, dv);
                else if (cmd.size() > 4 && cmd.get(1).equals("modify") && Set.of("prefix", "suffix", "displayName").contains(cmd.get(3))) cmd = textArg(cmd, 4, dv);
            }
            default -> {}
        }
        if (r.equals("scoreboard") && cmd.size() > 5 && cmd.get(1).equals("objectives")
            && (cmd.get(2).equals("add") || (cmd.get(2).equals("modify") && cmd.get(4).equals("displayname")))) {
            cmd = textArg(cmd, 5, dv);
        }
        // 1.20.5: horse armor moved to the body slot.
        if (r.equals("item") || r.equals("loot")) {
            for (int i = 1; i < cmd.size(); i++) if (cmd.get(i).equals("horse.armor")) cmd.set(i, "armor.body");
        }
        out.addAll(cmd);
        return out;
    }

    /** JSON text component from token {@code idx} to the end of the command. */
    private static List<String> textArg(List<String> cmd, int idx, int dv) {
        if (idx >= cmd.size()) return cmd;
        String json = String.join(" ", cmd.subList(idx, cmd.size()));
        String fixed = TextComponents.fixString(json, dv);
        if (fixed.equals(json)) return cmd;
        List<String> out = new ArrayList<>(cmd.subList(0, idx));
        out.add(fixed);
        return out;
    }

    private static final java.util.regex.Pattern UUID_RE = java.util.regex.Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    static String modifierId(String uuid) {
        return "aea:" + uuid.toLowerCase(Locale.ROOT);
    }

    /** 1.20.1: modifier add &lt;uuid&gt; &lt;name&gt; &lt;value&gt; add|multiply_base|multiply → 1.21: modifier add &lt;id&gt; &lt;value&gt; add_value|... */
    private static List<String> attribute(List<String> c) {
        // attribute <target> <attribute> modifier (add|remove|value get) ...
        if (c.size() < 6 || !c.get(3).equals("modifier")) return c;
        List<String> out = new ArrayList<>(c.subList(0, 4));
        String sub = c.get(4);
        out.add(sub);
        int i = 5;
        if (sub.equals("value") && i < c.size() && c.get(i).equals("get")) { out.add(c.get(i++)); }
        if (i >= c.size()) return c;
        String id = c.get(i++);
        boolean legacy = UUID_RE.matcher(id).matches();
        out.add(legacy ? modifierId(id) : id);
        if (sub.equals("add") && legacy) {
            if (i < c.size() && !isNumber(c.get(i))) i++; // drop the legacy name
            if (i < c.size()) out.add(c.get(i++));         // value
            if (i < c.size()) {
                String op = c.get(i++);
                out.add(switch (op) {
                    case "add" -> "add_value";
                    case "multiply_base" -> "add_multiplied_base";
                    case "multiply" -> "add_multiplied_total";
                    default -> op;
                });
            }
        }
        out.addAll(c.subList(Math.min(i, c.size()), c.size()));
        return out;
    }

    private static boolean isNumber(String s) {
        try { Double.parseDouble(s); return true; } catch (NumberFormatException e) { return false; }
    }

    private static List<String> replaceItem(List<String> cmd, int dv) {
        // replaceitem block <x> <y> <z> <slot> <item> [count] | replaceitem entity <sel> <slot> <item> [count]
        List<String> out = new ArrayList<>();
        out.add("item");
        out.add("replace");
        int itemIdx;
        if (cmd.size() > 1 && cmd.get(1).equals("block")) {
            itemIdx = 6;
        } else {
            itemIdx = 4;
        }
        if (cmd.size() <= itemIdx) return cmd;
        out.addAll(cmd.subList(1, itemIdx));
        out.add("with");
        out.add(fixItemArg(cmd.get(itemIdx), false, dv));
        out.addAll(cmd.subList(itemIdx + 1, cmd.size()));
        return out;
    }

    private static List<String> execute(List<String> c, int dv) {
        List<String> out = new ArrayList<>();
        out.add(c.get(0));
        int i = 1;
        while (i < c.size()) {
            String k = c.get(i);
            switch (k) {
                case "run" -> {
                    out.add(k);
                    if (i + 1 < c.size()) out.addAll(upgradeTokens(new ArrayList<>(c.subList(i + 1, c.size())), 0, dv));
                    return out;
                }
                case "as", "at", "in", "on", "align", "anchored", "summon" -> { out.add(k); i++; if (i < c.size()) out.add(c.get(i)); i++; }
                case "positioned", "rotated" -> {
                    out.add(k); i++;
                    if (i < c.size() && (c.get(i).equals("as") || c.get(i).equals("over"))) { out.add(c.get(i)); out.add(c.get(i + 1)); i += 2; }
                    else { int n = k.equals("positioned") ? 3 : 2; for (int j = 0; j < n && i < c.size(); j++) out.add(c.get(i++)); }
                }
                case "facing" -> {
                    out.add(k); i++;
                    if (i < c.size() && c.get(i).equals("entity")) { for (int j = 0; j < 3 && i < c.size(); j++) out.add(c.get(i++)); }
                    else for (int j = 0; j < 3 && i < c.size(); j++) out.add(c.get(i++));
                }
                case "if", "unless" -> { out.add(k); i++; i = condition(c, i, out, dv); }
                case "store" -> { out.add(k); i++; i = store(c, i, out); }
                default -> { out.add(k); i++; }
            }
        }
        return out;
    }

    private static int condition(List<String> c, int i, List<String> out, int dv) {
        if (i >= c.size()) return i;
        String kind = c.get(i);
        out.add(kind); i++;
        switch (kind) {
            case "block" -> {
                for (int j = 0; j < 3 && i < c.size(); j++) out.add(c.get(i++));
                if (i < c.size()) out.add(fixBlockArg(c.get(i++), dv));
            }
            case "blocks" -> { for (int j = 0; j < 10 && i < c.size(); j++) out.add(c.get(i++)); }
            case "entity", "predicate", "dimension", "function", "loaded" -> {
                int n = kind.equals("loaded") ? 3 : 1;
                for (int j = 0; j < n && i < c.size(); j++) out.add(c.get(i++));
            }
            case "biome" -> { for (int j = 0; j < 4 && i < c.size(); j++) out.add(c.get(i++)); }
            case "score" -> {
                out.add(c.get(i++)); out.add(c.get(i++));
                if (i < c.size() && c.get(i).equals("matches")) { out.add(c.get(i++)); out.add(c.get(i++)); }
                else for (int j = 0; j < 3 && i < c.size(); j++) out.add(c.get(i++));
            }
            case "data" -> {
                String src = c.get(i++); out.add(src);
                if (src.equals("block")) { for (int j = 0; j < 3; j++) out.add(c.get(i++)); }
                else out.add(c.get(i++));
                if (i < c.size()) { String p = c.get(i++); out.add(src.equals("storage") ? p : fixPath(p, dv)); }
            }
            case "items" -> {
                String src = c.get(i++); out.add(src);
                int n = src.equals("block") ? 3 : 1;
                for (int j = 0; j < n + 1 && i < c.size(); j++) out.add(c.get(i++));
                if (i < c.size()) out.add(c.get(i++));
            }
            default -> {}
        }
        return i;
    }

    private static int store(List<String> c, int i, List<String> out) {
        if (i + 1 >= c.size()) return i;
        out.add(c.get(i++)); // result|success
        String kind = c.get(i++); out.add(kind);
        switch (kind) {
            case "block" -> { for (int j = 0; j < 6 && i < c.size(); j++) out.add(c.get(i++)); }
            case "entity" -> {
                out.add(c.get(i++));
                if (i < c.size()) out.add(fixPath(c.get(i++), Integer.MIN_VALUE));
                for (int j = 0; j < 2 && i < c.size(); j++) out.add(c.get(i++));
            }
            case "storage" -> { for (int j = 0; j < 4 && i < c.size(); j++) out.add(c.get(i++)); }
            case "bossbar" -> { for (int j = 0; j < 2 && i < c.size(); j++) out.add(c.get(i++)); }
            case "score" -> { for (int j = 0; j < 2 && i < c.size(); j++) out.add(c.get(i++)); }
            default -> {}
        }
        return i;
    }

    private static List<String> data(List<String> c, int dv) {
        if (c.size() < 3) return c;
        String op = c.get(1);
        List<String> out = new ArrayList<>(c.subList(0, 2));
        int i = 2;
        i = target(c, i, out);
        boolean entity = c.get(2).equals("entity") || c.get(2).equals("block");
        boolean isEntity = c.get(2).equals("entity");
        switch (op) {
            case "merge" -> {
                if (i < c.size()) {
                    CompoundTag nbt = NbtUpgrader.parse(c.get(i));
                    if (nbt != null && entity) { NbtUpgrader.fixEntityLike(nbt, dv); out.add(nbt.toString()); }
                    else out.add(c.get(i));
                    i++;
                }
            }
            case "get", "remove" -> { if (i < c.size()) out.add(isEntity ? fixPath(c.get(i++), dv) : c.get(i++)); }
            case "modify" -> {
                if (i < c.size()) out.add(isEntity ? fixPath(c.get(i++), dv) : c.get(i++));
                if (i < c.size()) { String mode = c.get(i++); out.add(mode); if (mode.equals("insert") && i < c.size()) out.add(c.get(i++)); }
                if (i < c.size()) {
                    String src = c.get(i++); out.add(src);
                    if (src.equals("value") && i < c.size()) {
                        String v = c.get(i++);
                        if (v.startsWith("{") && entity) {
                            CompoundTag nbt = NbtUpgrader.parse(v);
                            if (nbt != null) {
                                if (nbt.contains("Count") || nbt.contains("tag")) nbt = NbtUpgrader.fixPartialItem(nbt, dv);
                                else NbtUpgrader.fixEntityLike(nbt, dv);
                                v = nbt.toString();
                            }
                        }
                        out.add(v);
                    } else if ((src.equals("from") || src.equals("string")) && i < c.size()) {
                        String kind = c.get(i);
                        List<String> tmp = new ArrayList<>();
                        i = target(c, i, tmp);
                        out.addAll(tmp);
                        if (i < c.size()) out.add(kind.equals("entity") ? fixPath(c.get(i++), dv) : c.get(i++));
                    }
                }
            }
            default -> {}
        }
        out.addAll(c.subList(Math.min(i, c.size()), c.size()));
        return out;
    }

    private static int target(List<String> c, int i, List<String> out) {
        if (i >= c.size()) return i;
        String kind = c.get(i++); out.add(kind);
        int n = kind.equals("block") ? 3 : 1;
        for (int j = 0; j < n && i < c.size(); j++) out.add(c.get(i++));
        return i;
    }

    // ---------------------------------------------------------------- particles

    private static final Set<String> PARAM_PARTICLES = Set.of("dust", "dust_color_transition", "item", "block",
        "falling_dust", "block_marker", "vibration", "sculk_charge", "shriek", "entity_effect", "ambient_entity_effect");

    private static int paramCount(String type, List<String> c, int from) {
        return switch (type) {
            case "dust" -> 4;
            case "dust_color_transition" -> 7;
            case "item", "block", "falling_dust", "block_marker", "sculk_charge", "shriek" -> 1;
            case "vibration" -> 4;
            default -> 0;
        };
    }

    private static List<String> particle(List<String> c, int dv) {
        if (c.size() < 2) return c;
        String type = c.get(1);
        if (type.contains("{")) return c; // already new syntax
        String path = root(type);
        if (path.equals("entity_effect") || path.equals("ambient_entity_effect")) return spellParticle(c, path);
        if (!PARAM_PARTICLES.contains(path)) return c;
        int n = paramCount(path, c, 2);
        if (c.size() < 2 + n) return c;
        // Make sure the params really are legacy (not already coordinates of a no-param particle).
        StringBuilder legacy = new StringBuilder(type.contains(":") ? type : "minecraft:" + type);
        for (int j = 0; j < n; j++) legacy.append(' ').append(c.get(2 + j));
        CompoundTag fixed = NbtUpgrader.fixParticle(legacy.toString(), dv);
        if (fixed == null) return c;
        String ftype = fixed.getString("type");
        fixed.remove("type");
        List<String> out = new ArrayList<>();
        out.add(c.get(0));
        out.add((ftype.isEmpty() ? type : ftype) + (fixed.isEmpty() ? "" : fixed.toString()));
        out.addAll(c.subList(2 + n, c.size()));
        return out;
    }

    /**
     * 1.20.5: entity_effect needs a colour, ambient_entity_effect is gone. On 1.20.1 a count of 0 used the delta as the
     * colour; otherwise the colour came from the random spread, so white is the closest fixed choice.
     */
    private static List<String> spellParticle(List<String> c, String path) {
        float alpha = path.equals("ambient_entity_effect") ? 0.15f : 1.0f;
        float r = 1, g = 1, b = 1;
        List<String> out = new ArrayList<>(List.of(c.get(0)));
        boolean colourFromDelta = c.size() >= 10 && isNumber(c.get(9)) && Float.parseFloat(c.get(9)) == 0f
            && isNumber(c.get(5)) && isNumber(c.get(6)) && isNumber(c.get(7));
        if (colourFromDelta) {
            r = Float.parseFloat(c.get(5));
            g = Float.parseFloat(c.get(6));
            b = Float.parseFloat(c.get(7));
        }
        out.add("minecraft:entity_effect{color:[" + r + "f," + g + "f," + b + "f," + alpha + "f]}");
        if (colourFromDelta) {
            out.addAll(c.subList(2, 5));
            out.addAll(List.of("0", "0", "0", "0", "1"));
            out.addAll(c.subList(10, c.size()));
        } else {
            out.addAll(c.subList(2, c.size()));
        }
        return out;
    }

    // ---------------------------------------------------------------- items / blocks

    static String fixItemArg(String arg, boolean predicate, int dv) {
        int brace = arg.indexOf('{');
        if (brace < 0 || arg.indexOf('[') >= 0 && arg.indexOf('[') < brace) return arg;
        String id = arg.substring(0, brace);
        CompoundTag tag = NbtUpgrader.parse(arg.substring(brace));
        if (tag == null) return arg;
        boolean isTag = id.startsWith("#");
        CompoundTag legacy = new CompoundTag();
        legacy.putString("id", isTag || id.equals("*") ? NbtUpgrader.dummyIdFor(tag) : (id.contains(":") ? id : "minecraft:" + id));
        legacy.putByte("Count", (byte) 1);
        legacy.put("tag", tag);
        CompoundTag fixed = NbtUpgrader.fixItemStack(legacy, dv);
        CompoundTag comps = fixed.contains("components", Tag.TAG_COMPOUND) ? fixed.getCompound("components") : null;
        if (comps == null || comps.isEmpty()) {
            if (fixed.contains("tag")) {
                comps = new CompoundTag();
                comps.put("minecraft:custom_data", tag);
            } else {
                return id;
            }
        }
        return id + NbtUpgrader.componentsToArgument(comps, predicate);
    }

    static String fixBlockArg(String arg, int dv) {
        int brace = arg.indexOf('{');
        if (brace < 0) return arg;
        CompoundTag nbt = NbtUpgrader.parse(arg.substring(brace));
        if (nbt == null) return arg;
        NbtUpgrader.fixEntityLike(nbt, dv);
        return arg.substring(0, brace) + nbt;
    }

    // ---------------------------------------------------------------- selectors

    static String fixSelector(String tok, int dv) {
        if (!tok.startsWith("@") || tok.length() < 3 || tok.charAt(2) != '[') return tok;
        if (!tok.contains("nbt=")) return tok;
        String inner = tok.substring(3, tok.length() - 1);
        List<String> args = splitArgs(inner);
        StringBuilder sb = new StringBuilder(tok.substring(0, 3));
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            int eq = a.indexOf('=');
            if (eq > 0 && a.substring(0, eq).trim().equals("nbt")) {
                String v = a.substring(eq + 1).trim();
                boolean neg = v.startsWith("!");
                CompoundTag nbt = NbtUpgrader.parse(neg ? v.substring(1) : v);
                if (nbt != null) {
                    NbtUpgrader.fixEntityLike(nbt, dv);
                    a = "nbt=" + (neg ? "!" : "") + nbt;
                }
            }
            if (i > 0) sb.append(',');
            sb.append(a);
        }
        return sb.append(']').toString();
    }

    private static List<String> splitArgs(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        boolean inStr = false;
        char q = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                cur.append(c);
                if (c == '\\' && i + 1 < s.length()) { cur.append(s.charAt(++i)); continue; }
                if (c == q) inStr = false;
                continue;
            }
            if (c == '"' || c == '\'') { inStr = true; q = c; cur.append(c); continue; }
            if (c == '{' || c == '[') depth++;
            if (c == '}' || c == ']') depth--;
            if (c == ',' && depth == 0) { out.add(cur.toString()); cur.setLength(0); continue; }
            cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    // ---------------------------------------------------------------- NBT paths

    private static final Set<String> ITEM_HOLDERS = Set.of("Item", "SelectedItem", "ArmorItem", "SaddleItem",
        "DecorItem", "Book", "RecordItem", "FireworksItem", "Inventory", "HandItems", "ArmorItems", "Items", "EnderItems");

    /** Rewrites paths like {@code SelectedItem.tag.X} → {@code SelectedItem.components."minecraft:custom_data".X}. */
    static String fixPath(String path, int dv) {
        if (dv == Integer.MIN_VALUE) dv = 3465;
        if (!(path.contains("tag") || path.contains("Count") || path.contains("ActiveEffects") || path.contains("Attributes"))) return path;
        List<String> segs = pathSegments(path);
        StringBuilder sb = new StringBuilder();
        boolean inItem = false;
        for (int i = 0; i < segs.size(); i++) {
            String s = segs.get(i);
            boolean bracket = s.startsWith("[") || s.startsWith("{");
            if (bracket) {
                if (inItem && s.startsWith("[{")) {
                    CompoundTag c = NbtUpgrader.parse(s.substring(1, s.length() - 1));
                    if (c != null) s = "[" + NbtUpgrader.fixPartialItem(c, dv) + "]";
                } else if (inItem && s.startsWith("{")) {
                    CompoundTag c = NbtUpgrader.parse(s);
                    if (c != null) s = NbtUpgrader.fixPartialItem(c, dv).toString();
                }
                sb.append(s);
                continue;
            }
            String name = unquote(s);
            String replacement = null;
            if (inItem) {
                if (name.equals("Count")) replacement = "count";
                else if (name.equals("tag")) {
                    String next = i + 1 < segs.size() && !segs.get(i + 1).startsWith("[") && !segs.get(i + 1).startsWith("{") ? unquote(segs.get(i + 1)) : null;
                    String comp = next == null ? null : switch (next) {
                        case "CustomModelData" -> "\"minecraft:custom_model_data\"";
                        case "Damage" -> "\"minecraft:damage\"";
                        case "Unbreakable" -> "\"minecraft:unbreakable\"";
                        case "RepairCost" -> "\"minecraft:repair_cost\"";
                        case "Enchantments" -> "\"minecraft:enchantments\".levels";
                        case "CustomPotionColor" -> "\"minecraft:potion_contents\".custom_color";
                        case "Potion" -> "\"minecraft:potion_contents\".potion";
                        default -> null;
                    };
                    if (comp != null) {
                        replacement = "components." + comp;
                        i++; // consumed the next segment
                    } else if (next != null && next.equals("display") && i + 2 < segs.size()) {
                        String sub = unquote(segs.get(i + 2));
                        if (sub.equals("Name")) { replacement = "components.\"minecraft:custom_name\""; i += 2; }
                        else if (sub.equals("Lore")) { replacement = "components.\"minecraft:lore\""; i += 2; }
                        else if (sub.equals("color")) { replacement = "components.\"minecraft:dyed_color\".rgb"; i += 2; }
                    }
                    if (replacement == null) replacement = "components.\"minecraft:custom_data\"";
                    inItem = false;
                }
            } else {
                if (name.equals("ActiveEffects")) replacement = "active_effects";
                else if (name.equals("Attributes")) replacement = "attributes";
            }
            if (sb.length() > 0) sb.append('.');
            sb.append(replacement != null ? replacement : s);
            if (replacement == null) inItem = ITEM_HOLDERS.contains(name);
        }
        return sb.toString();
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\""))) return s.substring(1, s.length() - 1);
        return s;
    }

    private static List<String> pathSegments(String path) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        boolean inStr = false;
        char q = 0;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (inStr) {
                cur.append(c);
                if (c == '\\' && i + 1 < path.length()) { cur.append(path.charAt(++i)); continue; }
                if (c == q) inStr = false;
                continue;
            }
            if (c == '"' || c == '\'') { inStr = true; q = c; cur.append(c); continue; }
            if (depth == 0 && (c == '[' || c == '{')) {
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
            }
            if (c == '[' || c == '{') depth++;
            if (c == ']' || c == '}') {
                depth--;
                cur.append(c);
                if (depth == 0) { out.add(cur.toString()); cur.setLength(0); }
                continue;
            }
            if (c == '.' && depth == 0) {
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
                continue;
            }
            cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
}
