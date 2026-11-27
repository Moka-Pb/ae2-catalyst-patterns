package io.github.zero_pb.ae2catalyst.pattern;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.EncodedPatternItem;
import appeng.util.InteractionUtil;

import io.github.zero_pb.ae2catalyst.client.ClientHooks;

/**
 * An encoded processing pattern that can carry catalyst flags. AE2's own
 * pattern decoder dispatches on any EncodedPatternItem, so providers, CPUs
 * and terminals pick this up automatically.
 *
 * Right-clicking the encoded pattern (without the alternate-use key) opens
 * the catalyst editor; alternate-use keeps AE2's standard clear behaviour.
 */
public class CatalystPatternItem extends EncodedPatternItem {
    public CatalystPatternItem(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public CatalystPatternDetails decode(ItemStack stack, Level level, boolean tryRecovery) {
        return decode(AEItemKey.of(stack), level);
    }

    @Nullable
    @Override
    public CatalystPatternDetails decode(AEItemKey what, Level level) {
        if (what == null || !what.hasTag()) {
            return null;
        }
        try {
            return new CatalystPatternDetails(what);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.hasTag() && !InteractionUtil.isInAlternateUseMode(player)) {
            if (level.isClientSide) {
                ClientHooks.openEditor(stack, hand);
            }
            return new InteractionResultHolder<>(InteractionResult.sidedSuccess(level.isClientSide), stack);
        }
        return super.use(level, player, hand);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        var details = decode(stack, level, false);
        if (details != null && details.hasCatalysts()) {
            var names = new StringBuilder();
            var inputs = details.getInputs();
            for (int i = 0; i < inputs.length; i++) {
                if (details.isCatalyst(i)) {
                    if (names.length() > 0) {
                        names.append(", ");
                    }
                    names.append(inputs[i].getPossibleInputs()[0].what().getDisplayName().getString());
                }
            }
            lines.add(Component.translatable("item.ae2catalyst.catalyst_pattern.catalysts", names.toString()));
        }
    }
}
