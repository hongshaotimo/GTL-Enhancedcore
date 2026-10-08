package com.gtl.enhancedcore.common.recipe.iv;

import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import java.util.function.BooleanSupplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/** Per-open widget state sent by the server, including initial state and live visibility. */
public final class IvActionButton extends ButtonWidget {
    private final BooleanSupplier shown, selected;
    private final String onKey, offKey;
    private final boolean toggle;
    private boolean on;
    private boolean displayed;

    public IvActionButton(int x,int y,int width,int height,BooleanSupplier shown,BooleanSupplier selected,
                          String onKey,String offKey,String help,boolean toggle,Runnable action) {
        super(x,y,width,height,click->{});
        this.shown=shown;this.selected=selected;this.onKey=onKey;this.offKey=offKey;this.toggle=toggle;
        setOnPressCallback(click->{
            if(!click.isRemote && shown.getAsBoolean()) { action.run(); detectAndSendChanges(); }
        });
        setButtonTexture(new TextTexture(()->Component.translatable(on?onKey:offKey).getString()) {
            @Override public void updateTick() {
                setBackgroundColor(toggle?(on?0xFF285A38:0xFF805A22):0xFF803838);
                super.updateTick();
            }
        });
        setHoverTooltips(help);setVisible(false);
    }
    private void apply(boolean visible,boolean selected) {
        // LDLib stops polling inactive children. Hidden buttons must remain active to reappear.
        displayed=visible;on=selected;setVisible(visible);
    }
    @Override public void writeInitialData(FriendlyByteBuf buf) {
        super.writeInitialData(buf);apply(shown.getAsBoolean(),selected.getAsBoolean());
        buf.writeBoolean(displayed);buf.writeBoolean(on);
    }
    @Override public void readInitialData(FriendlyByteBuf buf) {
        super.readInitialData(buf);apply(buf.readBoolean(),buf.readBoolean());
    }
    @Override public void detectAndSendChanges() {
        super.detectAndSendChanges();
        boolean visible=shown.getAsBoolean(),enabled=selected.getAsBoolean();
        if(visible!=displayed || enabled!=on) {
            apply(visible,enabled);writeUpdateInfo(101,buf->{buf.writeBoolean(displayed);buf.writeBoolean(on);});
        }
    }
    @Override public void readUpdateInfo(int id,FriendlyByteBuf buf) {
        if(id==101)apply(buf.readBoolean(),buf.readBoolean());else super.readUpdateInfo(id,buf);
    }
    public boolean isAcceptingShown(){return on;}
}
