package dev.obsidian.mixin;

import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(SoundSystem.class)
public interface SoundSystemAccessor {
	/** The OpenAL source playing each sound, so a single sound can be paused and resumed. */
	@Accessor("sources")
	Map<SoundInstance, Channel.SourceManager> obsidian$getSources();
}
