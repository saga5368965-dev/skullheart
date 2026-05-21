package saga.skullheart.world.inventory;

import com.tacz.guns.item.ModernKineticGunItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import saga.skullheart.Skullheart;

public class FunnelContainerMenu extends AbstractContainerMenu {

    // ラージチェスト1個分の容量（9列×6行 = 54スロット）へ拡張 [cite: 2026-04-12]
    private static final int CONTAINER_SIZE = 54;
    private final Container funnelInventory;
    private final ItemStack containerStack;

    // 2引数コンストラクタ
    public FunnelContainerMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(CONTAINER_SIZE), playerInventory.player.getMainHandItem());
    }

    // 3引数コンストラクタ（ネットワーク通信用）
    public FunnelContainerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, new SimpleContainer(CONTAINER_SIZE), playerInventory.player.getMainHandItem());
    }

    // サーバー側（実処理用）- 4引数コンストラクタ
    public FunnelContainerMenu(int containerId, Inventory playerInventory, Container funnelInventory, ItemStack containerStack) {
        super(Skullheart.FUNNEL_CONTAINER_MENU.get(), containerId);
        checkContainerSize(funnelInventory, CONTAINER_SIZE);
        this.funnelInventory = funnelInventory;
        this.containerStack = containerStack;

        // 1. ファンネルの銃格納スロット (9列×6行 = 計54スロットをバニラのラージチェスト枠へ正確に配置) [cite: 2026-03-03]
        int slotIndex = 0;
        for (int row = 0; row < 6; ++row) {
            for (int col = 0; col < 9; ++col) {
                // X軸開始を8、Y軸開始を18に設定してラージチェスト枠にジャストフィットさせます [cite: 2026-04-12]
                this.addSlot(new Slot(funnelInventory, slotIndex++, 8 + col * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return stack.getItem() instanceof ModernKineticGunItem;
                    }

                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                });
            }
        }

        // 2. プレイヤーのメインインベントリ (Y座標を140まで下げて重なりを完全回避)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }

        // 3. プレイヤーのホットバー (Y座標を198に設定)
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 198) {
                @Override
                public boolean mayPickup(Player player) {
                    return !ItemStack.matches(this.getItem(), containerStack);
                }
            });
        }
    }

    @Override
    public void removed(Player player) {
        // コンテナを閉じた時に54スロットすべてのアイテム状態をコントローラーのNBTへ安全に一括セーブ [cite: 2026-04-10]
        if (!player.level().isClientSide && containerStack != null && !containerStack.isEmpty()) {
            net.minecraft.nbt.ListTag listTag = new net.minecraft.nbt.ListTag();
            for (int i = 0; i < funnelInventory.getContainerSize(); i++) {
                ItemStack stack = funnelInventory.getItem(i);
                if (!stack.isEmpty()) {
                    net.minecraft.nbt.CompoundTag itemTag = new net.minecraft.nbt.CompoundTag();
                    itemTag.putByte("Slot", (byte) i);
                    stack.save(itemTag);
                    listTag.add(itemTag);
                }
            }
            if (!listTag.isEmpty()) {
                containerStack.getOrCreateTag().put("StoredGunsList", listTag);
            } else {
                containerStack.getOrCreateTag().remove("StoredGunsList");
            }
        }
        super.removed(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemStack = slotStack.copy();

            // スロット0〜53（ファンネルインベントリ）からのシフトクリック移動 [cite: 2026-04-12]
            if (index < CONTAINER_SIZE) {
                if (!this.moveItemStackTo(slotStack, CONTAINER_SIZE, CONTAINER_SIZE + 36, true)) {
                    return ItemStack.EMPTY;
                }
            }
            // プレイヤーインベントリからファンネルの空きスロットへ移動
            else {
                if (slotStack.getItem() instanceof ModernKineticGunItem) {
                    if (!this.moveItemStackTo(slotStack, 0, CONTAINER_SIZE, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemStack;
    }

    @Override
    public boolean stillValid(Player player) {
        return !this.containerStack.isEmpty() && player.getMainHandItem() == this.containerStack;
    }
}