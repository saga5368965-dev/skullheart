package saga.skullheart.init;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import saga.skullheart.Skullheart;

@Mod.EventBusSubscriber(modid = Skullheart.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModKeyBindings {

    public static final String KEY_CATEGORY = "key.category.skullheart";
    public static final String KEY_SWITCH_MODE = "key.skullheart.switch_mode";

    public static KeyMapping SWITCH_MODE = new KeyMapping(
            KEY_SWITCH_MODE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            KEY_CATEGORY
    );

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(SWITCH_MODE);
        Skullheart.LOGGER.info("Key binding registered!");
    }
}