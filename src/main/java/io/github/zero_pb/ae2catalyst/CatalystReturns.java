package io.github.zero_pb.ae2catalyst;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.Actionable;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import appeng.parts.crafting.PatternProviderPart;

import io.github.zero_pb.ae2catalyst.mixin.CraftingCpuLogicAccessor;
import io.github.zero_pb.ae2catalyst.mixin.ExecutingCraftingJobAccessor;
import io.github.zero_pb.ae2catalyst.pattern.CatalystPatternDetails;

/**
 * Gives delivered catalysts back to the network.
 *
 * When a catalyst is pushed from ME storage into a machine, the delivery
 * is recorded here. Once a second, every open delivery is checked: the
 * catalyst is taken back out of the machine and inserted into the
 * network only when both of these hold:
 *
 * 1. No running crafting job uses a pattern that needs this catalyst.
 *    A finished AE2 job means all of its expected outputs have already
 *    returned to the network, so the machine's last operation for the
 *    job is done as well — this is a definite signal, not a timer.
 * 2. The machine no longer holds any of the other inputs of those
 *    patterns, so nothing is waiting to be processed with it.
 *
 * Records live in memory only. If the game stops while a catalyst is
 * out, the record is gone and the catalyst simply stays at the machine.
 */
public final class CatalystReturns {

    private CatalystReturns() {
    }

    private static final class Delivery {
        final ResourceKey<Level> dimension;
        final BlockPos providerPos;
        final Direction providerSide; // null: block provider; else: cable part on this side
        final BlockPos targetPos;
        final Direction side; // from the provider towards the target
        final AEKey catalyst;
        long amount;

        Delivery(ResourceKey<Level> dimension, BlockPos providerPos, Direction providerSide, BlockPos targetPos,
                Direction side, AEKey catalyst, long amount) {
            this.dimension = dimension;
            this.providerPos = providerPos;
            this.providerSide = providerSide;
            this.targetPos = targetPos;
            this.side = side;
            this.catalyst = catalyst;
            this.amount = amount;
        }
    }

    /** A resolved pattern provider: its logic and its grid node. */
    private record Provider(PatternProviderLogic logic, IGridNode node) {
    }

    private static final List<Delivery> DELIVERIES = new ArrayList<>();
    private static int tickCounter;

    public static void record(Level level, BlockPos providerPos, Direction providerSide, BlockPos targetPos,
            Direction side, Map<AEKey, Long> catalysts) {
        for (var entry : catalysts.entrySet()) {
            var merged = false;
            for (var delivery : DELIVERIES) {
                if (delivery.dimension.equals(level.dimension()) && delivery.providerPos.equals(providerPos)
                        && delivery.providerSide == providerSide && delivery.targetPos.equals(targetPos)
                        && delivery.catalyst.equals(entry.getKey())) {
                    delivery.amount += entry.getValue();
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                DELIVERIES.add(new Delivery(level.dimension(), providerPos, providerSide, targetPos, side,
                        entry.getKey(), entry.getValue()));
            }
        }
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || DELIVERIES.isEmpty()) {
            return;
        }
        if (++tickCounter % 20 != 0) {
            return;
        }
        var server = event.getServer();
        DELIVERIES.removeIf(delivery -> tryReturn(server, delivery));
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        DELIVERIES.clear();
    }

    /**
     * @return true when this record is finished: the catalyst was fully
     *         returned, or it can never be returned from here.
     */
    private static boolean tryReturn(MinecraftServer server, Delivery delivery) {
        var level = server.getLevel(delivery.dimension);
        if (level == null) {
            return true;
        }
        if (!level.isLoaded(delivery.providerPos) || !level.isLoaded(delivery.targetPos)) {
            return false;
        }

        var provider = resolveProvider(level, delivery);
        if (provider == null) {
            return true; // The provider is gone (or no longer a pattern provider).
        }
        var grid = provider.node() != null ? provider.node().getGrid() : null;
        if (grid == null) {
            return false; // Network is offline; try again later.
        }

        // 1) A running job that uses this catalyst keeps it at the machine.
        for (var cpu : grid.getCraftingService().getCpus()) {
            if (!(cpu instanceof CraftingCPUCluster cluster)) {
                continue;
            }
            var job = ((CraftingCpuLogicAccessor) cluster.craftingLogic).ae2catalyst$getJob();
            if (job == null) {
                continue;
            }
            var tasks = ((ExecutingCraftingJobAccessor) job).ae2catalyst$getTasks();
            for (var pattern : tasks.keySet()) {
                if (pattern instanceof CatalystPatternDetails catalystDetails
                        && catalystDetails.hasCatalystKey(delivery.catalyst)) {
                    return false;
                }
            }
        }

        var targetBe = level.getBlockEntity(delivery.targetPos);
        if (targetBe == null) {
            return true; // The machine is gone.
        }
        var handler = targetBe.getCapability(ForgeCapabilities.ITEM_HANDLER, delivery.side.getOpposite())
                .resolve().orElse(null);
        if (handler == null) {
            handler = targetBe.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        }
        if (handler == null) {
            return true; // No inventory to take the catalyst back from.
        }

        // 2) Other inputs of the patterns using this catalyst must be gone.
        Set<AEKey> materialKeys = new HashSet<>();
        for (var pattern : provider.logic().getAvailablePatterns()) {
            if (pattern instanceof CatalystPatternDetails catalystDetails
                    && catalystDetails.hasCatalystKey(delivery.catalyst)) {
                for (int i = 0; i < catalystDetails.getInputs().length; i++) {
                    if (!catalystDetails.isCatalyst(i)) {
                        for (var possible : catalystDetails.getInputs()[i].getPossibleInputs()) {
                            materialKeys.add(possible.what());
                        }
                    }
                }
            }
        }
        long present = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            var stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            var key = AEItemKey.of(stack);
            if (materialKeys.contains(key)) {
                return false;
            }
            if (delivery.catalyst.equals(key)) {
                present += stack.getCount();
            }
        }
        if (present == 0) {
            return true; // Already taken out by hand; nothing to return.
        }

        // 3) Take the catalyst back into the network.
        var storage = grid.getStorageService().getInventory();
        var source = new MachineSource(() -> provider.node());
        long toReturn = Math.min(delivery.amount, present);
        toReturn = Math.min(toReturn,
                storage.insert(delivery.catalyst, toReturn, Actionable.SIMULATE, source));
        if (toReturn <= 0) {
            return false; // The network cannot take it right now.
        }

        long extracted = extract(handler, delivery.catalyst, toReturn);
        var internalInventory = extracted == 0 ? internalInventoryOf(targetBe) : null;
        if (internalInventory != null) {
            // Some machines (e.g. AE2's own inscriber) refuse automation
            // extraction from the slots that hold the catalyst. Take it
            // from the machine's internal inventory instead — the same
            // inventory the machine's GUI shows.
            extracted = extract(internalInventory, delivery.catalyst, toReturn);
        }
        if (extracted == 0) {
            return false; // The inventory refuses extraction; retry later.
        }
        long inserted = storage.insert(delivery.catalyst, extracted, Actionable.MODULATE, source);
        delivery.amount -= inserted;

        if (inserted < extracted) {
            // Never lose the difference: put it back, or drop it in the world.
            long leftover = extracted - inserted;
            leftover -= insertBack(handler, delivery.catalyst, leftover);
            if (leftover > 0) {
                var internal = internalInventoryOf(targetBe);
                if (internal != null) {
                    leftover -= insertBack(internal, delivery.catalyst, leftover);
                }
            }
            if (leftover > 0 && delivery.catalyst instanceof AEItemKey itemKey) {
                var stack = itemKey.toStack((int) leftover);
                level.addFreshEntity(new ItemEntity(level, delivery.targetPos.getX() + 0.5,
                        delivery.targetPos.getY() + 1.0, delivery.targetPos.getZ() + 0.5, stack));
            }
        }
        return delivery.amount <= 0;
    }

    /**
     * The machine's internal inventory (the one its GUI shows), for AE2
     * machines that expose one. Null for anything else.
     */
    private static InternalInventory internalInventoryOf(net.minecraft.world.level.block.entity.BlockEntity be) {
        if (be instanceof ISegmentedInventory segmented) {
            return segmented.getSubInventory(ISegmentedInventory.STORAGE);
        }
        return null;
    }

    private static Provider resolveProvider(Level level, Delivery delivery) {
        var providerBe = level.getBlockEntity(delivery.providerPos);
        if (delivery.providerSide == null) {
            if (providerBe instanceof PatternProviderBlockEntity blockProvider) {
                return new Provider(blockProvider.getLogic(), blockProvider.getGridNode());
            }
            return null;
        }
        if (providerBe instanceof CableBusBlockEntity cableBus
                && cableBus.getPart(delivery.providerSide) instanceof PatternProviderPart part) {
            return new Provider(part.getLogic(), part.getGridNode());
        }
        return null;
    }

    private static long extract(IItemHandler handler, AEKey catalyst, long amount) {
        long extracted = 0;
        for (int slot = 0; slot < handler.getSlots() && extracted < amount; slot++) {
            var stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || !catalyst.equals(AEItemKey.of(stack))) {
                continue;
            }
            int want = (int) Math.min(amount - extracted, stack.getCount());
            extracted += handler.extractItem(slot, want, false).getCount();
        }
        return extracted;
    }

    private static long extract(InternalInventory inventory, AEKey catalyst, long amount) {
        long extracted = 0;
        for (int slot = 0; slot < inventory.size() && extracted < amount; slot++) {
            var stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty() || !catalyst.equals(AEItemKey.of(stack))) {
                continue;
            }
            int want = (int) Math.min(amount - extracted, stack.getCount());
            extracted += inventory.extractItem(slot, want, false).getCount();
        }
        return extracted;
    }

    private static long insertBack(IItemHandler handler, AEKey catalyst, long amount) {
        if (!(catalyst instanceof AEItemKey itemKey)) {
            return 0;
        }
        long remaining = amount;
        for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
            var stack = itemKey.toStack((int) Math.min(remaining, itemKey.getItem().getMaxStackSize()));
            remaining -= stack.getCount() - handler.insertItem(slot, stack, false).getCount();
        }
        return amount - remaining;
    }

    private static long insertBack(InternalInventory inventory, AEKey catalyst, long amount) {
        if (!(catalyst instanceof AEItemKey itemKey)) {
            return 0;
        }
        long remaining = amount;
        for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
            var stack = itemKey.toStack((int) Math.min(remaining, itemKey.getItem().getMaxStackSize()));
            remaining -= stack.getCount() - inventory.insertItem(slot, stack, false).getCount();
        }
        return amount - remaining;
    }
}
