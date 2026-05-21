package saga.skullheart.init;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import saga.skullheart.Skullheart;
import saga.skullheart.world.inventory.FunnelContainerMenu;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, Skullheart.MODID);

    // ✅ 修正完了 - ラムダ式で明示的にコンストラクタのシグネチャを指定
    public static final RegistryObject<MenuType<FunnelContainerMenu>> FUNNEL_MENU =
            MENUS.register("funnel_menu", () -> IForgeMenuType.create(
                    (int windowId, Inventory inv, FriendlyByteBuf data) ->
                            new FunnelContainerMenu(windowId, inv, data)
            ));
}