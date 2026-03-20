package saga.skullheart.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.Comparator;
import java.util.List;

public class PsychoFrameItem extends Item {

    public PsychoFrameItem(Properties properties) {
        super(properties.stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC));
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onProjectileSpawn(EntityJoinLevelEvent event) {
        // クライアント側、または既に除去されているエンティティは無視
        if (event.getLevel().isClientSide || event.getEntity().isRemoved()) return;

        if (event.getEntity() instanceof Projectile proj) {
            // 所有者がいない弾（トラップなど）でのクラッシュを防ぐ
            if (!(proj.getOwner() instanceof LivingEntity shooter)) return;

            // Curiosの装備チェックを安全に行う
            CuriosApi.getCuriosHelper().findFirstCurio(shooter, this).ifPresent(slotResult -> {

                // 索敵範囲
                double range = 128.0;
                AABB searchArea = shooter.getBoundingBox().inflate(range);

                // ターゲットの選定
                LivingEntity target = proj.level().getEntitiesOfClass(LivingEntity.class, searchArea,
                                e -> e != shooter && e.isAlive() && shooter.hasLineOfSight(e))
                        .stream()
                        .min(Comparator.comparingDouble(e -> e.distanceToSqr(shooter)))
                        .orElse(null);

                // --- ここが重要：ターゲットが見つかった場合のみワープ ---
                if (target != null) {
                    try {
                        // ターゲットの少し上（胴体）を狙う
                        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.7, 0);

                        // 1. 位置を移動（ワープ）
                        proj.setPos(targetPos.x, targetPos.y, targetPos.z);

                        // 2. 弾道を真下に固定し、即座に当たり判定を発生させる
                        // TaCZの弾などは速度がないと消える場合があるため、微弱な速度を与える
                        proj.setDeltaMovement(new Vec3(0, -0.1, 0));

                        // 3. 演出
                        proj.level().playSound(null, shooter.blockPosition(),
                                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4F, 2.0F);
                    } catch (Exception e) {
                        // 万が一の座標計算エラー時もクラッシュさせない
                        // そのまま通常の弾道で飛ばす
                    }
                }
                // target == null の場合は何も処理しないため、
                // 弾はそのままプレイヤーが撃った方向へ通常通り飛んでいく
            });
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skullheart.psycho_frame.desc").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.skullheart.psycho_frame.usage").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
