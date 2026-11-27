package io.github.zero_pb.ae2catalyst.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Client-only entry points. Referenced from common code only behind an
 * isClientSide check, so this class is never loaded on a dedicated server.
 */
public class ClientHooks {
    public static void openEditor(ItemStack stack, InteractionHand hand) {
        Minecraft.getInstance().setScreen(new CatalystEditScreen(stack, hand));
    }
}
