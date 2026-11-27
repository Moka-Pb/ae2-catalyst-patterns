package io.github.zero_pb.ae2catalyst.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ICraftingInventory;

import io.github.zero_pb.ae2catalyst.pattern.CatalystPatternDetails;

/**
 * Execution-side half of the catalyst feature.
 *
 * When a crafting CPU dispatches a catalyst pattern, the catalyst input is
 * not extracted from the CPU inventory at all: the catalyst is expected to
 * already be resident at the machine (in its input bus, a stocking bus, and
 * so on), so there is nothing to push and nothing to wait for coming back.
 * All other inputs are extracted exactly like AE2 does it, and the expected
 * outputs are registered the same way.
 */
@Mixin(CraftingCpuHelper.class)
public class CraftingCpuHelperMixin {

    @Inject(method = "extractPatternInputs", at = @At("HEAD"), cancellable = true)
    private static void ae2catalyst$extractPatternInputs(IPatternDetails details, ICraftingInventory sourceInv,
            Level level, KeyCounter expectedOutputs, KeyCounter expectedContainerItems,
            CallbackInfoReturnable<KeyCounter[]> cir) {
        if (!(details instanceof CatalystPatternDetails catalystDetails)) {
            return;
        }

        var inputs = details.getInputs();
        KeyCounter[] inputHolder = new KeyCounter[inputs.length];
        boolean found = true;

        for (int x = 0; x < inputs.length; x++) {
            var list = inputHolder[x] = new KeyCounter();

            if (catalystDetails.isCatalyst(x)) {
                // Resident at the machine: nothing to extract, nothing to
                // push, and no container item to wait for.
                continue;
            }

            long remainingMultiplier = inputs[x].getMultiplier();
            for (var template : CraftingCpuHelper.getValidItemTemplates(sourceInv, inputs[x], level)) {
                long extracted = CraftingCpuHelper.extractTemplates(sourceInv, template, remainingMultiplier);
                list.add(template.key(), extracted * template.amount());

                var containerItem = inputs[x].getRemainingKey(template.key());
                if (containerItem != null) {
                    expectedContainerItems.add(containerItem, extracted);
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
            cir.setReturnValue(null);
            return;
        }

        for (var output : details.getOutputs()) {
            expectedOutputs.add(output.what(), output.amount());
        }

        cir.setReturnValue(inputHolder);
    }
}
