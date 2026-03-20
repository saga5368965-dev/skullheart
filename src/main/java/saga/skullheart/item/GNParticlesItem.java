package saga.skullheart.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GNParticlesItem extends Item {
    public GNParticlesItem(Properties properties) {
        // レア度をEPICにして、見た目を豪華に（名前が紫になる）
        super(properties.rarity(net.minecraft.world.item.Rarity.EPIC));
    }

    // アイテムがインベントリにある時に光る（エンチャントのような光沢）
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skullheart.gn_particles.desc").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(" "));
        tooltip.add(Component.translatable("tooltip.skullheart.gn_particles.usage").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}