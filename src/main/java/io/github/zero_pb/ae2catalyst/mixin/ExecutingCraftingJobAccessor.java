package io.github.zero_pb.ae2catalyst.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.crafting.execution.ExecutingCraftingJob;

@Mixin(ExecutingCraftingJob.class)
public interface ExecutingCraftingJobAccessor {

    @Accessor(value = "tasks", remap = false)
    Map ae2catalyst$getTasks();
}
