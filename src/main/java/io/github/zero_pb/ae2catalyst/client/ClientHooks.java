package io.github.zero_pb.ae2catalyst.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import appeng.crafting.pattern.AEProcessingPattern;

/**
 * Client-only entry points. Referenced from common code only behind an
 * isClientSide check or a dist-safe executor call, so this class is never
 * loaded on a dedicated server.
 */
public class ClientHooks {
    /**
     * Catalyst flags of the currently open pattern encoding terminal, as
     * last synced from the server. Used by the terminal screen to draw the
     * slot markers.
     */
    private static boolean[] catalystFlags = new boolean[AEProcessingPattern.MAX_INPUT_SLOTS];

    public static void openEditor(ItemStack stack, InteractionHand hand) {
        Minecraft.getInstance().setScreen(new CatalystEditScreen(stack, hand));
    }

    public static boolean[] getCatalystFlags() {
        return catalystFlags;
    }

    public static void setCatalystFlags(boolean[] flags) {
        catalystFlags = flags;
    }
}
