package saga.skullheart.client.renderer;

import com.finderfeed.fdlib.systems.bedrock.models.FDModel;
import com.finderfeed.fdlib.systems.bedrock.models.FDModelInfo;
import com.finderfeed.fdlib.util.rendering.FDRenderUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import saga.skullheart.Skullheart;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Optional;

public class ParticleAcceleratorRenderer extends BlockEntityWithoutLevelRenderer implements ICurioRenderer {
    private FDModel model;
    private static final Gson GSON = new Gson();
    private static final ResourceLocation GEO_PATH = new ResourceLocation(Skullheart.MODID, "particle_accelerator");
    private static final ResourceLocation TEXTURE = new ResourceLocation(Skullheart.MODID, "textures/item/particle_accelerator.png");

    public ParticleAcceleratorRenderer() {
        super(null, null);
    }

    private void ensureModel() {
        if (this.model == null) {
            try {
                var manager = Minecraft.getInstance().getResourceManager();
                ResourceLocation fullPath = new ResourceLocation(GEO_PATH.getNamespace(), "bedrock/models/" + GEO_PATH.getPath() + ".geo.json");
                Optional<Resource> resource = manager.getResource(fullPath);

                if (resource.isPresent()) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.get().open()))) {
                        JsonObject json = GSON.fromJson(reader, JsonObject.class);
                        FDModelInfo info = new FDModelInfo(GEO_PATH, 1.0f);
                        info.load(json);
                        this.model = new FDModel(info);
                    }
                }
            } catch (Exception e) {}
        }
    }

    // --- Curios 描画用 (背中 -> 肩への固定) ---
    @Override
    public <T extends LivingEntity, M extends net.minecraft.client.model.EntityModel<T>> void render(
            ItemStack stack, SlotContext slotContext, PoseStack poseStack,
            net.minecraft.client.renderer.entity.RenderLayerParent<T, M> renderLayerParent,
            MultiBufferSource renderTypeBuffer, int light, float limbSwing, float limbSwingAmount,
            float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

        ensureModel();
        if (this.model != null) {
            poseStack.pushPose();

            ICurioRenderer.translateIfSneaking(poseStack, slotContext.entity());
            ICurioRenderer.rotateIfSneaking(poseStack, slotContext.entity());

            // 1. 位置調整 (微調整しました)
            // 肩幅を広げると中心がズレて見えることがあるので、X(0.043)はそのままで様子見
            poseStack.translate(-0.25, 1.7, 0.5);

            // 2. 回転
            poseStack.mulPose(FDRenderUtil.rotationDegrees(FDRenderUtil.XP(), 180f));
            poseStack.mulPose(FDRenderUtil.rotationDegrees(FDRenderUtil.YP(), 90f));

            // 3. 【ここが重要！】肩幅を広げるためのスケール調整
            // 第1引数(X)を 0.85 から 1.1 にアップ。これで左右に広がります。
            // もし広がりすぎたら 1.05、足りなければ 1.2 と調整してください。
            poseStack.scale(1f, 1f, 1f);

            renderCommon(stack, poseStack, renderTypeBuffer, light);

            poseStack.popPose();
        }
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        // インベントリ内は2Dアイコンなので描画しない
    }

    private void renderCommon(ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        // --- 回転処理を完全に削除 ---
        // ザンネックの肩パーツは回転しないので、mulPose(ZP, degrees) は不要です。

        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.model.render(poseStack, vc, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
    }
}