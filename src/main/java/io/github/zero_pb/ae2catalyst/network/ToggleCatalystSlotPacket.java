package io.github.zero_pb.ae2catalyst.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import io.github.zero_pb.ae2catalyst.Ae2Catalyst;
import io.github.zero_pb.ae2catalyst.menu.CatalystMenuData;

/**
 * Client to server: the player Alt+clicked a processing input slot in the
 * pattern encoding terminal. Flips that slot's catalyst flag on the open
 * menu and syncs the full flag set back to the client for rendering.
 */
public class ToggleCatalystSlotPacket {
    private final int index;

    public ToggleCatalystSlotPacket(int index) {
        this.index = index;
    }

    public static void encode(ToggleCatalystSlotPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.index);
    }

    public static ToggleCatalystSlotPacket decode(FriendlyByteBuf buf) {
        return new ToggleCatalystSlotPacket(buf.readVarInt());
    }

    public static void handle(ToggleCatalystSlotPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            var player = ctx.getSender();
            if (player != null && player.containerMenu instanceof CatalystMenuData data) {
                var flags = data.ae2catalyst$getCatalystFlags();
                if (msg.index >= 0 && msg.index < flags.length) {
                    flags[msg.index] = !flags[msg.index];
                    Ae2Catalyst.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new SyncCatalystFlagsPacket(flags));
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
