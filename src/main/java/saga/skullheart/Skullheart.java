package saga.skullheart;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
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
import saga.skullheart.entity.FunnelTurretEntity;
import saga.skullheart.events.AnvilEventHandler;
import saga.skullheart.item.FunnelTurretItem;
import saga.skullheart.item.GNParticlesItem;
import saga.skullheart.item.ParticleAcceleratorItem;
import saga.skullheart.item.PsychoFrameItem;
import saga.skullheart.network.NetworkHandler;
import saga.skullheart.world.inventory.FunnelContainerMenu;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

@Mod(Skullheart.MODID)
public class Skullheart {

    public static final String MODID = "skullheart";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);

    // --- アイテム登録 ---
    public static final RegistryObject<Item> PARTICLE_ACCELERATOR = ITEMS.register("particle_accelerator",
            () -> new ParticleAcceleratorItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> GN_PARTICLES = ITEMS.register("gn_particles",
            () -> new GNParticlesItem(new Item.Properties()));

    public static final RegistryObject<Item> PSYCHO_FRAME = ITEMS.register("psycho_frame",
            () -> new PsychoFrameItem(new Item.Properties()));

    public static final RegistryObject<Item> FUNNEL_TURRET = ITEMS.register("funnel_turret",
            () -> new FunnelTurretItem(new Item.Properties().stacksTo(1)));

    // --- エンティティ登録 ---
    public static final RegistryObject<EntityType<FunnelTurretEntity>> FUNNEL_TURRET_ENTITY = ENTITY_TYPES.register("funnel_turret",
            () -> EntityType.Builder.<FunnelTurretEntity>of(FunnelTurretEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .build(new ResourceLocation(MODID, "funnel_turret").toString()));

    // --- メニュー登録（修正: 3引数コンストラクタを使用）---
    public static final RegistryObject<MenuType<FunnelContainerMenu>> FUNNEL_CONTAINER_MENU = MENUS.register("funnel_container_menu",
            () -> IForgeMenuType.create((windowId, inv, data) -> new FunnelContainerMenu(windowId, inv, data)));

    // --- エンチャント登録 ---
    public static final RegistryObject<Enchantment> GN_BULLET = ENCHANTMENTS.register("gn_bullet",
            GNBulletEnchantment::new);

    // --- クリエイティブタブ登録 ---
    public static final RegistryObject<CreativeModeTab> SKULLHEART_TAB = CREATIVE_MODE_TABS.register("skullheart_tab", () -> CreativeModeTab.builder()
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> PARTICLE_ACCELERATOR.get().getDefaultInstance())
            .title(Component.translatable("itemGroup.skullheart"))
            .displayItems((parameters, output) -> {
                ItemStack accel = new ItemStack(PARTICLE_ACCELERATOR.get());
                CompoundTag tag = accel.getOrCreateTag();
                tag.putInt("EditMode", 0);
                tag.putInt("BoostLevel", 0);
                output.accept(accel);

                output.accept(GN_PARTICLES.get());
                output.accept(PSYCHO_FRAME.get());

                ItemStack turret = new ItemStack(FUNNEL_TURRET.get());
                CompoundTag turretTag = turret.getOrCreateTag();
                turretTag.putInt("Mode", 0);
                turretTag.putInt("DeployCount", 0);
                output.accept(turret);
            }).build());

    public Skullheart() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ITEMS.register(modEventBus);
        ENCHANTMENTS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        MENUS.register(modEventBus);
        NetworkHandler.register();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerAttributes);  // ✅ 属性登録イベントを追加

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(AnvilEventHandler.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("SKULL HEART MOD 初期化 - ザンネック & サイコミュ 同期中");
    }

    // ✅ エンティティの属性を登録（必須）
    private void registerAttributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(FUNNEL_TURRET_ENTITY.get(), FunnelTurretEntity.createAttributes().build());
        LOGGER.info("SKULL HEART - ファンネル属性を登録");
    }

    // クライアント専用イベントの登録クラス
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                CuriosRendererRegistry.register(PARTICLE_ACCELERATOR.get(), ParticleAcceleratorRenderer::new);

                net.minecraft.client.gui.screens.MenuScreens.register(
                        FUNNEL_CONTAINER_MENU.get(),
                        saga.skullheart.client.screen.FunnelContainerScreen::new
                );

                LOGGER.info("SKULL HEART CLIENT SETUP - システム同期完了");
            });
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(FUNNEL_TURRET_ENTITY.get(), saga.skullheart.client.renderer.FunnelTurretRenderer::new);
            LOGGER.info("SKULL HEART RENDERER - ファンネル描画システムオンライン");
        }
    }
}