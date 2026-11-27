package io.github.zero_pb.ae2catalyst.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

import appeng.api.stacks.AEItemKey;

import io.github.zero_pb.ae2catalyst.pattern.CatalystPatternDetails;
import io.github.zero_pb.ae2catalyst.pattern.CatalystPatternItem;

/**
 * Client to server: the player finished editing the catalyst flags of the
 * pattern held in the given hand. The server re-checks the flag count
 * against the pattern's condensed inputs before applying anything.
 */
public class SetCatalystsPacket {
    private final InteractionHand hand;
    private final boolean[] flags;

    public SetCatalystsPacket(InteractionHand hand, boolean[] flags) {
        this.hand = hand;
        this.flags = flags;
    }

    public static void encode(SetCatalystsPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.hand);
        buf.writeVarInt(msg.flags.length);
        for (boolean flag : msg.flags) {
            buf.writeBoolean(flag);
        }
    }

    public static SetCatalystsPacket decode(FriendlyByteBuf buf) {
        var hand = buf.readEnum(InteractionHand.class);
        int length = buf.readVarInt();
        var flags = new boolean[length];
        for (int i = 0; i < length; i++) {
            flags[i] = buf.readBoolean();
        }
        return new SetCatalystsPacket(hand, flags);
    }

    public static void handle(SetCatalystsPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            var player = ctx.getSender();
            if (player == null) {
                return;
            }
            var stack = player.getItemInHand(msg.hand);
            if (stack.getItem() instanceof CatalystPatternItem && stack.hasTag()) {
                var condensed = CatalystPatternDetails.condensedInputs(AEItemKey.of(stack));
                if (condensed.length == msg.flags.length) {
                    CatalystPatternDetails.setCatalysts(stack, msg.flags);
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
