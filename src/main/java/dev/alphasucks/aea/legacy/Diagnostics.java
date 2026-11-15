package dev.alphasucks.aea.legacy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Commands embedded in upgraded legacy JSON, kept so {@code /aea validate} can parse-check them. */
public final class Diagnostics {
    private Diagnostics() {}

    public static final Map<String, String> COMMANDS = new ConcurrentHashMap<>();
    public static final Map<net.minecraft.resources.ResourceLocation, com.google.gson.JsonElement> POWERS = new ConcurrentHashMap<>();

    static void command(String command) {
        if (COMMANDS.size() < 20000) COMMANDS.putIfAbsent(command, "");
    }
}
