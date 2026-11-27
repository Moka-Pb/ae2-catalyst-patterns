package io.github.zero_pb.ae2catalyst.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import io.github.zero_pb.ae2catalyst.client.ClientHooks;

/**
 * Server to client: the current catalyst flags of the encoding terminal
 * menu, so the screen can draw the markers on the input slots.
 */
public class SyncCatalystFlagsPacket {
    private final boolean[] flags;

    public SyncCatalystFlagsPacket(boolean[] flags) {
        this.flags = flags.clone();
    }

    public static void encode(SyncCatalystFlagsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.flags.length);
        for (boolean flag : msg.flags) {
            buf.writeBoolean(flag);
        }
    }

    public static SyncCatalystFlagsPacket decode(FriendlyByteBuf buf) {
        int length = buf.readVarInt();
        var flags = new boolean[length];
        for (int i = 0; i < length; i++) {
            flags[i] = buf.readBoolean();
        }
        return new SyncCatalystFlagsPacket(flags);
    }

    public static void handle(SyncCatalystFlagsPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientHooks.setCatalystFlags(msg.flags)));
        ctx.setPacketHandled(true);
    }
}
