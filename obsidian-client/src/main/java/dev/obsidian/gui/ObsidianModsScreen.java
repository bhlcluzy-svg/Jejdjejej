package dev.obsidian.gui;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/** A plain list of installed mods, used when Mod Menu is not installed. */
public class ObsidianModsScreen extends Screen {
	private static final int ROW = 22;
	private final Screen parent;
	private final List<ModContainer> mods = ModsScreens.userMods();
	private double scroll;

	public ObsidianModsScreen(Screen parent) {
		super(Text.literal("Mods"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int y = height - 28;
		addDrawableChild(ButtonWidget.builder(Text.literal("Open Mods Folder"),
				b -> Util.getOperatingSystem().open(FabricLoader.getInstance().getGameDir().resolve("mods")))
			.dimensions(width / 2 - 154, y, 150, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
			.dimensions(width / 2 + 4, y, 150, 20).build());
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	private int listTop() {
		return 32;
	}

	private int listBottom() {
		return height - 36;
	}

	private int maxScroll() {
		return Math.max(0, mods.size() * ROW - (listBottom() - listTop()));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll = MathHelper.clamp(scroll - verticalAmount * ROW, 0, maxScroll());
		return true;
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fillGradient(0, 0, width, height, Theme.BG_TOP, Theme.BG_BOTTOM);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.drawCenteredTextWithShadow(textRenderer, mods.size() + " mods installed", width / 2, 12, Theme.TEXT);

		int w = Math.min(320, width - 20);
		int x = (width - w) / 2;
		int top = listTop(), bottom = listBottom();
		Theme.panel(ctx, x - 4, top - 4, w + 8, bottom - top + 8, Theme.PANEL, Theme.PANEL_BORDER);
		ctx.enableScissor(x, top, x + w, bottom);
		int y = top - (int) scroll;
		for (ModContainer mod : mods) {
			if (y + ROW > top && y < bottom) {
				ModMetadata meta = mod.getMetadata();
				boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW - 2 && mouseY >= top && mouseY < bottom;
				ctx.fill(x, y, x + w, y + ROW - 2, over ? Theme.BUTTON_HOVER : Theme.BUTTON);
				ctx.fill(x, y, x + 2, y + ROW - 2, Theme.ACCENT);
				ctx.drawTextWithShadow(textRenderer, meta.getName(), x + 7, y + 3, Theme.TEXT);
				String version = meta.getVersion().getFriendlyString();
				ctx.drawTextWithShadow(textRenderer, version, x + w - 5 - textRenderer.getWidth(version), y + 3, Theme.TEXT_DIM);
				String desc = textRenderer.trimToWidth(meta.getDescription().replace('\n', ' '), w - 12);
				ctx.drawTextWithShadow(textRenderer, desc, x + 7, y + 12, 0xFF6F6894);
			}
			y += ROW;
		}
		ctx.disableScissor();
		super.render(ctx, mouseX, mouseY, delta);
	}
}
