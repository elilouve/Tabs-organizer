package com.tabsorganizer.client;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A small screen to type a new name for a tab. It opens over the creative screen and goes back to it. */
public final class RenameScreen extends Screen {
    private final Screen parent;
    private final String initial;
    private final Consumer<String> onDone;
    private EditBox box;

    public RenameScreen(Screen parent, String initial, Consumer<String> onDone) {
        super(Component.translatable("tabsorganizer.rename.title"));
        this.parent = parent;
        this.initial = initial;
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int top = this.height / 2 - 30;
        this.box = new EditBox(this.font, centerX - 100, top + 14, 200, 20, this.title);
        this.box.setMaxLength(64);
        this.box.setValue(this.initial);
        this.addRenderableWidget(this.box);
        this.setInitialFocus(this.box);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.finish())
                .bounds(centerX - 100, top + 44, 64, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("tabsorganizer.rename.default"), b -> {
            this.box.setValue("");
            this.finish();
        }).bounds(centerX - 32, top + 44, 64, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.onClose())
                .bounds(centerX + 36, top + 44, 64, 20).build());
    }

    private void finish() {
        this.onDone.accept(this.box.getValue());
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter
            this.finish();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawString(this.font, this.title, this.width / 2 - 100, this.height / 2 - 30, 0xFFFFFFFF, true);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
