package dev.alphasucks.aea.mindcontrol;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * What a mind controller leaves behind while their mind is elsewhere: their body, wearing their skin, armour and held
 * items. It isn't saved with the world; damage it takes goes to the controller.
 */
public final class ControlBody extends LivingEntity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(ControlBody.class, EntityDataSerializers.OPTIONAL_UUID);

    private final NonNullList<ItemStack> hands = NonNullList.withSize(2, ItemStack.EMPTY);
    private final NonNullList<ItemStack> armor = NonNullList.withSize(4, ItemStack.EMPTY);

    public ControlBody(EntityType<? extends ControlBody> type, Level level) {
        super(type, level);
    }

    static ControlBody of(ServerPlayer owner) {
        ControlBody body = new ControlBody(MindControl.BODY.get(), owner.level());
        body.entityData.set(OWNER, Optional.of(owner.getUUID()));
        body.moveTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), owner.getXRot());
        body.setYHeadRot(owner.getYHeadRot());
        body.yBodyRot = owner.yBodyRot;
        body.setCustomName(owner.getDisplayName());
        body.setCustomNameVisible(true);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ANIMAL_ARMOR) body.setItemSlot(slot, owner.getItemBySlot(slot).copy());
        }
        var max = body.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) max.setBaseValue(owner.getMaxHealth());
        body.setHealth(owner.getHealth());
        return body;
    }

    public Optional<UUID> owner() {
        return entityData.get(OWNER);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isInvulnerableTo(source) || invulnerableTime > 10) return false;
        if (!MindControl.hurtBody(this, source, amount)) return false;
        invulnerableTime = 20;
        hurtDuration = 10;
        hurtTime = 10;
        level().broadcastDamageEvent(this, source);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 20 == 0 && !MindControl.bodyInUse(this)) discard();
    }

    /** Bodies only exist during a session, so they are never saved. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return armor;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot.getType()) {
            case HAND -> hands.get(slot.getIndex());
            case HUMANOID_ARMOR -> armor.get(slot.getIndex());
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        switch (slot.getType()) {
            case HAND -> hands.set(slot.getIndex(), stack);
            case HUMANOID_ARMOR -> armor.set(slot.getIndex(), stack);
            default -> {}
        }
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
