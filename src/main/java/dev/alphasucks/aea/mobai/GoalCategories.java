package dev.alphasucks.aea.mobai;

import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;

import java.util.Locale;
import java.util.Map;

/**
 * The "remove" categories of aea:mob_ai (AEA 1.5.4). A value containing '.' or '$' is a goal class name instead.
 * Class names written for 1.20.1 Fabric (yarn or intermediary) are translated to their 1.21.1 Mojang names.
 */
final class GoalCategories {
    private GoalCategories() {}

    static boolean isClassName(String s) {
        return s.indexOf('.') >= 0 || s.indexOf('$') >= 0;
    }

    private static boolean named(Goal goal, String... parts) {
        String name = goal.getClass().getName();
        for (String p : parts) if (name.contains(p)) return true;
        return false;
    }

    static boolean matchesGoal(String category, Goal goal) {
        if (isClassName(category)) return matchesClass(category, goal);
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "all", "goals" -> true;
            case "wander" -> goal instanceof RandomStrollGoal || named(goal, "Wander", "Stroll");
            case "look_around" -> goal instanceof RandomLookAroundGoal || named(goal, "LookAround", "RandomLook");
            case "look_at" -> goal instanceof LookAtPlayerGoal || named(goal, "LookAt", "LookAtPlayer");
            case "melee" -> goal instanceof MeleeAttackGoal || named(goal, "Melee");
            case "ranged" -> goal instanceof RangedBowAttackGoal || goal instanceof RangedAttackGoal || goal instanceof RangedCrossbowAttackGoal
                || named(goal, "Bow", "Crossbow", "Ranged", "Projectile");
            case "follow_owner" -> goal instanceof FollowOwnerGoal || named(goal, "FollowOwner");
            case "panic" -> goal instanceof PanicGoal || named(goal, "Panic", "EscapeDanger");
            case "flee" -> goal instanceof AvoidEntityGoal || named(goal, "Flee", "Avoid");
            case "swim" -> goal instanceof FloatGoal || named(goal, "Swim", "Float");
            default -> false;
        };
    }

    static boolean matchesTarget(String category, Goal goal) {
        if (isClassName(category)) return matchesClass(category, goal);
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "all", "targets" -> true;
            case "revenge" -> goal instanceof HurtByTargetGoal || named(goal, "Revenge", "HurtBy");
            default -> false;
        };
    }

    private static boolean matchesClass(String className, Goal goal) {
        String name = goal.getClass().getName();
        if (name.equals(className) || name.endsWith("." + className) || name.endsWith("$" + className)) return true;
        String mapped = LEGACY_NAMES.get(simple(className));
        if (mapped == null) return false;
        return name.equals(mapped) || name.endsWith("." + mapped) || name.endsWith("$" + mapped);
    }

    private static String simple(String className) {
        int i = Math.max(className.lastIndexOf('.'), className.lastIndexOf('/'));
        return i >= 0 ? className.substring(i + 1) : className;
    }

    /** 1.20.1 Fabric goal class names (yarn simple name and intermediary) → 1.21.1 Mojang simple name. */
    private static final Map<String, String> LEGACY_NAMES = Map.ofEntries(
        Map.entry("WanderAroundGoal", "RandomStrollGoal"), Map.entry("class_1379", "RandomStrollGoal"),
        Map.entry("WanderAroundFarGoal", "WaterAvoidingRandomStrollGoal"), Map.entry("class_1394", "WaterAvoidingRandomStrollGoal"),
        Map.entry("LookAroundGoal", "RandomLookAroundGoal"), Map.entry("class_1376", "RandomLookAroundGoal"),
        Map.entry("LookAtEntityGoal", "LookAtPlayerGoal"), Map.entry("class_1361", "LookAtPlayerGoal"),
        Map.entry("MeleeAttackGoal", "MeleeAttackGoal"), Map.entry("class_1366", "MeleeAttackGoal"),
        Map.entry("BowAttackGoal", "RangedBowAttackGoal"), Map.entry("class_1380", "RangedBowAttackGoal"),
        Map.entry("CrossbowAttackGoal", "RangedCrossbowAttackGoal"), Map.entry("class_1383", "RangedCrossbowAttackGoal"),
        Map.entry("ProjectileAttackGoal", "RangedAttackGoal"), Map.entry("class_1381", "RangedAttackGoal"),
        Map.entry("FollowOwnerGoal", "FollowOwnerGoal"), Map.entry("class_1350", "FollowOwnerGoal"),
        Map.entry("EscapeDangerGoal", "PanicGoal"), Map.entry("class_1374", "PanicGoal"),
        Map.entry("FleeEntityGoal", "AvoidEntityGoal"), Map.entry("class_1338", "AvoidEntityGoal"),
        Map.entry("SwimGoal", "FloatGoal"), Map.entry("class_1347", "FloatGoal"),
        Map.entry("RevengeGoal", "HurtByTargetGoal"), Map.entry("class_1399", "HurtByTargetGoal"),
        Map.entry("ActiveTargetGoal", "NearestAttackableTargetGoal"), Map.entry("class_1400", "NearestAttackableTargetGoal"),
        Map.entry("TrackOwnerAttackerGoal", "OwnerHurtByTargetGoal"), Map.entry("class_1403", "OwnerHurtByTargetGoal"),
        Map.entry("AttackWithOwnerGoal", "OwnerHurtTargetGoal"), Map.entry("class_1406", "OwnerHurtTargetGoal"),
        Map.entry("SitGoal", "SitWhenOrderedToGoal"), Map.entry("class_1386", "SitWhenOrderedToGoal"),
        Map.entry("TemptGoal", "TemptGoal"), Map.entry("class_1391", "TemptGoal"),
        Map.entry("AnimalMateGoal", "BreedGoal"), Map.entry("class_1341", "BreedGoal"),
        Map.entry("FollowParentGoal", "FollowParentGoal"), Map.entry("class_1353", "FollowParentGoal"),
        Map.entry("PounceAtTargetGoal", "LeapAtTargetGoal"), Map.entry("class_1359", "LeapAtTargetGoal"),
        Map.entry("ZombieAttackGoal", "ZombieAttackGoal"), Map.entry("class_1396", "ZombieAttackGoal")
    );
}
