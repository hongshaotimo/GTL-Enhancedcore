package com.gtl.enhancedcore.common.gui;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gtl.enhancedcore.common.recipe.iv.SuperBufferAutoName;
import com.gtl.enhancedcore.common.recipe.iv.SuperBufferNameAccess;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/** Automatic names are always Chinese; manual drafts stay in each player's UI. */
public final class SuperBufferNameWidget extends WidgetGroup {
    private static final int NAME_UPDATE = 20, RENAME = 21, RESTORE = 22;
    private final MEPatternBufferPartMachine machine;
    private final TextFieldWidget editor;
    private NameSnapshot snapshot;
    private boolean editing;
    private String draft = "", editBase = "";

    public record NameSnapshot(String stored, boolean automatic) {
        public Component display() {
            return Component.literal(automatic ? SuperBufferAutoName.displayName(stored) : stored);
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(stored);
            buffer.writeBoolean(automatic);
        }

        public static NameSnapshot read(FriendlyByteBuf buffer) {
            return new NameSnapshot(buffer.readUtf(), buffer.readBoolean());
        }
    }

    public SuperBufferNameWidget(int x, int y, int width, int height, MEPatternBufferPartMachine machine) {
        super(x - height - 2, y, width + height + 2, height);
        this.machine = machine;
        snapshot = currentName();
        editor = new TextFieldWidget(height + 2, 0, width - height - 2, height,
                () -> draft, value -> draft = value).setMaxStringLength(256);
        editor.setClientSideWidget();
        editor.setActive(false);
        editor.setVisible(false);
        addWidget(editor);
        var edit = new ButtonWidget(width + 2, 0, height, height,
                new GuiTextureGroup(GuiTextures.VANILLA_BUTTON,
                        new TextTexture(() -> editing ? "\u2714" : "\u270e")), click -> {
                    if (!click.isRemote) return;
                    if (editing) {
                        writeClientAction(RENAME, buffer -> {
                            buffer.writeUtf(editBase);
                            buffer.writeUtf(draft, 256);
                        });
                    } else {
                        editBase = snapshot.stored();
                        draft = snapshot.display().getString();
                        editor.setCurrentString(draft);
                    }
                    editing = !editing;
                    editor.setActive(editing);
                    editor.setVisible(editing);
                });
        edit.setClientSideWidget();
        edit.setHoverTooltips(Component.translatable("gui.gtl_enhancedcore.buffer_name_edit"));
        addWidget(edit);
        var restore = new ButtonWidget(0, 0, height, height,
                new GuiTextureGroup(GuiTextures.VANILLA_BUTTON, GuiTextures.BUTTON_CLEAR_GRID), click -> {
                    if (click.isRemote) {
                        writeClientAction(RESTORE, buffer -> buffer.writeUtf(snapshot.stored()));
                        editing = false;
                        editor.setActive(false);
                        editor.setVisible(false);
                    }
                });
        restore.setClientSideWidget();
        restore.setHoverTooltips(Component.translatable("gui.gtl_enhancedcore.buffer_name_restore"));
        addWidget(restore);
    }

    private NameSnapshot currentName() {
        var names = (SuperBufferNameAccess) (Object) machine;
        String stored = machine.getCustomName();
        if (stored == null) stored = "";
        return new NameSnapshot(stored, SuperBufferAutoName.shouldTranslate(stored,
                names.enhanced$getAutomaticName(), names.enhanced$isManualName()));
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        snapshot = currentName();
        snapshot.write(buffer);
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        snapshot = NameSnapshot.read(buffer);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        var current = currentName();
        if (!current.equals(snapshot)) {
            snapshot = current;
            writeUpdateInfo(NAME_UPDATE, snapshot::write);
        }
    }

    @Override
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id == NAME_UPDATE) snapshot = NameSnapshot.read(buffer);
        else super.readUpdateInfo(id, buffer);
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (id != RENAME && id != RESTORE) {
            super.handleClientAction(id, buffer);
            return;
        }
        if (machine.isRemote()) return;
        String expected = buffer.readUtf();
        String renamed = id == RENAME ? buffer.readUtf(256) : null;
        // A second open menu must not silently overwrite a newer player's edit.
        if (expected.equals(machine.getCustomName())) {
            if (id == RENAME) machine.setCustomName(renamed);
            else if (!((SuperBufferNameAccess) (Object) machine).enhanced$restoreAutomaticName()) {
                notifyPlayer("message.gtl_enhancedcore.buffer_name_unavailable");
            }
        } else {
            notifyPlayer("message.gtl_enhancedcore.buffer_name_changed");
        }
        snapshot = currentName();
        writeUpdateInfo(NAME_UPDATE, snapshot::write);
    }

    private void notifyPlayer(String key) {
        if (gui != null && gui.entityPlayer != null) {
            gui.entityPlayer.displayClientMessage(Component.translatable(key), true);
        }
    }
}
