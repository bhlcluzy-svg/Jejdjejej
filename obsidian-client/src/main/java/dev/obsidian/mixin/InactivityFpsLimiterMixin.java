package dev.obsidian.mixin;

import dev.obsidian.gui.ObsidianTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.InactivityFpsLimiter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InactivityFpsLimiter.class)
public abstract class InactivityFpsLimiterMixin {
	@Shadow
	@Final
	private MinecraftClient client;
	@Shadow
	private int maxFps;

	@Shadow
	public abstract InactivityFpsLimiter.LimitReason getLimitReason();

	/** Vanilla caps menus at 60 FPS; the animated Obsidian menu runs at your normal max FPS. */
	@Inject(method = "update", at = @At("HEAD"), cancellable = true)
	private void obsidian$uncapMenu(CallbackInfoReturnable<Integer> cir) {
		if (client.currentScreen instanceof ObsidianTitleScreen
			&& getLimitReason() == InactivityFpsLimiter.LimitReason.OUT_OF_LEVEL_MENU) {
			cir.setReturnValue(maxFps);
		}
	}
}
