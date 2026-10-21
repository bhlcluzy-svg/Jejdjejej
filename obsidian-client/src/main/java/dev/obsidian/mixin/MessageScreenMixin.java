package dev.obsidian.mixin;

import dev.obsidian.gui.JoinScreens;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.NarratedMultilineTextWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Short messages like "Saving world" in the Obsidian style. */
@Mixin(MessageScreen.class)
public abstract class MessageScreenMixin extends Screen {
	@Shadow
	private NarratedMultilineTextWidget textWidget;

	protected MessageScreenMixin(Text title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void obsidian$hideText(CallbackInfo ci) {
		if (JoinScreens.enabled() && textWidget != null) textWidget.visible = false;
	}

	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
	private void obsidian$background(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
		if (!JoinScreens.enabled()) return;
		JoinScreens.background(context, width, height);
		JoinScreens.status(context, width, height, title.getString(), -1f);
		ci.cancel();
	}
}
