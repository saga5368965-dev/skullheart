package saga.skullheart.enchantment;

import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import saga.skullheart.Skullheart;


@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GNBulletEnchantment extends Enchantment {

    public GNBulletEnchantment() {
        // TaCZの銃に付与できるように設定
        super(Rarity.VERY_RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // サーバー側かつティックの開始時に処理
        if (event.phase != TickEvent.Phase.START || event.player.level().isClientSide) return;

        Player player = event.player;
        ItemStack stack = player.getMainHandItem();

        // 1. TaCZの銃かどうかをチェック
        IGun iGun = IGun.getIGunOrNull(stack);

        // 2. 銃であり、かつ「GNバレット」エンチャントが付いているか確認
        if (iGun != null && EnchantmentHelper.getItemEnchantmentLevel(Skullheart.GN_BULLET.get(), stack) > 0) {

            // --- 弾薬の無限充填ロジック ---
            // 現在の弾数が減っていたら、強制的に100発（固定値）に書き換える
            if (iGun.getCurrentAmmoCount(stack) < 99) {
                iGun.setCurrentAmmoCount(stack, 100);
            }

            // チャンバー（薬室）の中に常に弾がある状態にする（閉膛待撃の銃対策）
            if (!iGun.hasBulletInBarrel(stack)) {
                iGun.setBulletInBarrel(stack, true);
            }

            // --- 過熱（ヒートアップ）対策 ---
            // 銃に熱データがある場合、常に0にしてロックを解除する
            if (iGun.hasHeatData(stack)) {
                iGun.setHeatAmount(stack, 0.0F); // 熱量をゼロに
                if (iGun.isOverheatLocked(stack)) {
                    iGun.setOverheatLocked(stack, false); // 過熱ロックを強制解除
                }
            }

            // --- 仮想備弾の補填 ---
            // 特殊な銃で「ダミー弾薬」を使う場合も最大にする
            if (iGun.useDummyAmmo(stack)) {
                iGun.setDummyAmmoAmount(stack, 999);
            }
        }
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        // 基本的にTaCZの銃にのみエンチャントを許可する
        return stack.getItem() instanceof IGun;
    }
}