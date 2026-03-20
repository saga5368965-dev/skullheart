package saga.skullheart;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import saga.skullheart.client.renderer.ParticleAcceleratorRenderer;
import saga.skullheart.enchantment.GNBulletEnchantment;
import saga.skullheart.events.AnvilEventHandler;
import saga.skullheart.item.GNParticlesItem;
import saga.skullheart.item.ParticleAcceleratorItem;
import saga.skullheart.item.PsychoFrameItem; // 追加
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

@Mod(Skullheart.MODID)
public class Skullheart {

    public static final String MODID = "skullheart";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // --- アイテム登録 ---
    public static final RegistryObject<Item> PARTICLE_ACCELERATOR = ITEMS.register("particle_accelerator",
            () -> new ParticleAcceleratorItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> GN_PARTICLES = ITEMS.register("gn_particles",
            () -> new GNParticlesItem(new Item.Properties()));

    public static final RegistryObject<Item> PSYCHO_FRAME = ITEMS.register("psycho_frame",
            () -> new PsychoFrameItem(new Item.Properties())); // 追加

    // --- エンチャント登録 ---
    public static final RegistryObject<Enchantment> GN_BULLET = ENCHANTMENTS.register("gn_bullet",
            GNBulletEnchantment::new);

    // --- クリエイティブタブ登録 ---
    public static final RegistryObject<CreativeModeTab> SKULLHEART_TAB = CREATIVE_MODE_TABS.register("skullheart_tab", () -> CreativeModeTab.builder()
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> PARTICLE_ACCELERATOR.get().getDefaultInstance())
            .title(Component.translatable("itemGroup.skullheart"))
            .displayItems((parameters, output) -> {
                // 粒子加速器に初期NBTを付与してタブに登録
                ItemStack accel = new ItemStack(PARTICLE_ACCELERATOR.get());
                CompoundTag tag = accel.getOrCreateTag();
                tag.putInt("EditMode", 0);
                tag.putInt("BoostLevel", 0);
                output.accept(accel);

                output.accept(GN_PARTICLES.get());
                output.accept(PSYCHO_FRAME.get()); // サイコフレーム追加
            }).build());

    public Skullheart() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ITEMS.register(modEventBus);
        ENCHANTMENTS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(AnvilEventHandler.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("SKULL HEART MOD 初期化 - ザンネック & サイコミュ 同期中");
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                CuriosRendererRegistry.register(PARTICLE_ACCELERATOR.get(), ParticleAcceleratorRenderer::new);
                LOGGER.info("SKULL HEART CLIENT SETUP - システム同期完了");
            });
        }
    }
}