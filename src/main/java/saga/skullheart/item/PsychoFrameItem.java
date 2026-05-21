package saga.skullheart.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class PsychoFrameItem extends Item {

    public PsychoFrameItem(Properties properties) {
        super(properties.stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC));
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * 【静的ヘルパー】他クラスからサイコフレームの所持状態とNBTデータを安全に読み出すためのメソッド
     */
    public static ItemStack getPsychoFrame(Player player) {
        // 1. Curiosスロットを優先的にスキャン
        var curiosOpt = CuriosApi.getCuriosInventory(player);
        if (curiosOpt.isPresent()) {
            var handler = curiosOpt.resolve().get().getEquippedCurios();
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem() instanceof PsychoFrameItem) {
                    return stack;
                }
            }
        }
        // 2. 通常インベントリ内をスキャン
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof PsychoFrameItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CompoundTag tag = stack.getOrCreateTag();

        if (player.isShiftKeyDown()) {
            // 弾速切り替え (x1.0 ～ x8.0)
            float speed = tag.getFloat("ProjectileSpeed");
            if (speed < 1.0f) speed = 1.0f;
            else if (speed >= 100.0f) speed = 1.0f;
            else speed *= 2.0f;

            tag.putFloat("ProjectileSpeed", speed);
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("サイコミュ出力：").append(Component.literal("x" + speed).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)), true);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.5f);
            }
        } else {
            // 索敵範囲切り替え (32mから倍々で最大1024m)
            int currentRange = tag.getInt("SearchRange");
            int nextRange;
            if (currentRange < 32) nextRange = 32;
            else if (currentRange >= 1024) nextRange = 32;
            else nextRange = currentRange * 2;

            tag.putInt("SearchRange", nextRange);
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("索敵限界：").append(Component.literal(nextRange + "m").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)), true);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.PLAYERS, 0.6f, 1.2f);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @SubscribeEvent
    public void onProjectileUpdate(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.level.isClientSide || !(event.level instanceof ServerLevel currentLevel)) return;

        AABB scanArea = new AABB(-20000, -64, -20000, 20000, 320, 20000);
        event.level.getEntitiesOfClass(Projectile.class, scanArea,
                        proj -> proj.isAlive() && proj.getOwner() instanceof Player shooter &&
                                CuriosApi.getCuriosHelper().findFirstCurio(shooter, this).isPresent())
                .forEach(proj -> {
                    Player shooter = (Player) proj.getOwner();
                    CompoundTag nbt = proj.getPersistentData();
                    ItemStack frame = CuriosApi.getCuriosHelper().findFirstCurio(shooter, this).get().stack();

                    float configSpeed = frame.getOrCreateTag().getFloat("ProjectileSpeed");
                    if (configSpeed <= 0) configSpeed = 2.0f;

                    LivingEntity target = null;

                    if (nbt.hasUUID("TargetUUID")) {
                        UUID targetId = nbt.getUUID("TargetUUID");
                        for (ServerLevel world : currentLevel.getServer().getAllLevels()) {
                            Entity e = world.getEntity(targetId);
                            if (e instanceof LivingEntity le && le.isAlive()) {
                                target = le;
                                if (world.dimension() != currentLevel.dimension()) {
                                    crossDimensionWarp(proj, world, target);
                                    return;
                                }
                                break;
                            }
                        }
                    }

                    if (target == null) {
                        double range = frame.getOrCreateTag().getInt("SearchRange");
                        if (range <= 0) range = 32;

                        target = currentLevel.getEntitiesOfClass(LivingEntity.class, proj.getBoundingBox().inflate(range),
                                        e -> e != shooter && e.isAlive())
                                .stream()
                                .min(Comparator.comparingDouble(e -> e.distanceToSqr(proj)))
                                .orElse(null);

                        if (target != null) {
                            nbt.putUUID("TargetUUID", target.getUUID());
                            performWarp(proj, target, configSpeed);
                        }
                    } else {
                        double distance = proj.position().distanceTo(target.position());
                        if (distance > 5.0) {
                            performWarp(proj, target, configSpeed);
                        } else {
                            followTarget(proj, target, configSpeed);
                        }
                    }
                });
    }

    private void crossDimensionWarp(Projectile proj, ServerLevel targetWorld, LivingEntity target) {
        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        proj.changeDimension(targetWorld, new net.minecraftforge.common.util.ITeleporter() {
            @Override
            public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw, java.util.function.Function<Boolean, Entity> repositionEntity) {
                Entity placedEntity = repositionEntity.apply(false);
                placedEntity.teleportTo(targetPos.x, targetPos.y, targetPos.z);
                return placedEntity;
            }
        });
    }

    private void performWarp(Projectile proj, LivingEntity target, float speed) {
        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 dir = proj.getDeltaMovement().normalize();
        if (dir.lengthSqr() < 0.01) dir = proj.getForward();

        Vec3 warpPos = targetPos.subtract(dir.scale(1.5));

        proj.setPos(warpPos.x, warpPos.y, warpPos.z);
        proj.setDeltaMovement(dir.scale(speed));

        if (proj.level() instanceof ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, proj.getX(), proj.getY(), proj.getZ(), 5, 0.1, 0.1, 0.1, 0.05);
        }
    }

    private void followTarget(Projectile proj, LivingEntity target, float speed) {
        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 toTarget = targetPos.subtract(proj.position()).normalize();
        proj.setDeltaMovement(toTarget.scale(speed));
        proj.setNoGravity(true);
        proj.setYRot((float) (Math.atan2(toTarget.x, toTarget.z) * (180 / Math.PI)));
        proj.setXRot((float) (Math.atan2(-toTarget.y, toTarget.horizontalDistance()) * (180 / Math.PI)));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getOrCreateTag();
        int range = tag.getInt("SearchRange");
        float speed = tag.getFloat("ProjectileSpeed");
        if (range <= 0) range = 32;
        if (speed <= 0) speed = 2.0f;

        tooltip.add(Component.literal("Skull Heart Project - 空間転送式サイコミュ").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        tooltip.add(Component.literal("索敵システム：").append(Component.literal(range + "m").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));
        tooltip.add(Component.literal("推進スラスター：").append(Component.literal("x" + speed).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        tooltip.add(Component.literal(" "));
        tooltip.add(Component.literal("右クリック: 索敵範囲拡張 (最大1024m)").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Shift+rightクリック: 弾速（追従性）変更").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§7[同調] ファンネルシステムとサイコミュ共鳴可能").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("※ 異次元を含む全領域のターゲットを捕捉可能").withStyle(ChatFormatting.AQUA));
    }
}