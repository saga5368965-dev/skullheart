package saga.skullheart.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;
import saga.skullheart.client.renderer.ParticleAcceleratorRenderer;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;
import java.util.function.Consumer;

public class ParticleAcceleratorItem extends Item {

    private static final int MAX_BOOST = 500;

    public ParticleAcceleratorItem(Properties properties) {
        super(properties.stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC));
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * 【静的ヘルパー】他クラスからザンネック・ハローの所持状態とブースト強度を安全に取得するためのメソッド
     */
    public static ItemStack getAccelerator(Player player) {
        var curiosOpt = CuriosApi.getCuriosInventory(player);
        if (curiosOpt.isPresent()) {
            var handler = curiosOpt.resolve().get().getEquippedCurios();
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem() instanceof ParticleAcceleratorItem) {
                    return stack;
                }
            }
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ParticleAcceleratorItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CompoundTag tag = stack.getOrCreateTag();
        int mode = tag.getInt("EditMode");
        int currentBoost = tag.getInt("BoostLevel");

        if (player.isShiftKeyDown()) {
            mode = (mode + 1) % 6;
            tag.putInt("EditMode", mode);
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("ハロー・出力同期：").append(Component.literal(getModeName(mode)).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)), true);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.5F);
            }
        } else {
            int amount = getAmountByMode(mode);
            if (amount == -1) {
                tag.putInt("BoostLevel", MAX_BOOST);
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.literal("【警告】最大広域制圧：リミッター全解除").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 2.0F);
                }
            } else {
                int nextBoost = Math.min(currentBoost + amount, MAX_BOOST);
                tag.putInt("BoostLevel", nextBoost);
                if (!level.isClientSide) {
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.8F, 0.5F + (nextBoost / 500f));
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private String getModeName(int mode) {
        return switch (mode) { case 0 -> "+1段階"; case 1 -> "+10段階"; case 2 -> "+50段階"; case 3 -> "+100段階"; case 4 -> "MAX出力"; default -> "待機"; };
    }

    private int getAmountByMode(int mode) {
        return switch (mode) { case 0 -> 1; case 1 -> 10; case 2 -> 50; case 3 -> 100; case 4 -> -1; default -> 0; };
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.level.isClientSide) return;

        AABB scanArea = new AABB(-20000, -64, -20000, 20000, 320, 20000);
        event.level.getEntitiesOfClass(Projectile.class, scanArea, proj -> proj.isAlive() && proj.getOwner() instanceof Player).forEach(proj -> {
            Player shooter = (Player) proj.getOwner();

            CuriosApi.getCuriosHelper().findFirstCurio(shooter, this).ifPresent(slotResult -> {
                int boost = slotResult.stack().getOrCreateTag().getInt("BoostLevel");
                if (boost > 0) {
                    applyParticleBoostToProjectile(proj, shooter, boost);
                }
            });
        });
    }

    private void applyParticleBoostToProjectile(Projectile p, Player shooter, int boost) {
        p.setNoGravity(true);
        p.noPhysics = true;

        double hitRadius = 1.0D + boost;
        AABB hitArea = p.getBoundingBox().inflate(hitRadius);
        float damage = (boost >= MAX_BOOST) ? Float.MAX_VALUE : (boost * 200.0F);

        p.level().getEntitiesOfClass(LivingEntity.class, hitArea, e -> e != shooter && e.isAlive())
                .forEach(target -> {
                    target.hurt(p.damageSources().indirectMagic(p, shooter), damage);
                    target.invulnerableTime = 0;
                });

        if (p.tickCount % 5 == 0) {
            var type = (boost >= 250) ? net.minecraft.core.particles.ParticleTypes.SONIC_BOOM : net.minecraft.core.particles.ParticleTypes.END_ROD;
            p.level().addParticle(type, p.getX(), p.getY(), p.getZ(), 0, 0, 0);
        }
    }

    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getProjectile().getOwner() instanceof Player player) {
            CuriosApi.getCuriosHelper().findFirstCurio(player, this).ifPresent(s -> event.setCanceled(true));
        }
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private ParticleAcceleratorRenderer renderer;
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) this.renderer = new ParticleAcceleratorRenderer();
                return this.renderer;
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Skull Heart Project - ザンネック・ハロー").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            int boost = tag.getInt("BoostLevel");
            int mode = tag.getInt("EditMode");
            int range = 1 + boost;
            tooltip.add(Component.literal("出力設定: ").append(Component.literal(getModeName(mode)).withStyle(ChatFormatting.AQUA)));
            tooltip.add(Component.literal("空間干渉半径: ").append(Component.literal(range + "m").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
            if (boost >= MAX_BOOST) tooltip.add(Component.literal("【警告】全域焦土化リミッター解除済").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            else tooltip.add(Component.literal("推定威力: ").append(Component.literal((boost * 200) + " /tick").withStyle(ChatFormatting.RED)));
        }
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§7[同調] ファンネルシステムへ超高出力ミノフスキー粒子を供給可能").withStyle(ChatFormatting.DARK_GRAY));
    }
}