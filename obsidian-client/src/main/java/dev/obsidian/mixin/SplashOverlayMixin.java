package dev.obsidian.mixin;

import dev.obsidian.gui.Theme;
import net.minecraft.client.gui.screen.SplashOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Recolours the loading screen: near-black instead of Mojang red, and a violet
 * progress bar. Our own textures and fonts are not loaded yet at this point, so
 * the Mojang logo stays; the Obsidian intro plays once loading finishes.
 */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {
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
