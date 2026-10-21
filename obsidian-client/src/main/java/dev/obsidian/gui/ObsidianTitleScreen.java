package dev.obsidian.gui;

import dev.obsidian.ObsidianClient;
import net.minecraft.SharedConstants;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The Obsidian main menu: an intro, the animated sunset scene, and
 * movable panels: the block logo, a button bar along the bottom, client info,
 * volume and the vanilla menu switch.
 */
public class ObsidianTitleScreen extends Screen {
	private static final float INTRO_LENGTH = 2.6f;
	/** The logo floats a little with the mouse, between the background and the buttons. */
	private static final float LOGO_DEPTH = 0.02f;

	private final List<Panel> panels = new ArrayList<>();
	private final SceneBackground scene = SceneBackground.INSTANCE;

	private LogoPanel logo;
	private BarPanel bar;
	private Panel dragging;
	private Panel contentDrag;
	private int dragOffX, dragOffY;

	private long lastFrame = -1;
	private float time;
	/** Seconds into the intro, or past INTRO_LENGTH when it is finished or skipped. */
	private float introT;
	private boolean introStarted;


	public ObsidianTitleScreen() {
		super(Text.literal(ObsidianClient.NAME));
		boolean play = ObsidianClient.CONFIG.playIntro && !ObsidianClient.introPlayed;
		introT = play ? 0f : INTRO_LENGTH;
	}

	@Override
	protected void init() {
		scene.prepare(width, height);
		panels.clear();
		logo = new LogoPanel(MathHelper.clamp(width * 0.072f, 22f, 40f));
		panels.add(logo);
		bar = new BarPanel(width);
		panels.add(bar);
		panels.add(new InfoPanel());
		panels.add(new VolumePanel());
		panels.add(new VanillaPanel());
		for (Panel p : panels) p.layout(width, height);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void removed() {
		ObsidianClient.CONFIG.save();
	}

	private boolean introRunning() {
		return introT < INTRO_LENGTH;
	}

	private void skipIntro() {
		introT = INTRO_LENGTH;
		ObsidianClient.introPlayed = true;
	}

	// ---------------------------------------------------------- background

	private void advanceClock() {
		long now = Util.getMeasuringTimeNano() / 1_000_000L;
		float dt = lastFrame < 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		time += dt;
		if (client.getOverlay() == null) introStarted = true;
		if (introStarted && introRunning()) {
			introT += dt;
			if (!introRunning()) ObsidianClient.introPlayed = true;
		}
	}

	@Override
	public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
		advanceClock();
		scene.render(ctx, width, height, mx, my);
		// Darken the bottom a little so the buttons and info read clearly.
		ctx.fillGradient(0, height * 3 / 5, width, height, 0x00000000, 0x90100A1E);
		float curtain = 1f - Theme.easeInOut((introT - 0.2f) / 1.3f);
		if (curtain > 0f) ctx.fill(0, 0, width, height, Theme.fade(0xFF000000, curtain));
	}

	// -------------------------------------------------------------- render

	@Override
	public void render(DrawContext ctx, int mx, int my, float delta) {
		float frame = Math.max(0.05f, Math.min(1f, delta));
		float others = Theme.easeOutCubic((introT - 1.9f) / 0.6f);

		for (Panel p : panels) {
			if (p == logo) continue;
			p.alpha = others;
			if (others < 1f) {
				ctx.getMatrices().pushMatrix();
				ctx.getMatrices().translate(0f, (1f - others) * 12f);
				p.render(ctx, textRenderer, mx, my, frame, false);
				ctx.getMatrices().popMatrix();
			} else {
				p.render(ctx, textRenderer, mx, my, frame, p == dragging);
			}
		}
		renderLogo(ctx);

	}

	/** The "Obsidian" title: smooth white letters that rise and fade in during the intro. */
	private void renderLogo(DrawContext ctx) {
		float appear = introStarted ? Theme.easeOutCubic((introT - 0.8f) / 1.0f) : 0f;
		if (appear <= 0.01f) return;
		float cx = logo.x + logo.w / 2f - scene.lookX() * LOGO_DEPTH * width;
		float y = logo.y - scene.lookY() * LOGO_DEPTH * width * 0.25f + (1f - appear) * 8f;
		Theme.glow(ctx, cx, y + logo.h / 2f, logo.w * 1.3f, Theme.fade(0xFFFFFFFF, 0.14f * appear));
		// A soft violet shadow, then the white letters.
		SmoothText.drawCentered(ctx, SmoothText.Face.TITLE, "Obsidian", cx, y + logo.size * 0.06f, logo.size, Theme.fade(0xFF4B3C86, 0.45f * appear), false);
		SmoothText.drawCentered(ctx, SmoothText.Face.TITLE, "Obsidian", cx, y, logo.size, Theme.fade(0xFFFFFFFF, appear), false);
		if (dragging == logo) {
			ctx.drawStrokedRectangle(logo.x - 1, logo.y - 1, logo.w + 2, logo.h + 2, Theme.fade(0xFFFFFFFF, 0.6f));
		}
	}

	// --------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (introRunning()) {
			skipIntro();
			return true;
		}
		double mx = click.x(), my = click.y();
		for (int i = panels.size() - 1; i >= 0; i--) {
			Panel p = panels.get(i);
			if (!p.contains(mx, my)) continue;
			if (click.button() == 1) {
				p.reset(width, height);
				ObsidianClient.CONFIG.save();
				return true;
			}
			if (click.button() != 0) return false;
			ObsidianButton b = p.buttonAt(mx, my);
			if (b != null) {
				b.press();
				return true;
			}
			if (p.clickContent(mx, my)) {
				contentDrag = p;
				return true;
			}
			dragging = p;
			dragOffX = (int) mx - p.x;
			dragOffY = (int) my - p.y;
			// Bring the grabbed panel to the front.
			panels.remove(i);
			panels.add(p);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (dragging != null) {
			dragging.moveTo((int) click.x() - dragOffX, (int) click.y() - dragOffY, width, height);
			return true;
		}
		if (contentDrag != null) return contentDrag.dragContent(click.x(), click.y());
		return false;
	}

	@Override
	public boolean mouseReleased(Click click) {
		if (dragging != null) {
			dragging.save(width, height);
			ObsidianClient.CONFIG.save();
			dragging = null;
			return true;
		}
		if (contentDrag != null) {
			contentDrag.releaseContent();
			contentDrag = null;
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (introRunning()) {
			skipIntro();
			return true;
		}
		return super.keyPressed(input);
	}

	// -------------------------------------------------------------- panels

	private class LogoPanel extends Panel {
		/** Title text size in GUI units. */
		final float size;

		LogoPanel(float size) {
			super("logo", Math.round(SmoothText.width(SmoothText.Face.TITLE, "Obsidian", size)), Math.round(size * 1.1f));
			this.size = size;
		}

		@Override
		protected int[] defaultPos(int sw, int sh) {
			// Below the corner panels, in the open sky above the middle.
			return new int[]{(sw - w) / 2, Math.max(34, Math.round(scene.logoCenterY() - h / 2f))};
		}

		@Override
		protected void renderContent(DrawContext ctx, TextRenderer tr, int mx, int my, float delta) {
			// Drawn by renderLogo, on top of the other panels.
		}
	}

	/** The row of small pill buttons along the bottom. */
	private class BarPanel extends Panel {
		private static final int ROW_H = 16, GAP = 5;
		private static final String[] LABELS = {"Singleplayer", "Multiplayer", "Mods", "Options", "Quit Game"};

		BarPanel(int screenW) {
			super("bar", 0, ROW_H + 2);
			List<Runnable> actions = List.of(
				() -> client.setScreen(new SelectWorldScreen(ObsidianTitleScreen.this)),
				() -> client.setScreen(new MultiplayerScreen(ObsidianTitleScreen.this)),
				() -> client.setScreen(ModsScreens.create(ObsidianTitleScreen.this)),
				() -> client.setScreen(new OptionsScreen(ObsidianTitleScreen.this, client.options)),
				() -> client.scheduleStop());

			// Size each pill to its label; tighten the padding if the row would not fit.
			int[] widths = new int[LABELS.length];
			int total = GAP * (LABELS.length - 1);
			for (int i = 0; i < LABELS.length; i++) {
				widths[i] = ObsidianButton.pillWidth(LABELS[i], ROW_H);
				total += widths[i];
			}
			int over = total - (screenW - 16);
			int shrink = over > 0 ? Math.min(10, (over + LABELS.length - 1) / LABELS.length) : 0;
			int x = 0;
			int rowY = 0;
			for (int i = 0; i < LABELS.length; i++) {
				String label = LABELS[i];
				int bw = widths[i] - shrink;
				buttons.add(new ObsidianButton(x, rowY, bw, ROW_H, () -> label, actions.get(i), ObsidianButton.Style.PILL));
				x += bw + GAP;
			}
			w = x - GAP;
		}

		@Override
		protected int[] defaultPos(int sw, int sh) {
			return new int[]{(sw - w) / 2, sh - h - 30};
		}

		@Override
		protected void renderContent(DrawContext ctx, TextRenderer tr, int mx, int my, float delta) {
		}
	}

	/** Client name, Minecraft version and mod count, small, in the bottom-left corner. */
	private class InfoPanel extends Panel {
		private static final float NAME_SIZE = 8.5f, LINE_SIZE = 6f;

		InfoPanel() {
			super("info", 0, 17);
			w = Math.round(Math.max(SmoothText.width(SmoothText.Face.UI, "OBSIDIAN CLIENT", NAME_SIZE), SmoothText.width(SmoothText.Face.UI, versionLine(), LINE_SIZE))) + 2;
		}

		private String versionLine() {
			int mods = ModsScreens.count();
			return "MINECRAFT " + SharedConstants.getGameVersion().name() + "  /  " + mods + (mods == 1 ? " MOD" : " MODS");
		}

		@Override
		protected int[] defaultPos(int sw, int sh) {
			return new int[]{6, sh - h - 4};
		}

		@Override
		protected void renderContent(DrawContext ctx, TextRenderer tr, int mx, int my, float delta) {
			SmoothText.draw(ctx, SmoothText.Face.UI, "OBSIDIAN CLIENT", x, y, NAME_SIZE, Theme.fade(0xFFFFFFFF, alpha), true);
			SmoothText.draw(ctx, SmoothText.Face.UI, versionLine(), x, y + NAME_SIZE + 2f, LINE_SIZE, Theme.fade(0xFFDAD5EE, alpha), true);
		}
	}

	private class VolumePanel extends Panel {
		private static final int TRACK_X = 40, TRACK_RIGHT = 26;
		private boolean sliding;

		VolumePanel() {
			super("volume", 110, 14);
			buttons.add(new ObsidianButton(2, 1, 34, 12, () -> ObsidianClient.CONFIG.muted ? "Muted" : "Music", () -> {
				ObsidianClient.CONFIG.muted = !ObsidianClient.CONFIG.muted;
				ObsidianClient.CONFIG.save();
			}, ObsidianButton.Style.ICON));
		}

		@Override
		protected int[] defaultPos(int sw, int sh) {
			return new int[]{sw - w - 5, 5};
		}

		private int trackStart() {
			return x + TRACK_X;
		}

		private int trackEnd() {
			return x + w - TRACK_RIGHT;
		}

		private void setFromMouse(double mx) {
			float v = (float) ((mx - trackStart()) / (trackEnd() - trackStart()));
			ObsidianClient.CONFIG.musicVolume = MathHelper.clamp(v, 0f, 1f);
			ObsidianClient.CONFIG.muted = false;
		}

		@Override
		public boolean clickContent(double mx, double my) {
			if (mx >= trackStart() - 3 && mx <= trackEnd() + 3) {
				sliding = true;
				setFromMouse(mx);
				return true;
			}
			return false;
		}

		@Override
		public boolean dragContent(double mx, double my) {
			if (sliding) setFromMouse(mx);
			return sliding;
		}

		@Override
		public void releaseContent() {
			sliding = false;
			ObsidianClient.CONFIG.save();
		}

		@Override
		protected void renderContent(DrawContext ctx, TextRenderer tr, int mx, int my, float delta) {
			Theme.pill(ctx, x, y, w, h, Theme.fade(0xC02B2D45, alpha));
			int ty = y + h / 2;
			int s = trackStart(), e = trackEnd();
			float v = ObsidianClient.CONFIG.effectiveVolume();
			int knob = s + Math.round((e - s) * v);
			Theme.pill(ctx, s, ty - 1f, e - s, 2, Theme.fade(0x60FFFFFF, alpha));
			Theme.pill(ctx, s, ty - 1f, knob - s, 2, Theme.fade(0xFFFFFFFF, alpha));
			Theme.pill(ctx, knob - 3, ty - 3, 6, 6, Theme.fade(0xFFFFFFFF, alpha));
			String pct = Math.round(v * 100) + "%";
			SmoothText.draw(ctx, SmoothText.Face.UI, pct, x + w - 6 - SmoothText.width(SmoothText.Face.UI, pct, 5.5f), y + (h - 5.5f) / 2f, 5.5f, Theme.fade(0xFFE4E2F2, alpha), false);
		}
	}

	private class VanillaPanel extends Panel {
		VanillaPanel() {
			super("vanilla", Math.round(SmoothText.width(SmoothText.Face.UI, "VANILLA MENU", 6.6f)) + 2, 11);
			buttons.add(new ObsidianButton(0, 0, w, 11, () -> "Vanilla Menu", () -> {
				ObsidianClient.CONFIG.vanillaMenu = true;
				ObsidianClient.CONFIG.save();
				client.setScreen(new TitleScreen());
			}, ObsidianButton.Style.TEXT));
		}

		@Override
		protected int[] defaultPos(int sw, int sh) {
			return new int[]{sw - w - 6, sh - h - 5};
		}

		@Override
		protected void renderContent(DrawContext ctx, TextRenderer tr, int mx, int my, float delta) {
		}
	}
}
