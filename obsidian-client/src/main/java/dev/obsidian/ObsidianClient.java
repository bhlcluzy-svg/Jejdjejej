package dev.obsidian;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObsidianClient implements ClientModInitializer {
	public static final String MOD_ID = "obsidian";
	public static final String NAME = "Obsidian";
	public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

	public static ObsidianConfig CONFIG;
	public static String VERSION = "?";

	/** The intro only plays once per launch. */
	public static boolean introPlayed = false;

	@Override
	public void onInitializeClient() {
		CONFIG = ObsidianConfig.load();
		VERSION = FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString())
			.orElse("?");
		LOGGER.info("{} {} loaded", NAME, VERSION);
	}
}
