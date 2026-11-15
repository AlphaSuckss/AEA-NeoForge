package dev.alphasucks.aea.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.IntToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code {value}}, {@code {player}} and {@code {slider:<id>}} placeholders in GUI text and {@code aea:gui_command}. */
public final class GuiPlaceholders {
    private GuiPlaceholders() {}

    private static final Pattern TOKEN = Pattern.compile("\\{(value|player|slider:[^{}\\s]+)}");
    private static final ThreadLocal<DecimalFormat> FORMAT = ThreadLocal.withInitial(() -> {
        DecimalFormat f = new DecimalFormat("0.####", DecimalFormatSymbols.getInstance(Locale.ROOT));
        f.setGroupingUsed(false);
        return f;
    });

    public static String format(double v) {
        return Double.isNaN(v) ? "0" : FORMAT.get().format(v);
    }

    public static boolean has(@Nullable String s) {
        return s != null && s.indexOf('{') >= 0 && TOKEN.matcher(s).find();
    }

    /** Resolves tokens for {@code menu}; {@code slider} is the element whose value {@code {value}} means (-1: none). */
    public static Function<String, String> resolver(@Nullable GuiMenu menu, @Nullable Player player, int slider, IntToDoubleFunction values) {
        return s -> apply(s, token -> {
            if (token.equals("player")) return player == null ? null : player.getName().getString();
            if (menu == null || menu.model == null) return null;
            if (token.equals("value")) return slider >= 0 && slider < menu.sliders.length ? format(values.applyAsDouble(slider)) : null;
            int e = menu.model.sliderElement(token.substring("slider:".length()));
            return e < 0 ? null : format(values.applyAsDouble(e));
        });
    }

    public static String apply(@Nullable String s, Function<String, String> tokens) {
        if (s == null || s.indexOf('{') < 0) return s;
        Matcher m = TOKEN.matcher(s);
        StringBuilder out = null;
        int last = 0;
        while (m.find()) {
            String rep = tokens.apply(m.group(1));
            if (rep == null) continue;
            if (out == null) out = new StringBuilder(s.length() + 16);
            out.append(s, last, m.start()).append(rep);
            last = m.end();
        }
        if (out == null) return s;
        out.append(s, last, s.length());
        return out.toString();
    }

    public static boolean has(Component text) {
        return has(text.getString()) || text.getContents() instanceof TranslatableContents;
    }

    public static Component apply(Component text, Function<String, String> fn) {
        ComponentContents contents = text.getContents();
        MutableComponent out;
        if (contents instanceof PlainTextContents.LiteralContents literal) {
            out = Component.literal(fn.apply(literal.text()));
        } else if (contents instanceof TranslatableContents tr) {
            Object[] args = tr.getArgs();
            Object[] mapped = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                Object a = args[i];
                mapped[i] = a instanceof Component c ? apply(c, fn) : a instanceof String str ? fn.apply(str) : a;
            }
            out = tr.getFallback() == null
                ? Component.translatable(tr.getKey(), mapped)
                : Component.translatableWithFallback(tr.getKey(), tr.getFallback(), mapped);
        } else {
            out = MutableComponent.create(contents);
        }
        out.setStyle(text.getStyle());
        for (Component sibling : text.getSiblings()) out.append(apply(sibling, fn));
        return out;
    }
}
