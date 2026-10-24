package dev.furkan.simpletradereroll.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenPositionAccessor {
    @Accessor("leftPos")
    int simpleTradeReroll$getLeftPos();

    @Accessor("topPos")
    int simpleTradeReroll$getTopPos();

    @Accessor("imageWidth")
    int simpleTradeReroll$getImageWidth();
}
