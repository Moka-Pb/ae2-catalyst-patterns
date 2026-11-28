package io.github.zero_pb.ae2catalyst.compat;

import java.util.Set;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.tile.base.TileEntityMekanism;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

/**
 * Catalyst return support for Mekanism machines (including addons such as
 * Astral Mekanism &amp; Energistics' Mekanical Inscriber).
 *
 * A Mekanism machine's side configuration decides what automation may take
 * out through the item-handler capability, and the press slots of a
 * Mekanical Inscriber are input-only on most sides — so, like AE2's own
 * inscriber, the catalyst often cannot be extracted that way. The
 * machine's full slot list (side = null means "internal, give all slots")
 * has no such restriction, so the catalyst is taken from there instead,
 * with manual (player-like) interaction semantics.
 *
 * This class must only be called when the "mekanism" mod is loaded.
 */
public final class MekanismCompat {

    private MekanismCompat() {
    }

    public static boolean isMekanismMachine(BlockEntity be) {
        return be instanceof TileEntityMekanism;
    }

    /**
     * @return {catalyst count, 1 if any of the material keys is present},
     *         or null when the block entity is not a Mekanism machine.
     */
    public static long[] scan(BlockEntity be, AEKey catalyst, Set<AEKey> materials) {
        if (!(be instanceof TileEntityMekanism tile)) {
            return null;
        }
        long present = 0;
        long blocked = 0;
        for (IInventorySlot slot : tile.getInventorySlots(null)) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) {
                continue;
            }
            AEKey key = AEItemKey.of(stack);
            if (materials.contains(key)) {
                blocked = 1;
            }
            if (catalyst.equals(key)) {
                present += stack.getCount();
            }
        }
        return new long[] { present, blocked };
    }

    public static long extract(BlockEntity be, AEKey catalyst, long amount) {
        if (!(be instanceof TileEntityMekanism tile)) {
            return 0;
        }
        long extracted = 0;
        for (IInventorySlot slot : tile.getInventorySlots(null)) {
            if (extracted >= amount) {
                break;
            }
            ItemStack stack = slot.getStack();
            if (stack.isEmpty() || !catalyst.equals(AEItemKey.of(stack))) {
                continue;
            }
            int want = (int) Math.min(amount - extracted, stack.getCount());
            extracted += slot.extractItem(want, Action.EXECUTE, AutomationType.MANUAL).getCount();
        }
        return extracted;
    }

    public static long insertBack(BlockEntity be, AEKey catalyst, long amount) {
        if (!(catalyst instanceof AEItemKey itemKey) || !(be instanceof TileEntityMekanism tile)) {
            return 0;
        }
        long remaining = amount;
        for (IInventorySlot slot : tile.getInventorySlots(null)) {
            if (remaining <= 0) {
                break;
            }
            ItemStack stack = itemKey.toStack((int) Math.min(remaining, itemKey.getItem().getMaxStackSize()));
            remaining -= stack.getCount() - slot.insertItem(stack, Action.EXECUTE, AutomationType.MANUAL).getCount();
        }
        return amount - remaining;
    }
}
