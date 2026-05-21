package saga.skullheart.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import saga.skullheart.entity.FunnelTurretEntity;

/**
 * 浮遊砲台のレンダラー - 格納されているTaCZの銃をそのまま空間に立体描画する
 */
public class FunnelTurretRenderer extends EntityRenderer<FunnelTurretEntity> {

    public FunnelTurretRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.3f;
    }

    @Override
    public void render(FunnelTurretEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {

        ItemStack gunStack = entity.getStoredItem();
        if (gunStack.isEmpty()) {
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }

        poseStack.pushPose();

        // 1. エンティティの現在の回転（ターゲットへの注視）を適用
        float interpolatedYaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        float interpolatedPitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        // マイクラのItem描画の基準に合わせるための回転調整
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - interpolatedYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-interpolatedPitch));

        // 2. ビットっぽい微細な上下浮遊アニメーション（なめらかな動きを補完） [cite: 2026-03-03]
        double bob = Math.sin((entity.level().getGameTime() + partialTick) * 0.1F) * 0.05F;
        poseStack.translate(0.0D, bob, 0.0D);

        // 3. 発射時の反動（リコイル）アニメーション
        if (entity.getFireCharge() > 0) {
            float recoil = (float) entity.getFireCharge() * 0.15f;
            poseStack.translate(0.0D, 0.0D, -recoil); // 後ろにキックバック
        }

        // 4. サイズ調整 (TaCZの銃モデルをファンネルサイズにする適正スケール)
        poseStack.scale(0.8f, 0.8f, 0.8f);

        // 5. 本物のTaCZ銃モデルをレンダリング
        // ItemDisplayContext.NONE または THIRD_PERSON_RIGHT_HAND を使うことで、MOD独自の3Dモデルがそのまま引き出せます
        Minecraft.getInstance().getItemRenderer().renderStatic(
                gunStack,
                ItemDisplayContext.NONE,
                packedLight,
                OverlayTexture.NO_OVERLAY,  // ✅ これで正しく解決される
                poseStack,
                buffer,
                entity.level(),
                entity.getId()
        );

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(FunnelTurretEntity entity) {
        // アイテムレンダラーに委託するため、デフォルトのアトラスを返す
        return TextureAtlas.LOCATION_BLOCKS;
    }
}