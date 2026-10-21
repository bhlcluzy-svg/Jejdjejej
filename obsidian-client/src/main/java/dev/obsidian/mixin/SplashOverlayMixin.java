package dev.obsidian.mixin;

import dev.obsidian.ObsidianClient;
import dev.obsidian.gui.LaunchScreen;
import dev.obsidian.gui.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The loading screen. When the game starts, the Obsidian launch screen is drawn
 * over it (and fades into the menu when loading is done). Resource reloads later
 * on keep Minecraft's screen, recoloured: near-black and a violet bar.
 */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {
	@Shadow
	@Final
	private boolean reloading;
	@Shadow
	private float progress;
	@Shadow
	private long reloadCompleteTime;

	@Unique
	private boolean obsidian$showLaunchScreen() {
		return !reloading && ObsidianClient.CONFIG != null && !ObsidianClient.CONFIG.vanillaMenu;
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void obsidian$launchScreen(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
		if (!obsidian$showLaunchScreen()) return;
		float doneFor = reloadCompleteTime > -1L ? (Util.getMeasuringTimeMs() - reloadCompleteTime) / 1000f : -1f;
		LaunchScreen.render(context, progress, doneFor);
	}

	/** Hold "loading finished" until the launch animation has played, so a fast load does not cut it short. */
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void obsidian$holdForAnimation(CallbackInfo ci) {
		if (obsidian$showLaunchScreen() && reloadCompleteTime == -1L && !LaunchScreen.introDone()) ci.cancel();
	}

	/** Hide the Mojang logo under the launch screen, or it shows through while fading out. */
	@ModifyArg(
		method = "render",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIIIIII)V"),
		index = 12
	)
	private int obsidian$hideLogo(int color) {
		return obsidian$showLaunchScreen() ? color & 0x00FFFFFF : color;
	}

	@Inject(method = "renderProgressBar", at = @At("HEAD"), cancellable = true)
	private void obsidian$hideBar(CallbackInfo ci) {
		if (obsidian$showLaunchScreen()) ci.cancel();
	}

	@ModifyArg(
		method = "render",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"),
		index = 4
	)
	private int obsidian$backgroundFill(int color) {
		return (color & 0xFF000000) | (Theme.LOADING_BG & 0x00FFFFFF);
	}

	@ModifyArg(
		method = "render",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearColorTexture(Lcom/mojang/blaze3d/textures/GpuTexture;I)V"),
		index = 1
	)
	private int obsidian$backgroundClear(int color) {
		return Theme.LOADING_BG;
	}

	@ModifyArg(
		method = "renderProgressBar",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"),
		index = 4
	)
	private int obsidian$progressBar(int color) {
		return (color & 0xFF000000) | (Theme.ACCENT & 0x00FFFFFF);
	}
}
