package dev.furkan.simpletradereroll.mixin;

import dev.furkan.simpletradereroll.SimpleTradeRerollClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class MerchantTextInputMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void simpleTradeReroll$keepTyping(KeyEvent event, CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof MerchantScreen
                && SimpleTradeRerollClient.handleFocusedTextKey((Screen) (Object) this, event)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void simpleTradeReroll$protectCompactPanel(MouseButtonEvent event, boolean doubleClick,
            CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof MerchantScreen
                && SimpleTradeRerollClient.handleCompactPanelClick((Screen) (Object) this, event)) {
            callback.setReturnValue(true);
        }
    }
}
