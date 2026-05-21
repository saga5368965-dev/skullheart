package saga.skullheart.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import saga.skullheart.world.inventory.FunnelContainerMenu;

/**
 * ファンネルコントローラー専用装填GUI - テクスチャレス・SFダークグリーン仕様（54スロット対応版）
 */
public class FunnelContainerScreen extends AbstractContainerScreen<FunnelContainerMenu> {

    public FunnelContainerScreen(FunnelContainerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        // ラージチェスト規格のサイズ調整（幅176、縦222マス）
        this.imageWidth = 176;
        this.imageHeight = 222;

        // テキストの配置位置調整
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        // プレイヤーインベントリの開始Y座標(140)の少し上（128マス目）に「インベントリ」の文字を綺麗に配置
        this.inventoryLabelY = 128;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // 1. 背景：サイコフレームやGN粒子を彷彿とさせる、深い緑の近未来グラデーション
        graphics.fillGradient(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF0A1F0A, 0xFF051005);

        // 2. メインウィンドウの外枠（サイバーグリーン）
        graphics.renderOutline(x, y, this.imageWidth, this.imageHeight, 0xFF1B4D1B);

        // 3. メニュー側のスロット位置（Menuクラスの登録データ）から正確に枠組みを自動生成！
        // Menu側でサイズを54に変更したため、ループ処理で自動的にすべての枠（54 + 27 + 9 = 計90個）がズレなく描画されます
        for (int i = 0; i < this.menu.slots.size(); i++) {
            var slot = this.menu.slots.get(i);
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;

            // スロットの縁取り（ダークグリーン）
            graphics.renderOutline(sx, sy, 18, 18, 0xFF1B4D1B);
            // スロット内部をさらに暗くして、セットしたTaCZの銃やインベントリのアイテムを際立たせる
            graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF020802);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // 背景のトーンダウン
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);
        // ホバーしたアイテムのツールチップを正常に描画
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}