package saga.skullheart.entity;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.item.ModernKineticGunItem;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.fml.LogicalSide;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class FunnelTurretEntity extends LivingEntity {

    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(FunnelTurretEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(FunnelTurretEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DATA_TACTIC_MODE = SynchedEntityData.defineId(FunnelTurretEntity.class, EntityDataSerializers.INT);

    private static final double SCAN_RANGE = 60.0;
    private static final double ATTACK_RANGE = 45.0;

    private int turretIndex = 0;
    private LivingEntity cachedOwner;
    private double fireCharge = 0;
    private LivingEntity currentTarget;

    private final IGunOperator gunOperator;
    private boolean gunDrawn = false;
    private long nextShootTime = 0;

    private Vec3 currentVelocity = Vec3.ZERO;

    public FunnelTurretEntity(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.gunOperator = IGunOperator.fromLivingEntity(this);
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.FOLLOW_RANGE, SCAN_RANGE);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ITEM, ItemStack.EMPTY);
        this.entityData.define(DATA_OWNER, Optional.empty());
        this.entityData.define(DATA_TACTIC_MODE, 0);
    }

    public void setStoredItem(ItemStack stack) {
        this.entityData.set(DATA_ITEM, stack.copy());
        this.gunDrawn = false;
        this.nextShootTime = 0;
    }

    public ItemStack getStoredItem() { return this.entityData.get(DATA_ITEM); }

    public void setOwner(LivingEntity owner) {
        this.cachedOwner = owner;
        if (owner != null) this.entityData.set(DATA_OWNER, Optional.of(owner.getUUID()));
    }

    public UUID getOwnerUUID() { return this.entityData.get(DATA_OWNER).orElse(null); }

    @Nullable
    public LivingEntity getOwner() {
        if (cachedOwner == null || !cachedOwner.isAlive()) {
            UUID uuid = getOwnerUUID();
            if (uuid != null && level() instanceof ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(uuid);
                if (entity instanceof LivingEntity living) cachedOwner = living;
                else cachedOwner = level().getPlayerByUUID(uuid);
            }
        }
        return cachedOwner;
    }

    public void setTurretIndex(int index) { this.turretIndex = index; }
    public int getTurretIndex() { return this.turretIndex; }
    public void setTacticMode(int mode) { this.entityData.set(DATA_TACTIC_MODE, mode); }
    public int getTacticMode() { return this.entityData.get(DATA_TACTIC_MODE); }
    public double getFireCharge() { return fireCharge; }

    public boolean hasGun() {
        return !getStoredItem().isEmpty() && getStoredItem().getItem() instanceof ModernKineticGunItem;
    }

    public ModernKineticGunItem getGunItem() {
        return hasGun() ? (ModernKineticGunItem) getStoredItem().getItem() : null;
    }

    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean isInvulnerableTo(DamageSource source) { return true; }
    @Override public boolean ignoreExplosion() { return true; }
    @Override public boolean causeFallDamage(float fallDistance, float damageMultiplier, DamageSource source) { return false; }
    @Override protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            addIdleParticles();
            LivingEntity owner = getOwner();
            if (owner != null && owner.isAlive()) {
                if (!hasGun()) {
                    idleOrbit(owner);
                } else {
                    if (!gunDrawn) {
                        gunOperator.draw(this::getStoredItem);
                        gunDrawn = true;
                    }
                    if (currentTarget != null && currentTarget.isAlive()) {
                        attackOrbit(currentTarget);
                    } else {
                        idleOrbit(owner);
                    }
                }
            }
            return;
        }

        LivingEntity owner = getOwner();
        if (owner == null || !owner.isAlive()) {
            this.discard();
            return;
        }

        if (!hasGun()) {
            idleOrbit(owner);
            return;
        }

        if (!gunDrawn) {
            gunOperator.draw(this::getStoredItem);
            gunDrawn = true;
        }

        Optional<LivingEntity> optTarget = findTarget(owner);
        if (optTarget.isPresent()) {
            currentTarget = optTarget.get();
            attackOrbit(currentTarget);

            long currentTime = level().getGameTime();
            if (currentTime >= nextShootTime && distanceTo(currentTarget) <= ATTACK_RANGE) {
                tryShoot(currentTarget, owner);
                nextShootTime = currentTime + calculateCooldown();
            }
        } else {
            currentTarget = null;
            idleOrbit(owner);
        }

        if (fireCharge > 0) fireCharge -= 0.1;
    }

    private int calculateCooldown() {
        if (!hasGun()) return 6;

        ItemStack gunStack = getStoredItem();
        ModernKineticGunItem gunItem = getGunItem();
        if (gunItem == null) return 6;

        ResourceLocation gunId = gunItem.getGunId(gunStack);
        var optional = TimelessAPI.getCommonGunIndex(gunId);

        if (optional.isPresent()) {
            GunData gunData = optional.get().getGunData();
            int rpm = gunData.getRoundsPerMinute();
            int interval = Math.max(1, (int) (1200.0 / rpm));

            int mode = getTacticMode();
            return switch (mode) {
                case 0 -> interval;
                case 1 -> Math.max(1, (int)(interval * 0.8));
                case 2 -> (int)(interval * 1.2);
                case 3 -> Math.max(1, (int)(interval * 0.7));
                default -> interval;
            };
        }
        return 6;
    }

    private void tryShoot(LivingEntity target, LivingEntity owner) {
        if (!hasGun() || !(level() instanceof ServerLevel serverLevel)) return;

        ModernKineticGunItem gunItem = getGunItem();
        ItemStack originalStack = getStoredItem();
        if (gunItem == null || originalStack.isEmpty()) return;

        ResourceLocation gunId = gunItem.getGunId(originalStack);
        if (gunId == null || gunId.getPath().isEmpty()) return;

        var indexOptional = TimelessAPI.getCommonGunIndex(gunId);
        if (indexOptional.isEmpty()) return;

        var gunIndex = indexOptional.get();
        GunData gunData = gunIndex.getGunData();
        if (gunData == null) return;

        BulletData bulletData = gunData.getBulletData();
        if (bulletData == null) return;

        ResourceLocation ammoId = gunData.getAmmoId();

        this.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        ItemStack shootableGunStack = originalStack.copy();

        // ★★★ GunFireEvent を発火させる（正しい3引数コンストラクタ）★★★
        com.tacz.guns.api.event.common.GunFireEvent gunFireEvent = new com.tacz.guns.api.event.common.GunFireEvent(
                this,                                   // shooter (タレット自身)
                shootableGunStack,                      // 使用する銃
                LogicalSide.SERVER                      // サーバー側
        );

        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(gunFireEvent)) {
            // イベントがキャンセルされた場合（マナ不足など）、発射を中止
            return;
        }

        EntityKineticBullet bullet;
        try {
            bullet = new EntityKineticBullet(
                    serverLevel, this, shootableGunStack, ammoId, gunId, true, gunData, bulletData
            );
        } catch (NullPointerException npe) {
            return;
        }

        bullet.setPos(this.getX(), this.getEyeY() - 0.1, this.getZ());

        float speed = bulletData.getSpeed();
        Vec3 lookVec = this.getLookAngle();
        bullet.setDeltaMovement(lookVec.scale(speed));
        bullet.shoot(lookVec.x, lookVec.y, lookVec.z, speed, 0.0F);

        if (serverLevel.addFreshEntity(bullet)) {
            fireCharge = 1.0;
            serverLevel.sendParticles(ParticleTypes.FLASH, this.getX() + lookVec.x, this.getY() + 1.0 + lookVec.y, this.getZ() + lookVec.z, 1, 0, 0, 0, 0);
            playFireSoundReflective(serverLevel, gunIndex, gunData, gunId);
        }
    }
    private void playFireSoundReflective(ServerLevel serverLevel, Object gunIndex, GunData gunData, ResourceLocation gunId) {
        try {
            ResourceLocation fireSoundUri = resolveFireSoundReflective(gunIndex, gunData, gunId);
            if (fireSoundUri != null) {
                serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.minecraft.sounds.SoundEvent.createVariableRangeEvent(fireSoundUri),
                        SoundSource.PLAYERS, 0.7F, 1.0F + (random.nextFloat() - random.nextFloat() * 0.15F));
            } else {
                serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.4F, 1.8F);
            }
        } catch (Exception e) {
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.4F, 1.8F);
        }
    }

    private ResourceLocation resolveFireSoundReflective(Object gunIndexObj, GunData gunData, ResourceLocation gunId) {
        try {
            if (gunIndexObj != null) {
                try {
                    java.lang.reflect.Method m = gunIndexObj.getClass().getMethod("getSounds");
                    Object soundsObj = m.invoke(gunIndexObj);
                    if (soundsObj != null) {
                        ResourceLocation rl = extractResourceFromSoundHolderReflective(soundsObj);
                        if (rl != null) return rl;
                    }
                } catch (NoSuchMethodException ignored) {}
            }
            try {
                java.lang.reflect.Method m2 = gunData.getClass().getMethod("getFireSound");
                Object res = m2.invoke(gunData);
                if (res instanceof ResourceLocation r) return r;
                if (res instanceof String s && !s.isEmpty()) return new ResourceLocation(s);
            } catch (NoSuchMethodException ignored) {}
        } catch (Exception ignored) {}

        return new ResourceLocation(gunId.getNamespace(), gunId.getPath() + "_fire");
    }

    private ResourceLocation extractResourceFromSoundHolderReflective(Object soundsObj) {
        try {
            for (java.lang.reflect.Method m : soundsObj.getClass().getMethods()) {
                String name = m.getName().toLowerCase();
                if ((name.contains("fire") || name.contains("shoot") || name.contains("normal")) && m.getParameterCount() == 0) {
                    Object res = m.invoke(soundsObj);
                    if (res instanceof ResourceLocation rl) return rl;
                    if (res instanceof String s && !s.isEmpty()) return new ResourceLocation(s);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Optional<LivingEntity> findTarget(LivingEntity owner) {
        int tacticMode = getTacticMode();
        UUID ownerUuid = owner.getUUID();

        if (tacticMode == 0 || tacticMode == 3 || owner.getLastHurtByMob() != null || owner.getLastHurtMob() != null) {
            LivingEntity attacker = owner.getLastHurtByMob();
            if (attacker != null && attacker.isAlive() && !attacker.getUUID().equals(ownerUuid) && !(attacker instanceof FunnelTurretEntity) && distanceTo(attacker) <= SCAN_RANGE) {
                return Optional.of(attacker);
            }
            LivingEntity targetMob = owner.getLastHurtMob();
            if (targetMob != null && targetMob.isAlive() && !targetMob.getUUID().equals(ownerUuid) && !(targetMob instanceof FunnelTurretEntity) && distanceTo(targetMob) <= SCAN_RANGE) {
                return Optional.of(targetMob);
            }
        }

        AABB area = this.getBoundingBox().inflate(SCAN_RANGE);
        List<LivingEntity> entities = level().getEntitiesOfClass(LivingEntity.class, area, entity -> {
            if (entity.getUUID().equals(this.getUUID()) || entity == owner || entity.getUUID().equals(ownerUuid) || !entity.isAlive() || entity instanceof FunnelTurretEntity) return false;

            if (owner instanceof Player p && entity instanceof Player targetP) {
                if (targetP.isAlliedTo(p) || p.isAlliedTo(targetP) || targetP.isCreative()) return false;
            }

            return switch (tacticMode) {
                case 0 -> entity instanceof Enemy || entity instanceof Monster ||
                        entity instanceof WitherBoss || entity instanceof EnderDragon ||
                        (entity instanceof Mob mob && mob.getTarget() == owner);
                case 1 -> entity instanceof Mob mob && !(entity instanceof Player) && mob.isAttackable() && !mob.isInvulnerable() && mob.getMaxHealth() > 0.0F;
                case 2 -> entity instanceof Player;
                case 3 -> entity instanceof Enemy || entity instanceof Monster || entity instanceof WitherBoss || entity instanceof EnderDragon;
                default -> false;
            };
        });

        if (entities.isEmpty()) return Optional.empty();

        if (tacticMode != 3) {
            entities.sort(Comparator.comparingDouble(this::distanceTo));
            int activeTurrets = owner.level().getEntitiesOfClass(FunnelTurretEntity.class, owner.getBoundingBox().inflate(15)).size();
            int targetSelectIndex = this.turretIndex % Math.max(1, activeTurrets);
            if (targetSelectIndex < entities.size()) {
                return Optional.of(entities.get(targetSelectIndex));
            }
            return Optional.of(entities.get(0));
        }

        return entities.stream().min(Comparator.comparingDouble(this::distanceTo));
    }

    private void attackOrbit(LivingEntity target) {
        long time = level().getGameTime();
        double baseTime = time * 0.08;
        int tacticMode = getTacticMode();

        if (tacticMode == 3) {
            double wildTime = time * 0.15 + (this.turretIndex * 45.0);
            double radius = 4.0 + Math.sin(wildTime * 0.3) * 2.0;
            double angle = wildTime + (turretIndex * (Math.PI * 2.0 / 4.0));

            double xOffset = Math.cos(angle) * radius;
            double zOffset = Math.sin(angle) * radius;
            double yOffset = target.getBbHeight() * 0.5 + 1.0 + Math.sin(wildTime * 0.7) * 1.8;

            smoothMoveTo(target.position().add(xOffset, yOffset, zOffset));
        } else {
            double radius = 5.5 + Math.sin(baseTime * 0.5) * 1.5;
            double angle = baseTime + (turretIndex * Math.PI / 2);

            double xOffset = Math.cos(angle) * radius;
            double zOffset = Math.sin(angle) * radius;
            double yOffset = target.getBbHeight() + 1.5 + Math.sin(baseTime * 0.6) * 0.5;

            smoothMoveTo(target.position().add(xOffset, yOffset, zOffset));
        }

        smoothLookAt(target.getEyePosition());
    }

    private void idleOrbit(LivingEntity owner) {
        long time = level().getGameTime();
        Vec3 lookVec = owner.getLookAngle();

        Vec3 forward = new Vec3(lookVec.x, 0, lookVec.z).normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);

        double xSide, zSide;
        double sideOffset = 1.6;
        double backOffset = -1.0;

        double hoverY = Math.sin(time * 0.05 + turretIndex * 1.5) * 0.15;
        double heightOffset = owner.getBbHeight() * 0.75;

        if (this.turretIndex % 2 == 0) {
            double innerOffset = (this.turretIndex == 0) ? 0.0 : 0.5;
            Vec3 leftPos = right.scale(-sideOffset - innerOffset).add(forward.scale(backOffset));
            xSide = leftPos.x;
            zSide = leftPos.z;
            heightOffset += (this.turretIndex == 0) ? 0.3 : -0.2;
        } else {
            double innerOffset = (this.turretIndex == 1) ? 0.0 : 0.5;
            Vec3 rightPos = right.scale(sideOffset + innerOffset).add(forward.scale(backOffset));
            xSide = rightPos.x;
            zSide = rightPos.z;
            heightOffset += (this.turretIndex == 1) ? 0.3 : -0.2;
        }

        Vec3 goalPos = owner.position().add(xSide, heightOffset + hoverY, zSide);
        smoothMoveTo(goalPos);
        smoothLookAt(owner.getEyePosition().add(owner.getLookAngle().scale(15)));
    }

    private void smoothMoveTo(Vec3 goalPos) {
        Vec3 currentPos = position();
        Vec3 targetDelta = goalPos.subtract(currentPos);
        double distance = targetDelta.length();

        if (distance > 0.001) {
            double springForce = 0.18;
            Vec3 desiredVelocity = targetDelta.scale(springForce);
            currentVelocity = currentVelocity.scale(0.82).add(desiredVelocity.scale(0.18));

            if (currentVelocity.length() > 1.8) {
                currentVelocity = currentVelocity.normalize().scale(1.8);
            }

            this.setDeltaMovement(currentVelocity);
            Vec3 nextPos = currentPos.add(currentVelocity);
            this.setPos(nextPos.x, nextPos.y, nextPos.z);
        } else {
            this.setPos(goalPos.x, goalPos.y, goalPos.z);
            this.setDeltaMovement(Vec3.ZERO);
            currentVelocity = Vec3.ZERO;
        }
    }

    private void smoothLookAt(Vec3 targetPos) {
        Vec3 diff = targetPos.subtract(this.getEyePosition());
        double d0 = diff.x;
        double d1 = diff.y;
        double d2 = diff.z;
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);

        float targetYaw = (float)(Math.atan2(d2, d0) * (180D / Math.PI)) - 90.0F;
        float targetPitch = (float)(-(Math.atan2(d1, d3) * (180D / Math.PI)));

        this.setXRot(lerpRotation(this.getXRot(), targetPitch, 0.18F));
        float yaw = lerpRotation(this.getYRot(), targetYaw, 0.18F);
        this.setYRot(yaw);

        this.yHeadRot = yaw;
        this.yBodyRot = yaw;
    }

    private float lerpRotation(float current, float target, float pct) {
        float diff = target - current;
        while (diff < -180.0F) diff += 360.0F;
        while (diff >= 180.0F) diff -= 360.0F;
        return current + diff * pct;
    }

    private void addIdleParticles() {
        if (level().isClientSide && random.nextInt(5) == 0) {
            level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    getX() + (random.nextDouble() - 0.5) * 0.3,
                    getY() + random.nextDouble() * 0.5,
                    getZ() + (random.nextDouble() - 0.5) * 0.3,
                    0, 0.01, 0);
        }
    }

    @Override public Iterable<ItemStack> getArmorSlots() { return List.of(); }
    @Override public ItemStack getItemBySlot(net.minecraft.world.entity.EquipmentSlot slot) { return ItemStack.EMPTY; }
    @Override public void setItemSlot(net.minecraft.world.entity.EquipmentSlot slot, ItemStack stack) {}
    @Override public net.minecraft.world.entity.HumanoidArm getMainArm() { return net.minecraft.world.entity.HumanoidArm.RIGHT; }
    @Override public void heal(float amount) {}
    @Override public boolean isSensitiveToWater() { return false; }
    @Override protected void actuallyHurt(DamageSource source, float amount) {}
    @Override public boolean canBeCollidedWith() { return true; }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("Item")) setStoredItem(ItemStack.of(nbt.getCompound("Item")));
        if (nbt.contains("TacticMode")) setTacticMode(nbt.getInt("TacticMode"));
        if (nbt.contains("Index")) this.turretIndex = nbt.getInt("Index");
        if (nbt.contains("NextShootTime")) this.nextShootTime = nbt.getLong("NextShootTime");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.put("Item", getStoredItem().save(new CompoundTag()));
        nbt.putInt("TacticMode", getTacticMode());
        nbt.putInt("Index", turretIndex);
        nbt.putLong("NextShootTime", nextShootTime);
    }
}