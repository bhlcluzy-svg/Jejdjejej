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
	private Text title;
	@Shadow
	private Text task;
	@Shadow
	private int progress;
	@Shadow
	private boolean done;

	protected ProgressScreenMixin(Text title) {
		super(title);
	}

	/** Progress messages (like preparing a world) in the Obsidian style. When done, vanilla closes the screen as usual. */
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void obsidian$status(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
		if (done || !JoinScreens.enabled()) return;
		String text = title != null ? title.getString() : task != null ? task.getString() : "";
		JoinScreens.status(context, width, height, text, task != null && progress > 0 ? progress / 100f : -1f);
		ci.cancel();
	}
}
