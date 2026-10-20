package dev.obsidian.gui;

import dev.obsidian.ObsidianClient;
import dev.obsidian.ObsidianConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
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
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * The Obsidian main menu: intro animation, animated background, and movable
 * panels for the buttons, client info, volume and the vanilla menu switch.
 */
public class ObsidianTitleScreen extends Screen {
	private static final float INTRO_LENGTH = 2.7f;

	private final List<Panel> panels = new ArrayList<>();
	private final List<Particle> particles = new ArrayList<>();
	private final Random random = Random.create();

	private LogoPanel logo;
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
		panels.clear();
		logo = new LogoPanel(MathHelper.clamp(Math.round(width * 0.5f), 150, 280));
		MenuPanel menu = new MenuPanel();
		panels.add(logo);
		panels.add(menu);
		panels.add(new InfoPanel());
		panels.add(new VolumePanel());
		panels.add(new VanillaPanel());
		for (Panel p : panels) p.layout(width, height);

		int target = Math.min(220, width * height / 1400);
		while (particles.size() < target) particles.add(new Particle(true));
		while (particles.size() > target) particles.remove(particles.size() - 1);
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

	// ---------------------------------------------------------------- render

	private void advanceClock() {
		long now = Util.getMeasuringTimeMs();
		float dt = lastFrame < 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		time += dt;
		// Hold the intro until the loading screen has faded away.
		if (client.getOverlay() == null) introStarted = true;
		if (introStarted && introRunning()) {
			introT += dt;
			if (!introRunning()) ObsidianClient.introPlayed = true;
		}
		for (Particle p : particles) p.update(dt);
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		advanceClock();
		// The vanilla panorama faintly shows through, then our colours on top.
		renderPanoramaBackground(ctx, delta);
		ctx.fillGradient(0, 0, width, height, Theme.fade(Theme.BG_TOP, 0.86f), Theme.fade(Theme.BG_BOTTOM, 0.92f));

		// Big slow blobs of light, like the lights drifting in the video.
		for (int i = 0; i < 3; i++) {
			float bx = width * (0.5f + 0.42f * MathHelper.sin(time * (0.07f + i * 0.03f) + i * 2.1f));
			float by = height * (0.5f + 0.38f * MathHelper.cos(time * (0.05f + i * 0.02f) + i * 1.3f));
			Theme.glow(ctx, bx, by, Math.max(width, height) * (0.55f + i * 0.12f), i == 1 ? 0x2A8B6CFF : 0x225878FF);
		}

		for (Particle p : particles) p.render(ctx);

		// Breathing orb behind the logo.
		float lx = logo.x + logo.w / 2f, ly = logo.y + logo.h / 2f;
		if (introRunning()) {
			float move = Theme.easeInOut((introT - 1.7f) / 0.8f);
			lx = MathHelper.lerp(move, width / 2f, lx);
			ly = MathHelper.lerp(move, height / 2f, ly);
		}
		float pulse = 0.5f + 0.5f * MathHelper.sin(time * 1.6f);
		float orbIn = Theme.easeOutCubic((introT - 0.5f) / 1.2f);
		Theme.glow(ctx, lx, ly, logo.w * (1.45f + 0.1f * pulse), Theme.fade(0xFF5878FF, (0.30f + 0.12f * pulse) * orbIn));

		// Black curtain at the start of the intro.
		float curtain = 1f - Theme.easeInOut((introT - 0.2f) / 1.3f);
		if (curtain > 0f) ctx.fill(0, 0, width, height, Theme.fade(0xFF000000, curtain));
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		float frame = Math.max(0.05f, Math.min(1f, delta));
		float others = Theme.easeOutCubic((introT - 2.0f) / 0.6f);

		for (Panel p : panels) {
			if (p == logo && introRunning()) continue;
			p.alpha = p == logo ? 1f : others;
			if (others < 1f && p != logo) {
				ctx.getMatrices().pushMatrix();
				ctx.getMatrices().translate(0f, (1f - others) * 10f);
				p.render(ctx, textRenderer, mouseX, mouseY, frame, false);
				ctx.getMatrices().popMatrix();
			} else {
				p.render(ctx, textRenderer, mouseX, mouseY, frame, p == dragging);
			}
		}

		if (introRunning()) renderIntroLogo(ctx);

		if (!introRunning()) {
			String hint = "Drag panels to move them  ·  Right-click a panel to reset it";
			ctx.drawCenteredTextWithShadow(textRenderer, hint, width / 2, height - 10, Theme.fade(Theme.TEXT_DIM, 0.45f));
		}
	}

	private void renderIntroLogo(DrawContext ctx) {
		float appear = Theme.easeOutCubic((introT - 0.4f) / 1.1f);
		float move = Theme.easeInOut((introT - 1.7f) / 0.8f);
		float scale = MathHelper.lerp(appear, 1.3f, 1.0f);
		float cx = MathHelper.lerp(move, width / 2f, logo.x + logo.w / 2f);
		float cy = MathHelper.lerp(move, height / 2f, logo.y + logo.h / 2f);

		// A flash of light as the logo settles.
		float burst = MathHelper.clamp((introT - 1.2f) / 0.8f, 0f, 1f);
		if (burst > 0f && burst < 1f) {
			Theme.glow(ctx, cx, cy, logo.w * (0.8f + burst * 1.6f), Theme.fade(0xFFB8A6FF, (1f - burst) * 0.7f));
		}

		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(cx, cy);
		ctx.getMatrices().scale(scale, scale);
		Theme.logo(ctx, -logo.w / 2, -logo.h / 2, logo.w, appear);
		ctx.getMatrices().popMatrix();
	}

	// ----------------------------------------------------------------- input

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

	// ---------------------------------------------------------------- panels

	private class LogoPanel extends Panel {
		LogoPanel(int w) {
			super("logo", w, Math.round(w * (float) Theme.LOGO_H / Theme.LOGO_W));
		}

		@Override
		protected int[] defaultPos(int sw, int sh) {
			return new int[]{(sw - w) / 2, Math.max(4, Math.round(sh * 0.11f))};
		}

		@Override
		protected void renderContent(DrawContext ctx, TextRenderer tr, int mx, int my, float delta) {
			Theme.logo(ctx, x, y, w, alpha);
		}
	}

	private class MenuPanel extends Panel {
		private static final int BW = 150, BH = 16, GAP = 3, PAD = 6;

		MenuPanel() {
			super("menu", BW + PAD * 2, PAD * 2 + BH * 5 + GAP * 4);
			int row = PAD;
			buttons.add(new ObsidianButton(PAD, row, BW, BH, () -> "Singleplayer",
				() -> client.setScreen(new SelectWorldScreen(ObsidianTitleScreen.this))));
			row += BH + GAP;
			buttons.add(new ObsidianButton(PAD, row, BW, BH, () -> "Multiplayer",
				() -> client.setScreen(new MultiplayerScreen(ObsidianTitleScreen.this))));
			row += BH + GAP;
			buttons.add(new ObsidianButton(PAD, row, BW, BH, MenuPanel::quickJoinLabel, ObsidianTitleScreen.this::quickJoin));
			row += BH + GAP;
			buttons.add(new ObsidianButton(PAD, row, BW, BH, () -> "Mods (" + ModsScreens.count() + ")",
				() -> client.setScreen(ModsScreens.create(ObsidianTitleScreen.this))));
			row += BH + GAP;
			int half = (BW - GAP) / 2;
			buttons.add(new ObsidianButton(PAD, row, half, BH, () -> "Options",
				() -> client.setScreen(new OptionsScreen(ObsidianTitleScreen.this, client.options))));
			buttons.add(new ObsidianButton(PAD + half + GAP, row, BW - half - GAP, BH, () -> "Quit",
				() -> client.scheduleStop()));
		}

		private static String quickJoinLabel() {
			String name = ObsidianClient.CONFIG.lastServerName;
			if (name == null || name.isBlank()) return "Quick Join";
			String label = "Quick Join · " + name;
			return label.length() > 26 ? label.substring(0, 25) + "…" : label;
		}

		@Override
		protected int[] defaultPos(int sw, int sh) {
			int below = logo.y + logo.h + 8;
			return new int[]{(sw - w) / 2, Math.min(below, sh - h - 14)};
		}

		@Override
		protected void renderContent(DrawContext ctx, TextRenderer tr, int mx, int my, float delta) {
			Theme.panel(ctx, x, y, w, h, Theme.fade(Theme.PANEL, alpha), Theme.fade(Theme.PANEL_BORDER, alpha));
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

	private class InfoPanel extends Panel {
		private final String loader = FabricLoader.getInstance().getModContainer("fabricloader")
			.map(ModContainer::getMetadata).map(m -> m.getVersion().getFriendlyString()).orElse("?");

		InfoPanel() {
			super("info", 132, 48);
		}

		@Override
		protected int[] defaultPos(int sw, int sh) {
			return new int[]{6, sh - h - 6};
		}

		@Override
		protected void renderContent(DrawContext ctx, TextRenderer tr, int mx, int my, float delta) {
			Theme.panel(ctx, x, y, w, h, Theme.fade(Theme.PANEL, alpha), Theme.fade(Theme.PANEL_BORDER, alpha));
			ctx.fill(x + 1, y + 1, x + 3, y + h - 1, Theme.fade(Theme.ACCENT, alpha));
			int tx = x + 8, ty = y + 5;
			ctx.drawTextWithShadow(tr, "OBSIDIAN", tx, ty, Theme.fade(Theme.ACCENT, alpha));
			ctx.drawTextWithShadow(tr, "v" + ObsidianClient.VERSION, tx + tr.getWidth("OBSIDIAN") + 4, ty, Theme.fade(Theme.TEXT_DIM, alpha));
			ctx.drawTextWithShadow(tr, "Minecraft " + SharedConstants.getGameVersion().name(), tx, ty + 10, Theme.fade(Theme.TEXT, alpha));
			ctx.drawTextWithShadow(tr, "Fabric Loader " + loader, tx, ty + 20, Theme.fade(Theme.TEXT_DIM, alpha));
			ctx.drawTextWithShadow(tr, ModsScreens.count() + " mods loaded", tx, ty + 30, Theme.fade(Theme.TEXT_DIM, alpha));
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
			Theme.panel(ctx, x, y, w, h, Theme.fade(Theme.PANEL, alpha), Theme.fade(Theme.PANEL_BORDER, alpha));
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
			super("vanilla", 92, 18);
			buttons.add(new ObsidianButton(0, 0, 92, 18, () -> "Vanilla Menu", () -> {
				ObsidianClient.CONFIG.vanillaMenu = true;
				ObsidianClient.CONFIG.save();
				client.setScreen(new TitleScreen());
			}));
		}

		@Override
		protected int[] defaultPos(int sw, int sh) {
			return new int[]{sw - w - 6, sh - h - 6};
		}

		@Override
		protected void renderContent(DrawContext ctx, TextRenderer tr, int mx, int my, float delta) {
		}
	}

	// ------------------------------------------------------------- particles

	/** A drifting speck of light, like the snow in the video. */
	private class Particle {
		float x, y, vx, vy, size, alpha, phase;
		int color;

		Particle(boolean anywhere) {
			respawn(anywhere);
		}

		void respawn(boolean anywhere) {
			x = random.nextFloat() * Math.max(1, width);
			y = anywhere ? random.nextFloat() * Math.max(1, height) : -6f;
			vx = (random.nextFloat() - 0.5f) * 6f;
			vy = 4f + random.nextFloat() * 14f;
			size = 2f + random.nextFloat() * random.nextFloat() * 9f;
			alpha = 0.25f + random.nextFloat() * 0.6f;
			phase = random.nextFloat() * MathHelper.TAU;
			color = random.nextFloat() < 0.7f ? 0xFFB9C8FF : 0xFFB8A6FF;
		}

		void update(float dt) {
			x += (vx + MathHelper.sin(time * 0.8f + phase) * 4f) * dt;
			y += vy * dt;
			if (y > height + 8 || x < -10 || x > width + 10) respawn(false);
		}

		void render(DrawContext ctx) {
			float twinkle = 0.75f + 0.25f * MathHelper.sin(time * 2.3f + phase);
			Theme.glow(ctx, x, y, size, Theme.fade(color, alpha * twinkle));
		}
	}
}
