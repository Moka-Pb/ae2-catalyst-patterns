package io.github.zero_pb.ae2catalyst.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import io.github.zero_pb.ae2catalyst.menu.CatalystMenuData;

/**
 * Client to server: replace the catalyst flags of the open encoding
 * terminal menu. Sent when the client restores the flags from a catalyst
 * pattern that was loaded into the terminal.
 */
public class SetCatalystFlagsPacket {
    private final boolean[] flags;

    public SetCatalystFlagsPacket(boolean[] flags) {
        this.flags = flags;
    }

    public static void encode(SetCatalystFlagsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.flags.length);
        for (boolean flag : msg.flags) {
            buf.writeBoolean(flag);
        }
    }

    public static SetCatalystFlagsPacket decode(FriendlyByteBuf buf) {
        int length = buf.readVarInt();
        var flags = new boolean[length];
        for (int i = 0; i < length; i++) {
            flags[i] = buf.readBoolean();
        }
        return new SetCatalystFlagsPacket(flags);
    }

    public static void handle(SetCatalystFlagsPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            var player = ctx.getSender();
            if (player != null && player.containerMenu instanceof CatalystMenuData data) {
                var target = data.ae2catalyst$getCatalystFlags();
                for (int i = 0; i < target.length; i++) {
                    target[i] = i < msg.flags.length && msg.flags[i];
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
