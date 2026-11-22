package dev.alphasucks.aea.compat.spellengine;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies {@code aea-spellengine.mixins.json} only when Spell Engine is installed. It looks for the target's class
 * file instead of loading the class, so nothing is defined early.
 */
public final class SpellEngineMixinPlugin implements IMixinConfigPlugin {
    private static final String MARKER = "net/spell_engine/internals/SpellParameters.class";
    private boolean present;

    @Override
    public void onLoad(String mixinPackage) {
        present = SpellEngineMixinPlugin.class.getClassLoader().getResource(MARKER) != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return present;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
