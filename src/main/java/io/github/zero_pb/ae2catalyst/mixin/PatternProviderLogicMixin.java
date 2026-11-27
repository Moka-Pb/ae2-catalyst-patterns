package io.github.zero_pb.ae2catalyst.mixin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.Direction;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.parts.crafting.PatternProviderPart;

import io.github.zero_pb.ae2catalyst.CatalystReturns;
import io.github.zero_pb.ae2catalyst.pattern.CatalystPatternDetails;

/**
 * Records catalyst deliveries: when a pattern provider successfully
 * pushes a catalyst pattern whose extracted inputs include a catalyst,
 * the catalyst has just been delivered to an adjacent machine. The
 * delivery is handed to {@link CatalystReturns}, which gives the
 * catalyst back to the network once it is no longer needed.
 */
@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderLogicMixin {

    @Shadow(remap = false)
    private PatternProviderLogicHost host;

    @Shadow(remap = false)
    protected abstract Set<Direction> getActiveSides();

    @Unique
    private Map<AEKey, Long> ae2catalyst$pendingDelivery;

    @Inject(method = "pushPattern", at = @At("HEAD"), remap = false)
    private void ae2catalyst$captureDeliveryHead(IPatternDetails details, KeyCounter[] inputHolder,
            CallbackInfoReturnable<Boolean> cir) {
        ae2catalyst$pendingDelivery = null;
        if (!(details instanceof CatalystPatternDetails catalystDetails)) {
            return;
        }
        Map<AEKey, Long> found = new HashMap<>();
        for (int i = 0; i < inputHolder.length; i++) {
            if (!catalystDetails.isCatalyst(i)) {
                continue;
            }
            for (var entry : inputHolder[i]) {
                found.merge(entry.getKey(), entry.getLongValue(), Long::sum);
            }
        }
        if (!found.isEmpty()) {
            ae2catalyst$pendingDelivery = found;
        }
    }

    @Inject(method = "pushPattern", at = @At("RETURN"), remap = false)
    private void ae2catalyst$captureDeliveryReturn(IPatternDetails details, KeyCounter[] inputHolder,
            CallbackInfoReturnable<Boolean> cir) {
        var pending = ae2catalyst$pendingDelivery;
        ae2catalyst$pendingDelivery = null;
        if (pending == null || !Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }
        var be = host.getBlockEntity();
        if (be == null || be.getLevel() == null) {
            return;
        }
        // For a cable part provider, remember which side of the cable bus
        // the part sits on so the return pass can find it again.
        Direction partSide = null;
        if (host instanceof PatternProviderPart part) {
            partSide = part.getSide();
        }
        for (var direction : getActiveSides()) {
            CatalystReturns.record(be.getLevel(), be.getBlockPos(), partSide,
                    be.getBlockPos().relative(direction), direction, pending);
        }
    }
}
