package dev.obsidian.mixin;

import dev.obsidian.gui.JoinScreens;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.world.ClientChunkLoadProgress;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "Loading terrain..." when joining a world or server, in the Obsidian style.
 * Nether and End portal loading keep Minecraft's own portal animation.
 */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {
	@Shadow
	@Final
	private static Text DOWNLOADING_TERRAIN_TEXT;
	@Shadow
	private ClientChunkLoadProgress chunkLoadProgress;
	@Shadow
	private float loadProgress;
	@Shadow
	private LevelLoadingScreen.WorldEntryReason reason;

	protected LevelLoadingScreenMixin(Text title) {
		super(title);
	}

	@Unique
	private boolean obsidian$styled() {
		return JoinScreens.enabled() && reason == LevelLoadingScreen.WorldEntryReason.OTHER;
	}

	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
	private void obsidian$background(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
		if (!obsidian$styled()) return;
		JoinScreens.background(context, width, height);
		ci.cancel();
	}

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void obsidian$status(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
		if (!obsidian$styled()) return;
		JoinScreens.status(context, width, height, DOWNLOADING_TERRAIN_TEXT.getString(), chunkLoadProgress.hasProgress() ? loadProgress : -1f);
		ci.cancel();
	}
}
