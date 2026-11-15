package dev.alphasucks.aea.hitbox;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.neoforged.neoforge.entity.PartEntity;

import java.util.function.Supplier;

/** One extra hit box of an {@code aea:hitbox_part} power. It is never added to the level as an entity of its own. */
public final class HitboxPart extends PartEntity<LivingEntity> {
    /** The entity the local camera belongs to. Set by the client so a player cannot target their own parts. */
    public static Supplier<Entity> viewer = () -> null;

    public final float width;
    public final float height;
    public final float forward;
    public final float up;
    public final float right;
    public final boolean scaleWithEntity;
    /** Game time of the last tick the owning power was active (server only). */
    long seen;
    private EntityDimensions size;
    private float appliedScale = Float.NaN;

    public HitboxPart(LivingEntity parent, float width, float height, float forward, float up, float right, boolean scaleWithEntity) {
        super(parent);
        this.width = width;
        this.height = height;
        this.forward = forward;
        this.up = up;
        this.right = right;
        this.scaleWithEntity = scaleWithEntity;
        reposition();
    }

    /** Moves the box to its place beside the owner. Returns true when its size changed. */
    public boolean reposition() {
        LivingEntity owner = getParent();
        float scale = scaleWithEntity ? owner.getBbWidth() / owner.getType().getWidth() : 1.0F;
        if (!(scale > 0.0F) || Float.isInfinite(scale)) scale = 1.0F;
        boolean resized = scale != appliedScale;
        if (resized) {
            appliedScale = scale;
            size = EntityDimensions.scalable(width * scale, height * scale);
            refreshDimensions();
        }
        float yaw = owner.yBodyRot * Mth.DEG_TO_RAD;
        float sin = Mth.sin(yaw);
        float cos = Mth.cos(yaw);
        double oldX = getX();
        double oldY = getY();
        double oldZ = getZ();
        // Yaw 0 faces +Z; the owner's right is then -X.
        setPos(owner.getX() + (-sin * forward - cos * right) * scale,
            owner.getY() + up * scale,
            owner.getZ() + (cos * forward - sin * right) * scale);
        xo = oldX;
        yo = oldY;
        zo = oldZ;
        xOld = oldX;
        yOld = oldY;
        zOld = oldZ;
        return resized;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return size != null ? size : super.getDimensions(pose);
    }

    @Override
    public boolean isPickable() {
        return !level().isClientSide() || getParent() != viewer.get();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        LivingEntity owner = getParent();
        if (source.getEntity() == owner || source.getDirectEntity() == owner) return false;
        return owner.hurt(source, amount);
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || getParent() == entity;
    }

    @Override
    public void push(double x, double y, double z) {
        getParent().push(x, y, z);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
