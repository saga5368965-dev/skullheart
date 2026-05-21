package saga.skullheart.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import saga.skullheart.entity.FunnelTurretEntity;
import saga.skullheart.world.inventory.FunnelContainerMenu;
import saga.skullheart.Skullheart;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 浮遊砲台コントローラー - Curios「手」スロット装備方式
 * 54スロット大容量コンテナ対応版
 */
public class FunnelTurretItem extends Item implements ICurioItem {

    private static final int MAX_TURRETS = 54;
    private static final double DEPLOY_RANGE = 3.0;
    // ⭕ Menu側の拡張に合わせて CONTAINER_SIZE を 10 から 54 へ変更してズレを解消！
    private static final int CONTAINER_SIZE = 54;
    private static final int CHECK_INTERVAL = 20;

    public FunnelTurretItem(Properties properties) {
        super(properties.stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC));
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * Curios装備中、手ぶらや他のアイテムを持っていてもShift+右クリックを検知できるようにする
     */
    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || event.getHand() != InteractionHand.MAIN_HAND) return;

        // Shiftが押されている時のみ処理（モード切り替え）
        if (player.isShiftKeyDown()) {
            ItemStack curiosStack = getCuriosStack(player);
            // Curiosスロット、またはメインハンド自身にこのコントローラーがある場合に実行
            if (!curiosStack.isEmpty() || player.getMainHandItem().getItem() instanceof FunnelTurretItem) {
                cycleMode(player);
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                event.setCanceled(true); // 通常の右クリック処理をキャンセル
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 手持ち状態でのShift+右クリックは上の@SubscribeEventで処理するため、ここではGUIを開く通常右クリックのみ処理
        if (!player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                openGui(serverPlayer, stack);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        return InteractionResultHolder.pass(stack);
    }

    private void openGui(ServerPlayer player, ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        // コンテナの枠を54個で初期化
        SimpleContainer container = new SimpleContainer(CONTAINER_SIZE);

        if (tag.contains("StoredGunsList")) {
            ListTag listTag = tag.getList("StoredGunsList", 10);
            for (int i = 0; i < listTag.size(); i++) {
                CompoundTag itemTag = listTag.getCompound(i);
                int slot = itemTag.getByte("Slot");
                // 上限チェックも54スロット未満(CONTAINER_SIZE)になるように自動同期
                if (slot >= 0 && slot < CONTAINER_SIZE) {
                    container.setItem(slot, ItemStack.of(itemTag));
                }
            }
        }

        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (containerId, playerInventory, p) -> new FunnelContainerMenu(containerId, playerInventory, container, stack),
                Component.translatable("container.skullheart.funnel_tacz")
        ), buf -> {});
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player player && !slotContext.entity().level().isClientSide) {
            player.displayClientMessage(
                    Component.translatable("msg.skullheart.funnel_turret.system_startup").withStyle(ChatFormatting.GREEN),
                    true
            );
            deployAllTurrets(player, stack);
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player player && !slotContext.entity().level().isClientSide) {
            removeAllTurrets(player);
            player.displayClientMessage(
                    Component.translatable("msg.skullheart.funnel_turret.system_shutdown").withStyle(ChatFormatting.RED),
                    true
            );
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;

        Player player = event.player;
        ItemStack controllerStack = getCuriosStack(player);
        if (controllerStack.isEmpty()) return;

        if (player.tickCount % CHECK_INTERVAL == 0) {
            int active = countActiveTurrets(player);
            if (active < MAX_TURRETS) {
                deployAllTurrets(player, controllerStack);
            }
        }
    }

    private ItemStack getCuriosStack(Player player) {
        var optional = CuriosApi.getCuriosInventory(player);
        if (optional.isPresent()) {
            var curios = optional.resolve().get();
            var stacksHandler = curios.getStacksHandler("hand");
            if (stacksHandler.isPresent()) {
                var handler = stacksHandler.get().getStacks();
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (!stack.isEmpty() && stack.getItem() instanceof FunnelTurretItem) {
                        return stack;
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private void deployAllTurrets(Player player, ItemStack controllerStack) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        CompoundTag tag = controllerStack.getOrCreateTag();
        List<ItemStack> availableGuns = getAvailableGuns(tag);

        if (availableGuns.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("msg.skullheart.funnel_turret.no_guns_loaded").withStyle(ChatFormatting.RED),
                    true
            );
            return;
        }

        int currentActive = countActiveTurrets(player);
        int needed = MAX_TURRETS - currentActive;
        if (needed <= 0) return;

        int deployed = 0;
        for (int i = 0; i < needed && i < availableGuns.size(); i++) {
            ItemStack gunItem = availableGuns.get(i);
            int index = currentActive + deployed;

            double angle = (index * 2 * Math.PI / MAX_TURRETS);
            double radius = DEPLOY_RANGE + (index / (double) MAX_TURRETS) * 1.5;
            double xOffset = Math.cos(angle) * radius;
            double zOffset = Math.sin(angle) * radius;

            FunnelTurretEntity turret = Skullheart.FUNNEL_TURRET_ENTITY.get().create(serverLevel);
            if (turret != null) {
                turret.setOwner(player);
                turret.setTurretIndex(index);
                turret.setTacticMode(tag.getInt("Mode"));
                turret.setPos(player.getX() + xOffset, player.getY() + 1.2, player.getZ() + zOffset);
                turret.setYRot(player.getYRot());
                turret.setXRot(0);
                turret.setStoredItem(gunItem.copy());

                if (serverLevel.addFreshEntity(turret)) {
                    deployed++;
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                            turret.getX(), turret.getY(), turret.getZ(), 15, 0.2, 0.2, 0.2, 0.4);
                }
            }
        }

        if (deployed > 0) {
            player.displayClientMessage(
                    Component.translatable("msg.skullheart.funnel_turret.deployed", deployed, currentActive + deployed, MAX_TURRETS)
                            .withStyle(ChatFormatting.GREEN),
                    true
            );
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.8F);
        }
    }

    private List<ItemStack> getAvailableGuns(CompoundTag tag) {
        List<ItemStack> guns = new ArrayList<>();
        if (tag.contains("StoredGunsList")) {
            ListTag listTag = tag.getList("StoredGunsList", 10);
            for (int i = 0; i < listTag.size(); i++) {
                ItemStack gun = ItemStack.of(listTag.getCompound(i));
                if (!gun.isEmpty()) {
                    guns.add(gun);
                }
            }
        }
        return guns;
    }

    private void removeAllTurrets(Player player) {
        for (var entity : player.level().getEntitiesOfClass(FunnelTurretEntity.class, player.getBoundingBox().inflate(100))) {
            if (player.getUUID().equals(entity.getOwnerUUID())) {
                entity.discard();
            }
        }
    }

    private int countActiveTurrets(Player player) {
        int count = 0;
        for (var entity : player.level().getEntitiesOfClass(FunnelTurretEntity.class, player.getBoundingBox().inflate(100))) {
            if (entity.getOwnerUUID() != null && entity.getOwnerUUID().equals(player.getUUID())) {
                count++;
            }
        }
        return count;
    }

    public static void cycleMode(Player player) {
        Skullheart.LOGGER.info("cycleMode called for player: " + player.getName().getString());

        if (player.level().isClientSide) return;

        // 手持ち状態とCurios装備状態の両方に対応させて確実にNBTタグを取得
        ItemStack stack = getEquippedController(player);
        if (stack.isEmpty() && player.getMainHandItem().getItem() instanceof FunnelTurretItem) {
            stack = player.getMainHandItem();
        }

        if (stack.isEmpty()) {
            Skullheart.LOGGER.warn("No controller found in curios or main hand!");
            return;
        }

        CompoundTag tag = stack.getOrCreateTag();
        int mode = (tag.getInt("Mode") + 1) % 4;
        tag.putInt("Mode", mode);
        Skullheart.LOGGER.info("Mode changed to: " + mode);

        int turretCount = 0;
        for (var entity : player.level().getEntitiesOfClass(FunnelTurretEntity.class, player.getBoundingBox().inflate(100))) {
            if (player.getUUID().equals(entity.getOwnerUUID())) {
                entity.setTacticMode(mode);
                turretCount++;
            }
        }
        Skullheart.LOGGER.info("Updated " + turretCount + " turrets to mode " + mode);

        String modeName = switch (mode) {
            case 0 -> "msg.skullheart.funnel_turret.mode.0";
            case 1 -> "msg.skullheart.funnel_turret.mode.1";
            case 2 -> "msg.skullheart.funnel_turret.mode.2";
            case 3 -> "msg.skullheart.funnel_turret.mode.3";
            default -> "msg.skullheart.funnel_turret.mode.0";
        };
        player.displayClientMessage(
                Component.translatable("msg.skullheart.funnel_turret.mode_switched", Component.translatable(modeName))
                        .withStyle(ChatFormatting.AQUA),
                true
        );
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.5F);
    }

    private static ItemStack getEquippedController(Player player) {
        var optional = CuriosApi.getCuriosInventory(player);
        if (optional.isPresent()) {
            var curios = optional.resolve().get();
            var stacksHandler = curios.getStacksHandler("hand");
            if (stacksHandler.isPresent()) {
                var handler = stacksHandler.get().getStacks();
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (!stack.isEmpty() && stack.getItem() instanceof FunnelTurretItem) {
                        return stack;
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        int mode = tag != null ? tag.getInt("Mode") : 0;
        String modeName = switch (mode) {
            case 0 -> "msg.skullheart.funnel_turret.mode.0";
            case 1 -> "msg.skullheart.funnel_turret.mode.1";
            case 2 -> "msg.skullheart.funnel_turret.mode.2";
            case 3 -> "msg.skullheart.funnel_turret.mode.3";
            default -> "msg.skullheart.funnel_turret.mode.0";
        };

        tooltip.add(Component.translatable("tooltip.skullheart.funnel_turret.curios_equipped").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.skullheart.funnel_turret.current_mode", Component.translatable(modeName)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.translatable("tooltip.skullheart.funnel_turret.right_click").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.skullheart.funnel_turret.shift_right_click").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.translatable("tooltip.skullheart.funnel_turret.auto_deploy").withStyle(ChatFormatting.GRAY));
    }
}