package dev.alphasucks.aea.mobai;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Per-mob AEA mob AI bookkeeping (AEA 1.5.4 MobAiState). Lives on the mob through {@link Holder}. */
public final class MobAiState {
    long pathStamp = Long.MIN_VALUE;
    MobPathingPower.Config path;
    long aiStamp = Long.MIN_VALUE;
    List<MobAiPower.Config> ai = List.of();
    final Map<ResourceLocation, MobAiPower.Applied> applied = new HashMap<>(2);
    final Set<Goal> aeaGoals = Collections.newSetFromMap(new IdentityHashMap<>());
    Boolean brainMob;
    boolean injectedAttackDamage;

    public interface Holder {
        MobAiState aea$mobAiState();
    }
}
