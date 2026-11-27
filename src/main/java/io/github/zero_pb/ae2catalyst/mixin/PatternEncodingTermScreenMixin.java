package io.github.zero_pb.ae2catalyst.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;

import io.github.zero_pb.ae2catalyst.Ae2Catalyst;
import io.github.zero_pb.ae2catalyst.client.ClientHooks;
import io.github.zero_pb.ae2catalyst.network.SetCatalystFlagsPacket;
import io.github.zero_pb.ae2catalyst.network.ToggleCatalystSlotPacket;
import io.github.zero_pb.ae2catalyst.pattern.CatalystPatternDetails;
import io.github.zero_pb.ae2catalyst.pattern.CatalystPatternItem;

/**
 * Client side of catalyst marking in AE2's pattern encoding terminal.
 *
 * Alt+left-click on a processing input slot toggles its catalyst flag
 * (the server menu owns the flags; the client only renders what it is
 * synced). Marked slots get an orange frame. When a catalyst pattern is
 * loaded into the terminal, its flags are restored onto the slots.
 *
 * This mixin extends AbstractContainerScreen so it can use the inherited
 * hoveredSlot field and findSlot method directly. Direct member accesses
 * are remapped by the jar reobfuscation step, unlike @Shadow declarations,
 * which would need refmap entries the annotation processor cannot produce
 * for members declared in a superclass of the target.
 */
@Mixin(PatternEncodingTermScreen.class)
public abstract class PatternEncodingTermScreenMixin extends AbstractContainerScreen<PatternEncodingTermMenu> {

    protected PatternEncodingTermScreenMixin(PatternEncodingTermMenu menu, Inventory playerInventory,
            Component title) {
        super(menu, playerInventory, title);
    }

    @Unique
    private ItemStack ae2catalyst$lastSeenPattern;

    @Unique
    private boolean ae2catalyst$hadCatalyst;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2catalyst$mouseClicked(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 || !Screen.hasAltDown()) {
            return;
        }
        var menu = getMenu();
        if (menu.mode != EncodingMode.PROCESSING) {
            return;
        }
        var slot = findSlot(mouseX, mouseY);
        if (slot == null || !slot.hasItem()
                || menu.getSlotSemantic(slot) != SlotSemantics.PROCESSING_INPUTS) {
            return;
        }
        int index = menu.getSlots(SlotSemantics.PROCESSING_INPUTS).indexOf(slot);
        if (index < 0) {
            return;
        }
        Ae2Catalyst.CHANNEL.sendToServer(new ToggleCatalystSlotPacket(index));
        cir.setReturnValue(true);
    }

    @Inject(method = "renderSlot", at = @At("TAIL"), remap = false)
    private void ae2catalyst$renderSlot(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        ae2catalyst$restoreFlagsIfPatternChanged();

        var menu = getMenu();
        if (menu.mode != EncodingMode.PROCESSING
                || menu.getSlotSemantic(slot) != SlotSemantics.PROCESSING_INPUTS) {
            return;
        }
        int index = menu.getSlots(SlotSemantics.PROCESSING_INPUTS).indexOf(slot);
        var flags = ClientHooks.getCatalystFlags();
        if (index < 0 || index >= flags.length || !flags[index]) {
            return;
        }
        // Translucent wash plus a 1px orange frame around the slot.
        graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x40FFAA00);
        graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 1, 0xFFFFAA00);
        graphics.fill(slot.x, slot.y + 15, slot.x + 16, slot.y + 16, 0xFFFFAA00);
        graphics.fill(slot.x, slot.y, slot.x + 1, slot.y + 16, 0xFFFFAA00);
        graphics.fill(slot.x + 15, slot.y, slot.x + 16, slot.y + 16, 0xFFFFAA00);
    }

    @Inject(method = "getTooltipFromContainerItem", at = @At("RETURN"), cancellable = true, remap = false)
    private void ae2catalyst$tooltip(ItemStack stack, CallbackInfoReturnable<List<Component>> cir) {
        var menu = getMenu();
        if (menu.mode != EncodingMode.PROCESSING || this.hoveredSlot == null
                || menu.getSlotSemantic(this.hoveredSlot) != SlotSemantics.PROCESSING_INPUTS) {
            return;
        }
        int index = menu.getSlots(SlotSemantics.PROCESSING_INPUTS).indexOf(this.hoveredSlot);
        if (index < 0) {
            return;
        }
        var flags = ClientHooks.getCatalystFlags();
        boolean marked = index < flags.length && flags[index];
        var lines = new ArrayList<>(cir.getReturnValue());
        lines.add(Component.translatable(marked ? "gui.ae2catalyst.marked" : "gui.ae2catalyst.mark_hint"));
        cir.setReturnValue(lines);
    }

    /**
     * Keeps the slot flags in step with the pattern loaded in the terminal:
     * when a catalyst pattern appears, restore its flags; when it is
     * removed again, clear them. Runs from renderSlot, guarded by an
     * identity check so it only acts on changes.
     */
    @Unique
    private void ae2catalyst$restoreFlagsIfPatternChanged() {
        var menu = getMenu();
        var patternSlots = menu.getSlots(SlotSemantics.ENCODED_PATTERN);
        if (patternSlots.isEmpty()) {
            return;
        }
        var stack = patternSlots.get(0).getItem();
        if (stack == ae2catalyst$lastSeenPattern) {
            return;
        }
        ae2catalyst$lastSeenPattern = stack;

        if (stack.getItem() instanceof CatalystPatternItem) {
            ae2catalyst$hadCatalyst = true;
            var condensed = CatalystPatternDetails.condensedInputs(AEItemKey.of(stack));
            var condensedFlags = CatalystPatternDetails.readCatalysts(stack);
            var inputs = menu.getSlots(SlotSemantics.PROCESSING_INPUTS);
            var sparse = new boolean[inputs.size()];
            for (int i = 0; i < inputs.size(); i++) {
                var slotStack = inputs.get(i).getItem();
                if (slotStack.isEmpty()) {
                    continue;
                }
                var key = AEItemKey.of(slotStack);
                if (key == null) {
                    continue;
                }
                for (int c = 0; c < condensed.length; c++) {
                    if (condensedFlags[c] && condensed[c].what().equals(key)) {
                        sparse[i] = true;
                        break;
                    }
                }
            }
            ClientHooks.setCatalystFlags(sparse);
            Ae2Catalyst.CHANNEL.sendToServer(new SetCatalystFlagsPacket(sparse));
        } else if (ae2catalyst$hadCatalyst) {
            ae2catalyst$hadCatalyst = false;
            var empty = new boolean[AEProcessingPattern.MAX_INPUT_SLOTS];
            ClientHooks.setCatalystFlags(empty);
            Ae2Catalyst.CHANNEL.sendToServer(new SetCatalystFlagsPacket(empty));
        }
    }
}
