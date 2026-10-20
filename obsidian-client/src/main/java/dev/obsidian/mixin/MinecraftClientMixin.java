package dev.obsidian.mixin;

import dev.obsidian.ObsidianClient;
import dev.obsidian.gui.ObsidianTitleScreen;
import dev.obsidian.sound.MenuMusic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	/** Swap the vanilla title screen for ours, unless the player picked the vanilla menu. */
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen obsidian$replaceTitleScreen(Screen screen) {
		if (screen instanceof TitleScreen && ObsidianClient.CONFIG != null && !ObsidianClient.CONFIG.vanillaMenu) {
			return new ObsidianTitleScreen();
		}
		return screen;
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void obsidian$tickMusic(CallbackInfo ci) {
		MenuMusic.tick((MinecraftClient) (Object) this);
	}
}
