package saga.skullheart.events;

import com.tacz.guns.api.item.IGun;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import saga.skullheart.Skullheart;
import saga.skullheart.item.GNParticlesItem;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AnvilEventHandler {

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();   // 改造したい銃
        ItemStack right = event.getRight(); // 素材（GN粒子）

        // 左がTaCZの銃で、右がGN粒子のとき
        if (left.getItem() instanceof IGun && right.is(Skullheart.GN_PARTICLES.get())) {
            ItemStack result = left.copy();

            // 「GNバレット」エンチャントを付与
            result.enchant(Skullheart.GN_BULLET.get(), 1);

            event.setOutput(result);
            event.setCost(30);   // 合成コスト
            event.setMaterialCost(1); // 粒子を1個消費
        }
    }
}