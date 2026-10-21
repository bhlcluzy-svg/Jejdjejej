package dev.obsidian.mixin;

import dev.obsidian.gui.JoinScreens;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProgressScreen.class)
public abstract class ProgressScreenMixin extends Screen {
	@Shadow
	private boolean done;

	protected ProgressScreenMixin(Text title) {
		super(title);
	}

	/** Progress messages (like preparing a world) show the Obsidian spinner. When done, vanilla closes the screen as usual. */
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void obsidian$status(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
		if (done || !JoinScreens.enabled()) return;
		JoinScreens.spinner(context, width, height);
		ci.cancel();
	}
}
