package io.github.zero_pb.ae2catalyst.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import appeng.crafting.pattern.ProcessingPatternItem;

import io.github.zero_pb.ae2catalyst.Ae2Catalyst;
import io.github.zero_pb.ae2catalyst.pattern.CatalystPatternDetails;

/**
 * Shapeless conversion: an encoded AE2 processing pattern, alone in the
 * crafting grid, becomes a catalyst pattern with the same contents and no
 * catalysts marked yet. Mark them by right-clicking the result.
 */
public class ConvertPatternRecipe extends CustomRecipe {
    public ConvertPatternRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    private static ItemStack findSource(CraftingContainer inv) {
        ItemStack found = ItemStack.EMPTY;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                if (!found.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                found = stack;
            }
        }
        return found;
    }

    @Override
    public boolean matches(CraftingContainer inv, Level level) {
        var source = findSource(inv);
        return source.getItem() instanceof ProcessingPatternItem && source.hasTag();
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess registryAccess) {
        var source = findSource(inv);
        var result = new ItemStack(Ae2Catalyst.CATALYST_PATTERN.get());
        if (source.hasTag()) {
            var tag = source.getTag().copy();
            tag.remove(CatalystPatternDetails.CATALYSTS_TAG);
            result.setTag(tag);
        }
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Ae2Catalyst.CONVERT_PATTERN.get();
    }
}
