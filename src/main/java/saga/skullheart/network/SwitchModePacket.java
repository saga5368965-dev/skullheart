package saga.skullheart.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import saga.skullheart.item.FunnelTurretItem;

import java.util.function.Supplier;

public class SwitchModePacket {

    public SwitchModePacket() {}

    public SwitchModePacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                FunnelTurretItem.cycleMode(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}