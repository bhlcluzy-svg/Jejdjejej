package dev.obsidian.mixin;

import dev.obsidian.gui.JoinScreens;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin extends Screen {
	protected ConnectScreenMixin(Text title) {
		super(title);
	}

	/** "Connecting to the server..." and the other statuses, drawn in the Obsidian style. */
	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"))
	private void obsidian$status(DrawContext context, TextRenderer textRenderer, Text text, int x, int y, int color) {
		if (JoinScreens.enabled()) {
			JoinScreens.status(context, width, height, text.getString(), -1f);
		} else {
			context.drawCenteredTextWithShadow(textRenderer, text, x, y, color);
		}
	}
}
