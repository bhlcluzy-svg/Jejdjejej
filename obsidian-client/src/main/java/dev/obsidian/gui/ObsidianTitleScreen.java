package dev.obsidian.gui;

import dev.obsidian.ObsidianClient;
import dev.obsidian.ObsidianConfig;
import net.minecraft.SharedConstants;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
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
	/** When the menu became usable, for fading out the hint. */
	private float readyAt = -1f;


	public ObsidianTitleScreen() {
		super(Text.literal(ObsidianClient.NAME));
		boolean play = ObsidianClient.CONFIG.playIntro && !ObsidianClient.introPlayed;
		introT = play ? 0f : INTRO_LENGTH;
	}

	@Override
	protected void init() {
		scene.prepare(width, height);
		panels.clear();
		logo = new LogoPanel(MathHelper.clamp(width * 0.42f, 150f, 360f));
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

		if (!introRunning()) {
			if (readyAt < 0f) readyAt = time;
			// Shown for a few seconds, then it gets out of the way.
			float hintAlpha = 1f - MathHelper.clamp((time - readyAt - 5f) / 1.5f, 0f, 1f);
			if (hintAlpha > 0.02f) {
				String hint = "Drag panels to move them  \u00B7  Right-click a panel to reset it";
				ctx.drawCenteredTextWithShadow(textRenderer, hint, width / 2, Math.max(2, bar.y - 12), Theme.fade(Theme.TEXT, 0.7f * hintAlpha));
			}
		}
	}

	/** The "Obsidian" title: soft white letters that rise and fade in during the intro. */
	private void renderLogo(DrawContext ctx) {
		float appear = introStarted ? Theme.easeOutCubic((introT - 0.8f) / 1.0f) : 0f;
		if (appear <= 0.01f) return;
		float cx = logo.x + logo.w / 2f - scene.lookX() * LOGO_DEPTH * width;
		float cy = logo.y + logo.h / 2f - scene.lookY() * LOGO_DEPTH * width * 0.25f + (1f - appear) * 10f;
		Text title = Theme.title("Obsidian");
		float tw = textRenderer.getWidth(title);
		float scale = logo.textScale;

		Theme.glow(ctx, cx, cy, logo.w * 1.3f, Theme.fade(0xFFFFFFFF, 0.16f * appear));
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(cx, cy);
		ctx.getMatrices().scale(scale, scale);
		// A soft violet drop shadow, then the white letters.
		ctx.drawText(textRenderer, title, Math.round(-tw / 2f), -20 + 2, Theme.fade(0xFF5B4A9A, 0.55f * appear), false);
		ctx.drawText(textRenderer, title, Math.round(-tw / 2f), -20, Theme.fade(0xFFFFFFFF, appear), false);
		ctx.getMatrices().popMatrix();
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
		/** Scale for the 40px title font so it spans the panel. */
		final float textScale;

		LogoPanel(float targetWidth) {
			super("logo", Math.round(targetWidth), 0);
			textScale = targetWidth / Math.max(1, textRenderer.getWidth(Theme.title("Obsidian")));
			h = Math.round(44 * textScale);
		}

		@Override
		protected int[] defaultPos(int sw, int sh) {
			// Below the corner panels, in the open sky above the middle.
			return new int[]{(sw - w) / 2, Math.max(40, Math.round(scene.logoCenterY() - h / 2f))};
		}

		@Override
		protected void renderContent(DrawContext ctx, TextRenderer tr, int mx, int my, float delta) {
			// Drawn by renderLogo, on top of the other panels.
		}
	}

	/**
	 * The row of slim pill buttons along the bottom. When you have played on a
	 * server, a small "Rejoin" pill sits above the row.
	 */
	private class BarPanel extends Panel {
		private static final int ROW_H = 20, GAP = 6, QUICK_H = 15;
		private static final String[] LABELS = {"Singleplayer", "Multiplayer", "Mods", "Options", "Quit Game"};

		BarPanel(int screenW) {
			super("bar", 0, QUICK_H + 6 + ROW_H + 2);
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
				widths[i] = ObsidianButton.pillWidth(textRenderer, LABELS[i]);
				total += widths[i];
			}
			int over = total - (screenW - 16);
			int shrink = over > 0 ? Math.min(14, (over + LABELS.length - 1) / LABELS.length) : 0;
			int x = 0;
			int rowY = QUICK_H + 6;
			for (int i = 0; i < LABELS.length; i++) {
				String label = LABELS[i];
				int bw = widths[i] - shrink;
				buttons.add(new ObsidianButton(x, rowY, bw, ROW_H, () -> label, actions.get(i), ObsidianButton.Style.PILL));
				x += bw + GAP;
			}
			w = x - GAP;

			String server = ObsidianClient.CONFIG.lastServerName;
			if (server != null && !server.isBlank() && ObsidianClient.CONFIG.lastServerAddress != null) {
				String label = "Rejoin " + (server.length() > 24 ? server.substring(0, 23) + "\u2026" : server);
				int qw = Math.min(w, ObsidianButton.pillWidth(textRenderer, label) - 6);
				buttons.add(new ObsidianButton((w - qw) / 2, 0, qw, QUICK_H, () -> label, ObsidianTitleScreen.this::quickJoin, ObsidianButton.Style.PILL));
			}
		}

		@Override
		protected int[] defaultPos(int sw, int sh) {
			return new int[]{(sw - w) / 2, sh - h - 40};
		}

		@Override
		protected void renderContent(DrawContext ctx, TextRenderer tr, int mx, int my, float delta) {
		}
	}

	private void quickJoin() {
		ObsidianConfig config = ObsidianClient.CONFIG;
		if (config.lastServerAddress == null || config.lastServerAddress.isBlank()) {
			client.setScreen(new MultiplayerScreen(this));
			return;
		}
		ServerInfo info = new ServerInfo(config.lastServerName, config.lastServerAddress, ServerInfo.ServerType.OTHER);
		ConnectScreen.connect(this, client, ServerAddress.parse(info.address), info, false, null);
	}

	/** Client name, Minecraft version and mod count, bottom left, as plain text. */
	private class InfoPanel extends Panel {
		private static final float NAME_SCALE = 1.6f;
		private final Text name = Theme.ui("OBSIDIAN CLIENT");

		InfoPanel() {
			super("info", 0, 28);
			w = Math.max(Math.round(textRenderer.getWidth(name) * NAME_SCALE), textRenderer.getWidth(versionLine())) + 4;
		}

		private Text versionLine() {
			int mods = ModsScreens.count();
			return Theme.ui("MINECRAFT " + SharedConstants.getGameVersion().name() + "  /  " + mods + (mods == 1 ? " MOD" : " MODS"));
		}

		@Override
		protected int[] defaultPos(int sw, int sh) {
			return new int[]{10, sh - h - 6};
		}

		@Override
		protected void renderContent(DrawContext ctx, TextRenderer tr, int mx, int my, float delta) {
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(x, y);
			ctx.getMatrices().scale(NAME_SCALE, NAME_SCALE);
			ctx.drawText(tr, name, 0, 0, Theme.fade(0xFFFFFFFF, alpha), true);
			ctx.getMatrices().popMatrix();
			ctx.drawText(tr, versionLine(), x, y + 17, Theme.fade(0xFFD5CFEA, alpha), true);
		}
	}

	private class VolumePanel extends Panel {
		private static final int TRACK_X = 22, TRACK_RIGHT = 36;
		private boolean sliding;

		VolumePanel() {
			super("volume", 120, 20);
			buttons.add(new ObsidianButton(3, 3, 14, 14, () -> ObsidianClient.CONFIG.muted ? "×" : "♪", () -> {
				ObsidianClient.CONFIG.muted = !ObsidianClient.CONFIG.muted;
				ObsidianClient.CONFIG.save();
			}, ObsidianButton.Style.ICON));
		}

		@Override
		protected int[] defaultPos(int sw, int sh) {
			return new int[]{sw - w - 6, 6};
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
			Theme.pill(ctx, s, ty - 1.5f, e - s, 3, Theme.fade(0x60FFFFFF, alpha));
			Theme.pill(ctx, s, ty - 1.5f, knob - s, 3, Theme.fade(0xFFFFFFFF, alpha));
			Theme.pill(ctx, knob - 4, ty - 4, 8, 8, Theme.fade(0xFFFFFFFF, alpha));
			Text pct = Theme.ui(Math.round(v * 100) + "%");
			ctx.drawText(tr, pct, x + w - 8 - tr.getWidth(pct), y + 5, Theme.fade(0xFFE4E2F2, alpha), true);
		}
	}

	private class VanillaPanel extends Panel {
		VanillaPanel() {
			super("vanilla", Math.round(textRenderer.getWidth(Theme.ui("VANILLA MENU")) * 1.25f) + 2, 16);
			buttons.add(new ObsidianButton(0, 0, w, 16, () -> "Vanilla Menu", () -> {
				ObsidianClient.CONFIG.vanillaMenu = true;
				ObsidianClient.CONFIG.save();
				client.setScreen(new TitleScreen());
			}, ObsidianButton.Style.TEXT));
		}

		@Override
		protected int[] defaultPos(int sw, int sh) {
			return new int[]{sw - w - 8, sh - h - 8};
		}

		@Override
		protected void renderContent(DrawContext ctx, TextRenderer tr, int mx, int my, float delta) {
		}
	}
}
