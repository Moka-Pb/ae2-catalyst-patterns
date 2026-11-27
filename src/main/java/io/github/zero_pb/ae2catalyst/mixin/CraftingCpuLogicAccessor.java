package io.github.zero_pb.ae2catalyst.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;

@Mixin(CraftingCpuLogic.class)
public interface CraftingCpuLogicAccessor {

    @Accessor(value = "job", remap = false)
    ExecutingCraftingJob ae2catalyst$getJob();
}
