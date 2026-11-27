package io.github.zero_pb.ae2catalyst.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import io.github.zero_pb.ae2catalyst.Ae2Catalyst;
import io.github.zero_pb.ae2catalyst.network.SetCatalystsPacket;
import io.github.zero_pb.ae2catalyst.pattern.CatalystPatternDetails;

/**
 * Minimal editor for a catalyst pattern: one row per condensed input of the
 * pattern held in hand, each with a catalyst on/off toggle. Pure client UI;
 * on Done the flags are sent to the server, which validates and applies
 * them to the held stack.
 */
public class CatalystEditScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int TOP = 44;

    private final ItemStack stack;
    private final InteractionHand hand;
    private final GenericStack[] inputs;
    private final boolean[] flags;
    private final List<Button> toggleButtons = new ArrayList<>();
    private int scrollOffset = 0;

    public CatalystEditScreen(ItemStack stack, InteractionHand hand) {
        super(Component.translatable("gui.ae2catalyst.title"));
        this.stack = stack;
        this.hand = hand;
        this.inputs = CatalystPatternDetails.condensedInputs(AEItemKey.of(stack));
        this.flags = CatalystPatternDetails.readCatalysts(stack);
    }

    private int visibleRows() {
        return Math.max(1, (this.height - TOP - 40) / ROW_HEIGHT);
    }

    private int contentX() {
        return this.width / 2 - 130;
    }

    @Override
    protected void init() {
        super.init();
        toggleButtons.clear();

        int visible = visibleRows();
        for (int row = 0; row < visible; row++) {
            int index = scrollOffset + row;
            if (index >= inputs.length) {
                break;
            }
            final int i = index;
            var button = Button.builder(toggleLabel(i), b -> {
                flags[i] = !flags[i];
                b.setMessage(toggleLabel(i));
            }).pos(contentX() + 190, TOP + row * ROW_HEIGHT).size(70, 20).build();
            toggleButtons.add(button);
            this.addRenderableWidget(button);
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.ae2catalyst.done"), b -> {
            Ae2Catalyst.CHANNEL.sendToServer(new SetCatalystsPacket(hand, flags));
            this.onClose();
        }).pos(this.width / 2 - 60, this.height - 32).size(120, 20).build());
    }

    private Component toggleLabel(int index) {
        return Component.translatable(flags[index] ? "gui.ae2catalyst.on" : "gui.ae2catalyst.off");
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxOffset = Math.max(0, inputs.length - visibleRows());
        int next = scrollOffset - (int) Math.signum(delta);
        next = Math.max(0, Math.min(maxOffset, next));
        if (next != scrollOffset) {
            scrollOffset = next;
            this.clearWidgets();
            this.init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("gui.ae2catalyst.hint"),
                this.width / 2, 28, 0xA0A0A0);

        int visible = visibleRows();
        for (int row = 0; row < visible; row++) {
            int index = scrollOffset + row;
            if (index >= inputs.length) {
                break;
            }
            int x = contentX();
            int y = TOP + row * ROW_HEIGHT;
            var input = inputs[index];

            if (input.what() instanceof AEItemKey itemKey) {
                graphics.renderItem(itemKey.toStack(), x, y + 2);
            }
            var label = input.what().getDisplayName().copy()
                    .append(Component.literal(" x" + input.amount()));
            graphics.drawString(this.font, label, x + 22, y + 6, 0xFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
