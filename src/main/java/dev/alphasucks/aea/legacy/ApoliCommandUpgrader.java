package dev.alphasucks.aea.legacy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rewrites Apoli 2.9.2 / Origins 1.10.2 command syntax to what Overgrown's Apoli/Origins accept, keeping 2.9 semantics.
 * <ul>
 *   <li>{@code power grant <t> <p>}: 2.9 used source {@code apoli:command}; Overgrown defaults to the power id, so the source is made explicit.</li>
 *   <li>{@code power revoke <t> <p> [<source>]}: 2.9 revoked from {@code apoli:command} (or the given source); Overgrown needs {@code from <source>}
 *       and without it revokes the power completely.</li>
 *   <li>{@code power list|sources|clear}: no Overgrown equivalent; routed to {@code aea:power}, which implements the 2.9 behavior and return values.</li>
 *   <li>{@code power remove|revokeall|has}, all of {@code resource} and {@code origin} already parse and behave the same.</li>
 * </ul>
 * Idempotent: an upgraded command is returned unchanged.
 */
public final class ApoliCommandUpgrader {
    private ApoliCommandUpgrader() {}

    public static String upgrade(String command) {
        if (command == null || command.isBlank()) return command;
        try {
            boolean slash = command.startsWith("/");
            String body = slash ? command.substring(1) : command;
            List<String> t = CommandUpgrader.split(body);
            if (t.isEmpty()) return command;
            List<String> up = tokens(t);
            if (up.equals(t)) return command;
            String out = String.join(" ", up);
            return slash ? "/" + out : out;
        } catch (Throwable e) {
            return command;
        }
    }

    static List<String> tokens(List<String> t) {
        if (t.isEmpty()) return t;
        String root = t.get(0).toLowerCase(Locale.ROOT);
        if (root.startsWith("minecraft:")) root = root.substring(10);
        if (root.equals("execute")) {
            int run = t.indexOf("run");
            if (run < 0 || run == t.size() - 1) return t;
            List<String> out = new ArrayList<>(t.subList(0, run + 1));
            out.addAll(tokens(new ArrayList<>(t.subList(run + 1, t.size()))));
            return out;
        }
        if (!root.equals("power") && !root.equals("apoli:power")) return t;
        if (t.size() < 3) return t;
        String sub = t.get(1).toLowerCase(Locale.ROOT);
        List<String> c = new ArrayList<>(t);
        switch (sub) {
            case "grant" -> { if (c.size() == 4) c.add("apoli:command"); }
            case "revoke" -> {
                if (c.size() == 4) { c.add("from"); c.add("apoli:command"); }
                else if (c.size() == 5 && !c.get(4).equals("from")) c.add(4, "from");
            }
            case "list", "sources", "clear" -> c.set(0, "aea:power");
            default -> {}
        }
        return c;
    }
}
