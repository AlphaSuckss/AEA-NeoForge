package dev.alphasucks.aea.mobai.goal;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

public final class AeaWanderGoal extends RandomStrollGoal {
    private final int horizontal;
    private final int vertical;

    public AeaWanderGoal(PathfinderMob mob, double speed, int interval, int horizontal, int vertical) {
        super(mob, speed, Math.max(1, interval));
        this.horizontal = Math.max(1, horizontal);
        this.vertical = Math.max(1, vertical);
    }

    @Override
    protected Vec3 getPosition() {
        return DefaultRandomPos.getPos(this.mob, horizontal, vertical);
    }
}
