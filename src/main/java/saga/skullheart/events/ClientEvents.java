package saga.skullheart.events;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import saga.skullheart.Skullheart;
import saga.skullheart.init.ModKeyBindings;
import saga.skullheart.network.NetworkHandler;
import saga.skullheart.network.SwitchModePacket;

@Mod.EventBusSubscriber(modid = Skullheart.MODID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // モード切替キーが押されたら
        if (ModKeyBindings.SWITCH_MODE.consumeClick()) {
            NetworkHandler.INSTANCE.sendToServer(new SwitchModePacket());
        }
    }
}