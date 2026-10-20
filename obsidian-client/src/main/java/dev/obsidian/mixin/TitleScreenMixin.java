package dev.obsidian.mixin;

import dev.obsidian.ObsidianClient;
import dev.obsidian.gui.ObsidianTitleScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
	protected TitleScreenMixin(Text title) {
		super(title);
	}

	/** A way back from the vanilla menu to the Obsidian one. */
	@Inject(method = "init", at = @At("TAIL"))
	private void obsidian$addBackButton(CallbackInfo ci) {
		this.addDrawableChild(ButtonWidget.builder(Text.literal("◆ Obsidian"), button -> {
			ObsidianClient.CONFIG.vanillaMenu = false;
			ObsidianClient.CONFIG.save();
			this.client.setScreen(new ObsidianTitleScreen());
		}).dimensions(this.width - 86, this.height - 26, 80, 20).build());
	}
}
