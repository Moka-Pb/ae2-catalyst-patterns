package io.github.zero_pb.ae2catalyst.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ICraftingInventory;

import io.github.zero_pb.ae2catalyst.pattern.CatalystPatternDetails;

/**
 * Execution-side half of the catalyst feature.
 *
 * The planner allocates exactly one catalyst into the job inventory (taken
 * from ME storage). On the first dispatch of the pattern, that catalyst is
 * extracted and pushed to the machine like a normal input, where it stays
 * and is reused. On later dispatches it is no longer in the job inventory,
 * so the input is skipped silently: the machine already has it. Either
 * way, no container item is ever registered for the catalyst, so the CPU
 * never waits for it to come back. All other inputs are extracted exactly
 * like AE2 does it, and the expected outputs are registered the same way.
 *
 * AE2 changed this method's signature between 15.2 and 15.4 (container
 * items got their own KeyCounter instead of sharing the expected-outputs
 * counter), so there is one injection per known signature. Each injection
 * names its exact descriptor and is require = 0, so on any given AE2
 * version only the matching one applies and the other is skipped.
 */
@Mixin(CraftingCpuHelper.class)
public class CraftingCpuHelperMixin {

    // AE2 15.2.x: extractPatternInputs(details, sourceInv, level, expectedOutputs)
    @Inject(method = "extractPatternInputs(Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/inv/ICraftingInventory;Lnet/minecraft/world/level/Level;Lappeng/api/stacks/KeyCounter;)[Lappeng/api/stacks/KeyCounter;", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void ae2catalyst$extractPatternInputs152(IPatternDetails details, ICraftingInventory sourceInv,
            Level level, KeyCounter expectedOutputs, CallbackInfoReturnable<KeyCounter[]> cir) {
        if (details instanceof CatalystPatternDetails) {
            // On 15.2, container items are registered into expectedOutputs.
            cir.setReturnValue(ae2catalyst$extract(details, sourceInv, level, expectedOutputs, expectedOutputs));
        }
    }

    // AE2 15.4.x: extractPatternInputs(details, sourceInv, level, expectedOutputs, expectedContainerItems)
    @Inject(method = "extractPatternInputs(Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/inv/ICraftingInventory;Lnet/minecraft/world/level/Level;Lappeng/api/stacks/KeyCounter;Lappeng/api/stacks/KeyCounter;)[Lappeng/api/stacks/KeyCounter;", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void ae2catalyst$extractPatternInputs154(IPatternDetails details, ICraftingInventory sourceInv,
            Level level, KeyCounter expectedOutputs, KeyCounter expectedContainerItems,
            CallbackInfoReturnable<KeyCounter[]> cir) {
        if (details instanceof CatalystPatternDetails) {
            cir.setReturnValue(
                    ae2catalyst$extract(details, sourceInv, level, expectedOutputs, expectedContainerItems));
        }
    }

    private static KeyCounter[] ae2catalyst$extract(IPatternDetails details, ICraftingInventory sourceInv,
            Level level, KeyCounter expectedOutputs, KeyCounter containerItems) {
        var catalystDetails = (CatalystPatternDetails) details;
        var inputs = details.getInputs();
        KeyCounter[] inputHolder = new KeyCounter[inputs.length];
        boolean found = true;

        for (int x = 0; x < inputs.length; x++) {
            var list = inputHolder[x] = new KeyCounter();

            if (catalystDetails.isCatalyst(x)) {
                // Try to take the catalyst from the job inventory. If it is
                // not (fully) there, it has already been delivered and is
                // resident at the machine: put back any partial amount and
                // skip this input instead of failing the dispatch.
                long remaining = inputs[x].getMultiplier();
                for (var template : CraftingCpuHelper.getValidItemTemplates(sourceInv, inputs[x], level)) {
                    long extracted = CraftingCpuHelper.extractTemplates(sourceInv, template, remaining);
                    list.add(template.key(), extracted * template.amount());
                    remaining -= extracted;
                    if (remaining == 0) {
                        break;
                    }
                }
                if (remaining > 0) {
                    for (var entry : list) {
                        sourceInv.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
                    }
                    list.reset();
                }
                continue;
            }

            long remainingMultiplier = inputs[x].getMultiplier();
            for (var template : CraftingCpuHelper.getValidItemTemplates(sourceInv, inputs[x], level)) {
                long extracted = CraftingCpuHelper.extractTemplates(sourceInv, template, remainingMultiplier);
                list.add(template.key(), extracted * template.amount());

                var containerItem = inputs[x].getRemainingKey(template.key());
                if (containerItem != null) {
                    containerItems.add(containerItem, extracted);
                }

                remainingMultiplier -= extracted;
                if (remainingMultiplier == 0) {
                    break;
                }
            }

            if (remainingMultiplier > 0) {
                found = false;
                break;
            }
        }

        if (!found) {
            CraftingCpuHelper.reinjectPatternInputs(sourceInv, inputHolder);
            return null;
        }

        for (var output : details.getOutputs()) {
            expectedOutputs.add(output.what(), output.amount());
        }

        return inputHolder;
    }
}
