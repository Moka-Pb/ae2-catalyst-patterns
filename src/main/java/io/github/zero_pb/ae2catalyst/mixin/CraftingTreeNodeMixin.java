package io.github.zero_pb.ae2catalyst.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.CraftingSimulationState;

import io.github.zero_pb.ae2catalyst.pattern.CatalystPatternDetails;

/**
 * Planning-side half of the catalyst feature.
 *
 * AE2's crafting tree normally demands every pattern input from the
 * network: whatever cannot be taken from the simulated inventory is
 * crafted by a sub-pattern or reported missing, and the job cannot start.
 * For a catalyst that is wrong in both directions — a catalyst that only
 * exists inside its machine (where it belongs) would block every job,
 * and a craftable catalyst would be produced once per job even though
 * the machine already has one.
 *
 * So a catalyst input node takes what the network actually has (which
 * registers it as its own container item, so the plan allocates at most
 * one and the execution side can deliver it) and then simply forgives
 * the rest instead of crafting or failing: the missing part is expected
 * to already be resident at the machine.
 */
@Mixin(CraftingTreeNode.class)
public class CraftingTreeNodeMixin {

    @Shadow(remap = false)
    private IPatternDetails.IInput parentInput;

    @Shadow(remap = false)
    private AEKey what;

    @Shadow(remap = false)
    private long amount;

    @Shadow(remap = false)
    private Level level;

    @Inject(method = "request", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2catalyst$requestCatalyst(CraftingSimulationState inv, long requestedAmount,
            @Nullable KeyCounter containerItems, CallbackInfo ci) {
        if (!CatalystPatternDetails.isCatalystInput(parentInput)) {
            return;
        }

        inv.addStackBytes(what, amount, requestedAmount);

        long remaining = requestedAmount;
        for (var template : CraftingCpuHelper.getValidItemTemplates(inv, parentInput, level)) {
            long extracted = CraftingCpuHelper.extractTemplates(inv, template, remaining);
            if (extracted > 0) {
                remaining -= extracted;
                if (containerItems != null) {
                    var containerItem = parentInput.getRemainingKey(template.key());
                    if (containerItem != null) {
                        containerItems.add(containerItem, extracted);
                    }
                }
                if (remaining == 0) {
                    break;
                }
            }
        }

        // Forgive whatever the network does not have: never craft the
        // catalyst, never report it missing, never block the job.
        ci.cancel();
    }
}
