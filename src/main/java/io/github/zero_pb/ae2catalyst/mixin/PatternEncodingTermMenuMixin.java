package io.github.zero_pb.ae2catalyst.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.menu.me.items.PatternEncodingTermMenu;

import io.github.zero_pb.ae2catalyst.Ae2Catalyst;
import io.github.zero_pb.ae2catalyst.menu.CatalystMenuData;
import io.github.zero_pb.ae2catalyst.pattern.CatalystPatternDetails;

/**
 * Lets the pattern encoding terminal produce catalyst patterns directly.
 *
 * The menu carries one catalyst flag per sparse processing input slot
 * (toggled from the screen with Alt+click). When a processing pattern is
 * encoded and at least one slot is marked, the freshly encoded stack is
 * converted into a catalyst pattern with the flags baked in, mapped from
 * sparse slots to condensed inputs. Patterns encoded without any marks
 * stay plain AE2 processing patterns.
 */
@Mixin(PatternEncodingTermMenu.class)
public abstract class PatternEncodingTermMenuMixin implements CatalystMenuData {

    @Unique
    private final boolean[] ae2catalyst$catalystFlags = new boolean[AEProcessingPattern.MAX_INPUT_SLOTS];

    @Override
    public boolean[] ae2catalyst$getCatalystFlags() {
        return ae2catalyst$catalystFlags;
    }

    @Redirect(method = "encodeProcessingPattern", remap = false, at = @At(value = "INVOKE",
            target = "Lappeng/api/crafting/PatternDetailsHelper;encodeProcessingPattern([Lappeng/api/stacks/GenericStack;[Lappeng/api/stacks/GenericStack;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack ae2catalyst$encodeProcessingPattern(GenericStack[] in, GenericStack[] out) {
        var stack = PatternDetailsHelper.encodeProcessingPattern(in, out);

        boolean any = false;
        for (boolean flag : ae2catalyst$catalystFlags) {
            any |= flag;
        }
        if (!any) {
            return stack;
        }

        var condensed = CatalystPatternDetails.condense(in);
        var condensedFlags = new boolean[condensed.length];
        for (int s = 0; s < in.length && s < ae2catalyst$catalystFlags.length; s++) {
            if (!ae2catalyst$catalystFlags[s] || in[s] == null) {
                continue;
            }
            for (int c = 0; c < condensed.length; c++) {
                if (condensed[c].what().equals(in[s].what())) {
                    condensedFlags[c] = true;
                    break;
                }
            }
        }

        boolean anyCondensed = false;
        for (boolean flag : condensedFlags) {
            anyCondensed |= flag;
        }
        if (!anyCondensed) {
            return stack;
        }

        var result = new ItemStack(Ae2Catalyst.CATALYST_PATTERN.get());
        if (stack.hasTag()) {
            result.setTag(stack.getTag().copy());
        }
        CatalystPatternDetails.setCatalysts(result, condensedFlags);
        return result;
    }
}
