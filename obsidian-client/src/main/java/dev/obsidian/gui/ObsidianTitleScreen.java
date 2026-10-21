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
		logo = new LogoPanel(BlockLogo.forWidth(MathHelper.clamp(width * 0.58f, 200f, 440f)));
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

	private void renderLogo(DrawContext ctx) {
		// The blocks start dropping as the scene fades in.
		float drop = introStarted ? introT - 0.7f : -1f;
		if (!introRunning()) drop = 100f;
		float x = logo.x + (logo.w - logo.blocks.width()) / 2f - scene.lookX() * LOGO_DEPTH * width;
		float y = logo.y + (logo.h - logo.blocks.height()) / 2f - scene.lookY() * LOGO_DEPTH * width * 0.25f;
		logo.blocks.render(ctx, x, y, drop, time);
		if (dragging == logo) {
			ctx.drawStrokedRectangle(logo.x - 1, logo.y - 1, logo.w + 2, logo.h + 2, Theme.fade(Theme.ACCENT, 0.8f));
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
		final BlockLogo blocks;

		LogoPanel(BlockLogo blocks) {
			// Room for the 3D side and outline around the blocks.
			super("logo", Math.round(blocks.width()) + 8, Math.round(blocks.height()) + 8);
			this.blocks = blocks;
		}

		@Override
		protected int[] defaultPos(int sw, int sh) {
			// Below the corner panels, in the empty sky above the middle.
			return new int[]{(sw - w) / 2, Math.max(34, Math.round(scene.logoCenterY() - h / 2f))};
		}

		@Override
		protected void renderContent(DrawContext ctx, TextRenderer tr, int mx, int my, float delta) {
			// Drawn by renderLogo, on top of the other panels.
		}
	}


	/**
	 * The buttons along the bottom: a row of four big ones (Singleplayer,
	 * Multiplayer, Options, Quit) with Quick Join and Mods as smaller ones above.
	 */
	private class BarPanel extends Panel {
		private static final int BIG_H = 26, SMALL_H = 15, GAP = 6;
		private final ObsidianButton quick;

		BarPanel(int screenW) {
			super("bar", 0, SMALL_H + 5 + BIG_H + 3);
			int bw = MathHelper.clamp((screenW - 24 - GAP * 3) / 4, 70, 140);
			w = bw * 4 + GAP * 3;
			int bigY = SMALL_H + 5;
			buttons.add(new ObsidianButton(0, bigY, bw, BIG_H, () -> "Singleplayer",
				() -> client.setScreen(new SelectWorldScreen(ObsidianTitleScreen.this)), ObsidianButton.Style.BIG));
			buttons.add(new ObsidianButton(bw + GAP, bigY, bw, BIG_H, () -> "Multiplayer",
				() -> client.setScreen(new MultiplayerScreen(ObsidianTitleScreen.this)), ObsidianButton.Style.BIG));
			buttons.add(new ObsidianButton((bw + GAP) * 2, bigY, bw, BIG_H, () -> "Options",
				() -> client.setScreen(new OptionsScreen(ObsidianTitleScreen.this, client.options)), ObsidianButton.Style.BIG));
			buttons.add(new ObsidianButton((bw + GAP) * 3, bigY, bw, BIG_H, () -> "Quit Game",
				() -> client.scheduleStop(), ObsidianButton.Style.BIG));

			// One text size for the whole row, set by the longest label.
			float scale = 1.5f;
			for (ObsidianButton b : buttons) scale = Math.min(scale, b.fittingScale(textRenderer));
			for (ObsidianButton b : buttons) b.setTextScale(scale);

			int sw = Math.min(90, bw);
			quick = new ObsidianButton(w / 2 - sw - 3, 0, sw, SMALL_H, () -> "Quick Join", ObsidianTitleScreen.this::quickJoin);
			buttons.add(quick);
			buttons.add(new ObsidianButton(w / 2 + 3, 0, sw, SMALL_H, () -> "Mods (" + ModsScreens.count() + ")",
				() -> client.setScreen(ModsScreens.create(ObsidianTitleScreen.this))));
		}

		@Override
		protected int[] defaultPos(int sw, int sh) {
			return new int[]{(sw - w) / 2, sh - h - 34};
		}

		@Override
		protected void renderContent(DrawContext ctx, TextRenderer tr, int mx, int my, float delta) {
		}

		@Override
		public void render(DrawContext ctx, TextRenderer tr, int mx, int my, float delta, boolean dragging) {
			super.render(ctx, tr, mx, my, delta, dragging);
			// Quick Join: show which server it will join.
			String name = ObsidianClient.CONFIG.lastServerName;
			if (alpha > 0.5f && quick.isHovered()) {
				String tip = name == null || name.isBlank() ? "No server yet: opens Multiplayer" : "Rejoin " + name;
				int tw = tr.getWidth(tip) + 8;
				int tx = MathHelper.clamp(x + quick.dx + quick.w / 2 - tw / 2, 2, width - tw - 2);
				int ty = y - 16;
				ObsidianButton.rounded(ctx, tx, ty, tw, 13, 3, 0xE0181426);
				ctx.drawTextWithShadow(tr, tip, tx + 4, ty + 3, Theme.TEXT);
			}
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
		private static final float NAME_SCALE = 1.5f;
		private final Text name = Text.literal("OBSIDIAN CLIENT").setStyle(net.minecraft.text.Style.EMPTY.withBold(true));

		InfoPanel() {
			super("info", 0, 26);
			w = Math.max(Math.round(textRenderer.getWidth(name) * NAME_SCALE), textRenderer.getWidth(versionLine())) + 4;
		}

		private String versionLine() {
			int mods = ModsScreens.count();
			return "MINECRAFT " + SharedConstants.getGameVersion().name() + "  /  " + mods + (mods == 1 ? " MOD" : " MODS");
		}

		@Override
		protected int[] defaultPos(int sw, int sh) {
			return new int[]{8, sh - h - 6};
		}

		@Override
		protected void renderContent(DrawContext ctx, TextRenderer tr, int mx, int my, float delta) {
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(x, y);
			ctx.getMatrices().scale(NAME_SCALE, NAME_SCALE);
			ctx.drawTextWithShadow(tr, name, 0, 0, Theme.fade(0xFFFFFFFF, alpha));
			ctx.getMatrices().popMatrix();
			ctx.drawTextWithShadow(tr, versionLine(), x, y + 16, Theme.fade(0xFFCFC6EC, alpha));
		}
	}

	private class VolumePanel extends Panel {
		private static final int TRACK_X = 22, TRACK_RIGHT = 30;
		private boolean sliding;

		VolumePanel() {
			super("volume", 120, 20);
			buttons.add(new ObsidianButton(3, 3, 14, 14, () -> ObsidianClient.CONFIG.muted ? "×" : "♪", () -> {
				ObsidianClient.CONFIG.muted = !ObsidianClient.CONFIG.muted;
				ObsidianClient.CONFIG.save();
			}));
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
			ObsidianButton.rounded(ctx, x, y, w, h, 4, Theme.fade(0xC0201C30, alpha));
			int ty = y + h / 2;
			int s = trackStart(), e = trackEnd();
			float v = ObsidianClient.CONFIG.effectiveVolume();
			int knob = s + Math.round((e - s) * v);
			ctx.fill(s, ty - 1, e, ty + 1, Theme.fade(0xFF2A2350, alpha));
			ctx.fillGradient(s, ty - 1, knob, ty + 1, Theme.fade(Theme.ACCENT, alpha), Theme.fade(Theme.ACCENT_BLUE, alpha));
			ctx.fill(knob - 2, ty - 4, knob + 2, ty + 4, Theme.fade(Theme.TEXT, alpha));
			String pct = Math.round(v * 100) + "%";
			ctx.drawTextWithShadow(tr, pct, x + w - 4 - tr.getWidth(pct), y + 6, Theme.fade(Theme.TEXT_DIM, alpha));
		}
	}

	private class VanillaPanel extends Panel {
		VanillaPanel() {
			super("vanilla", 96, 16);
			buttons.add(new ObsidianButton(0, 0, 96, 16, () -> "Vanilla Menu", () -> {
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
