package io.github.zero_pb.ae2catalyst.pattern;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;

/**
 * Pattern details for a catalyst pattern. The encoded layout is exactly
 * AE2's processing pattern layout ("in"/"out"), plus an int array tag
 * "Catalysts" holding one flag per condensed input.
 *
 * A catalyst input reports itself as its own remaining key. AE2's crafting
 * tree already knows how to reuse remaining items (it does this for buckets
 * in crafting patterns): the pattern becomes quantity-limited, is simulated
 * one run at a time, and the catalyst is put back into the simulated
 * inventory after each run. So the planner requires exactly one catalyst no
 * matter how many operations the job needs, and only ever tries to craft a
 * single one if none is in stock.
 */
public class CatalystPatternDetails implements IPatternDetails {
    public static final String CATALYSTS_TAG = "Catalysts";

    private final AEItemKey definition;
    private final AEProcessingPattern wrapped;
    private final boolean[] catalysts;
    private final IInput[] inputs;

    public CatalystPatternDetails(AEItemKey definition) {
        this.definition = definition;
        this.wrapped = new AEProcessingPattern(definition);

        var condensed = condense(wrapped.getSparseInputs());
        var tag = definition.getTag();
        int[] stored = tag != null && tag.contains(CATALYSTS_TAG, Tag.TAG_INT_ARRAY)
                ? tag.getIntArray(CATALYSTS_TAG)
                : new int[0];

        this.catalysts = new boolean[condensed.length];
        this.inputs = new IInput[condensed.length];
        for (int i = 0; i < condensed.length; i++) {
            catalysts[i] = i < stored.length && stored[i] != 0;
            inputs[i] = new CatalystInput(condensed[i], catalysts[i]);
        }
    }

    /** Condensed inputs of an encoded pattern key, in pattern order. */
    public static GenericStack[] condensedInputs(AEItemKey key) {
        return condense(new AEProcessingPattern(key).getSparseInputs());
    }

    /**
     * Merges a sparse stack array by key, summing amounts and preserving
     * first-seen order. This mirrors AE2's own condensing (its helper class
     * is package-private), so the result lines up with the input order of
     * AEProcessingPattern itself.
     */
    public static GenericStack[] condense(GenericStack[] sparse) {
        var result = new java.util.ArrayList<GenericStack>();
        for (var stack : sparse) {
            if (stack == null) {
                continue;
            }
            int found = -1;
            for (int i = 0; i < result.size(); i++) {
                if (result.get(i).what().equals(stack.what())) {
                    found = i;
                    break;
                }
            }
            if (found >= 0) {
                var existing = result.get(found);
                result.set(found, new GenericStack(existing.what(), existing.amount() + stack.amount()));
            } else {
                result.add(stack);
            }
        }
        return result.toArray(new GenericStack[0]);
    }

    /** Current catalyst flags stored on an encoded pattern stack. */
    public static boolean[] readCatalysts(ItemStack stack) {
        var condensed = condensedInputs(AEItemKey.of(stack));
        var result = new boolean[condensed.length];
        var tag = stack.getTag();
        if (tag != null && tag.contains(CATALYSTS_TAG, Tag.TAG_INT_ARRAY)) {
            int[] stored = tag.getIntArray(CATALYSTS_TAG);
            for (int i = 0; i < result.length && i < stored.length; i++) {
                result[i] = stored[i] != 0;
            }
        }
        return result;
    }

    /** Writes catalyst flags (one per condensed input) onto a pattern stack. */
    public static void setCatalysts(ItemStack stack, boolean[] flags) {
        var ints = new int[flags.length];
        for (int i = 0; i < flags.length; i++) {
            ints[i] = flags[i] ? 1 : 0;
        }
        stack.getOrCreateTag().putIntArray(CATALYSTS_TAG, ints);
    }

    public boolean isCatalyst(int inputIndex) {
        return catalysts[inputIndex];
    }

    public boolean hasCatalysts() {
        for (boolean catalyst : catalysts) {
            if (catalyst) {
                return true;
            }
        }
        return false;
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IInput[] getInputs() {
        return inputs;
    }

    @Override
    public GenericStack[] getOutputs() {
        return wrapped.getOutputs();
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        var sparseInputs = wrapped.getSparseInputs();
        if (sparseInputs.length == inputs.length) {
            // No compression -> the default push over the holders is fine;
            // catalyst holders are empty, so nothing is pushed for them.
            IPatternDetails.super.pushInputsToExternalInventory(inputHolder, inputSink);
            return;
        }

        var allInputs = new KeyCounter();
        for (var counter : inputHolder) {
            allInputs.addAll(counter);
        }

        // Push according to sparse input order, like AE2 does, but skip
        // catalysts: they are resident at the machine and never extracted,
        // so AE2's own version of this loop would throw an exception
        // expecting them among the extracted inputs.
        for (var sparseInput : sparseInputs) {
            if (sparseInput == null || isCatalystKey(sparseInput.what())) {
                continue;
            }

            var key = sparseInput.what();
            var amount = sparseInput.amount();
            long available = allInputs.get(key);

            if (available < amount) {
                throw new RuntimeException("Expected at least %d of %s when pushing pattern, but only %d available"
                        .formatted(amount, key, available));
            }

            inputSink.pushInput(key, amount);
            allInputs.remove(key, amount);
        }
    }

    private boolean isCatalystKey(AEKey key) {
        for (int i = 0; i < inputs.length; i++) {
            if (catalysts[i] && inputs[i].getPossibleInputs()[0].what().equals(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj.getClass() == getClass()
                && ((CatalystPatternDetails) obj).definition.equals(definition);
    }

    private static class CatalystInput implements IInput {
        private final GenericStack[] template;
        private final long multiplier;
        private final boolean catalyst;

        CatalystInput(GenericStack stack, boolean catalyst) {
            this.template = new GenericStack[] { new GenericStack(stack.what(), 1) };
            this.multiplier = stack.amount();
            this.catalyst = catalyst;
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return template;
        }

        @Override
        public long getMultiplier() {
            return multiplier;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return input.matches(template[0]);
        }

        @Nullable
        @Override
        public AEKey getRemainingKey(AEKey template) {
            // The whole trick: a catalyst "remains" after the craft, exactly
            // like an empty bucket remains after using a water bucket. The
            // planner reuses the same one for every run of the pattern.
            return catalyst ? template : null;
        }
    }
}
