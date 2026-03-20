package saga.skullheart.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.RenderType;
import com.finderfeed.fdlib.util.rendering.FDRenderUtil;

public class ParticleAcceleratorModel extends Model {
    public ParticleAcceleratorModel() {
        super(RenderType::entityCutoutNoCull);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, float r, float g, float b, float a) {}

    public void applySmoothRotation(PoseStack poseStack) {
        // ゲーム内の経過時間 + パーシャルティック（描画フレーム間の補間値）を取得
        // これにより20TPSの壁を超えてモニターのHz（60, 144等）で動きます
        float partialTick = FDRenderUtil.tryGetPartialTickIgnorePause();

        // 実行時間ベースの回転（15秒で1周）
        long time = System.currentTimeMillis();
        float speed = 15000.0f;

        // 角度を計算
        float degrees = ((time % (long)speed) / speed * 360.0f);

        // FDRenderUtilの回転メソッドを使用して適用
        poseStack.mulPose(FDRenderUtil.rotationDegrees(FDRenderUtil.ZP(), degrees));
    }
}