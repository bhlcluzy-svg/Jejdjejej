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
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The Obsidian main menu: an intro, a layered background that shifts with the
 * mouse, and movable panels: the logo, a button bar along the bottom, client
 * info, volume and the vanilla menu switch.
 */
public class ObsidianTitleScreen extends Screen {
	private static final float INTRO_LENGTH = 2.6f;
	/** How far each background layer slides at full mouse offset, as a fraction of the screen width. */
	private static final float DEPTH_SKY = 0.010f, DEPTH_RING = 0.020f, DEPTH_FAR = 0.036f, DEPTH_PARTICLES = 0.048f, DEPTH_NEAR = 0.068f;

	private final List<Panel> panels = new ArrayList<>();
	private final List<Particle> particles = new ArrayList<>();
	private final Random random = Random.create();

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

	/** Smoothed mouse position, -1 to 1 from the centre of the screen. */
	private float lookX, lookY;
	private int mouseX = -1000, mouseY = -1000;

	public ObsidianTitleScreen() {
		super(Text.literal(ObsidianClient.NAME));
		boolean play = ObsidianClient.CONFIG.playIntro && !ObsidianClient.introPlayed;
		introT = play ? 0f : INTRO_LENGTH;
	}

	@Override
	protected void init() {
		panels.clear();
		logo = new LogoPanel(MathHelper.clamp(Math.round(width * 0.42f), 150, 300));
		panels.add(logo);
		bar = new BarPanel(width);
		panels.add(bar);
		panels.add(new InfoPanel());
		panels.add(new VolumePanel());
		panels.add(new VanillaPanel());
		for (Panel p : panels) p.layout(width, height);

		int target = MathHelper.clamp(width * height / 2200, 30, 90);
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

	// ---------------------------------------------------------- background

	private void advanceClock(int mx, int my) {
		long now = Util.getMeasuringTimeNano() / 1_000_000L;
		float dt = lastFrame < 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		time += dt;
		if (client.getOverlay() == null) introStarted = true;
		if (introStarted && introRunning()) {
			introT += dt;
			if (!introRunning()) ObsidianClient.introPlayed = true;
		}

		mouseX = mx;
		mouseY = my;
		// Ease towards the mouse, plus a slow sway so the scene never sits still.
		float targetX = MathHelper.clamp((mx / (float) Math.max(1, width)) * 2f - 1f, -1f, 1f) + 0.12f * MathHelper.sin(time * 0.23f);
		float targetY = MathHelper.clamp((my / (float) Math.max(1, height)) * 2f - 1f, -1f, 1f) + 0.08f * MathHelper.cos(time * 0.19f);
		float k = 1f - (float) Math.exp(-dt * 4f);
		lookX += (targetX - lookX) * k;
		lookY += (targetY - lookY) * k;

		for (Particle p : particles) p.update(dt);
	}

	/** Horizontal and vertical shift for a layer at the given depth. */
	private float shiftX(float depth) {
		return -lookX * depth * width;
	}

	private float shiftY(float depth) {
		return -lookY * depth * width * 0.6f;
	}

	/** Size of a 16:9 layer scaled to cover the screen plus enough margin to slide: {width, height}. */
	private float[] cover(float depth) {
		float margin = depth * width * 1.2f;
		float coverW = Math.max(width + margin * 2, (height + margin * 2) * Theme.BG_W / (float) Theme.BG_H);
		return new float[]{coverW, coverW * Theme.BG_H / Theme.BG_W};
	}

	private void layer(DrawContext ctx, Identifier texture, float depth) {
		float[] c = cover(depth);
		float x = (width - c[0]) / 2f + shiftX(depth);
		float y = (height - c[1]) / 2f + shiftY(depth);
		Theme.image(ctx, texture, x, y, c[0], c[1], Theme.BG_W, Theme.BG_H, -1);
	}

	/** Where the eclipse is on screen right now: {centre x, centre y, disc radius}. */
	private float[] eclipse() {
		float[] c = cover(DEPTH_RING);
		float cx = (width - c[0]) / 2f + c[0] * Theme.RING_X + shiftX(DEPTH_RING);
		float cy = (height - c[1]) / 2f + c[1] * Theme.RING_Y + shiftY(DEPTH_RING);
		return new float[]{cx, cy, c[0] * 250f / Theme.BG_W};
	}

	@Override
	public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
		advanceClock(mx, my);
		float reveal = Theme.easeInOut((introT - 0.2f) / 1.3f);

		ctx.fill(0, 0, width, height, 0xFF000000);
		layer(ctx, Theme.BG_SKY, DEPTH_SKY);

		// The eclipse: a breathing glow behind it, and it "ignites" during the intro.
		float[] e = eclipse();
		float pulse = 0.5f + 0.5f * MathHelper.sin(time * 1.4f);
		float ignite = Theme.easeOutCubic((introT - 0.4f) / 1.2f);
		Theme.glow(ctx, e[0], e[1], e[2] * (3.4f + 0.25f * pulse), Theme.fade(0xFF6A4CFF, (0.28f + 0.12f * pulse) * ignite));
		float ringSize = e[2] * Theme.RING_SIZE / 250f * MathHelper.lerp(ignite, 0.92f, 1f);
		Theme.image(ctx, Theme.BG_RING, e[0] - ringSize / 2f, e[1] - ringSize / 2f, ringSize, ringSize, Theme.RING_SIZE, Theme.RING_SIZE, Theme.fade(0xFFFFFFFF, ignite));

		layer(ctx, Theme.BG_FAR, DEPTH_FAR);

		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(shiftX(DEPTH_PARTICLES), shiftY(DEPTH_PARTICLES));
		for (Particle p : particles) p.render(ctx);
		ctx.getMatrices().popMatrix();

		layer(ctx, Theme.BG_NEAR, DEPTH_NEAR);

		// Darken the bottom so the button bar reads clearly.
		ctx.fillGradient(0, height * 2 / 3, width, height, 0x00000000, 0xB0040210);

		float curtain = 1f - reveal;
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
				String hint = "Drag panels to move them  ·  Right-click a panel to reset it";
				ctx.drawCenteredTextWithShadow(textRenderer, hint, width / 2, Math.max(2, bar.y - 12), Theme.fade(Theme.TEXT, 0.7f * hintAlpha));
			}
		}
	}

	/** The logo floats with the eclipse layer, so it stays sitting on it as the scene moves. */
	private void renderLogo(DrawContext ctx) {
		float appear = Theme.easeOutCubic((introT - 0.8f) / 1.0f);
		float scale = MathHelper.lerp(appear, 1.25f, 1.0f);
		float cx = logo.x + logo.w / 2f + shiftX(DEPTH_RING);
		float cy = logo.y + logo.h / 2f + shiftY(DEPTH_RING);

		float burst = MathHelper.clamp((introT - 1.3f) / 0.8f, 0f, 1f);
		if (burst > 0f && burst < 1f) {
			Theme.glow(ctx, cx, cy, logo.w * (0.8f + burst * 1.8f), Theme.fade(0xFFB8A6FF, (1f - burst) * 0.7f));
		}

		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(cx, cy);
		ctx.getMatrices().scale(scale, scale);
		Theme.logo(ctx, -logo.w / 2f, -logo.h / 2f, logo.w, appear);
		ctx.getMatrices().popMatrix();
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
		LogoPanel(int w) {
			super("logo", w, Math.round(w * (float) Theme.LOGO_H / Theme.LOGO_W));
		}

		@Override
		protected int[] defaultPos(int sw, int sh) {
			// Centred on the eclipse, before parallax (that is added while drawing).
			float[] c = cover(DEPTH_RING);
			float cy = (sh - c[1]) / 2f + c[1] * Theme.RING_Y;
			return new int[]{(sw - w) / 2, Math.round(cy - h / 2f)};
		}

		@Override
		protected void renderContent(DrawContext ctx, TextRenderer tr, int mx, int my, float delta) {
			// Drawn by renderLogo so it can follow the eclipse.
		}
	}

	/** The row of main buttons along the bottom of the screen. */
	private class BarPanel extends Panel {
		private static final int BH = 20, GAP = 4, PAD = 4;

		BarPanel(int screenW) {
			super("bar", 0, BH + PAD * 2);
			List<String> labels = List.of("Singleplayer", "Multiplayer", "Quick Join", "Mods", "Options", "Quit");
			List<Runnable> actions = List.of(
				() -> client.setScreen(new SelectWorldScreen(ObsidianTitleScreen.this)),
				() -> client.setScreen(new MultiplayerScreen(ObsidianTitleScreen.this)),
				ObsidianTitleScreen.this::quickJoin,
				() -> client.setScreen(ModsScreens.create(ObsidianTitleScreen.this)),
				() -> client.setScreen(new OptionsScreen(ObsidianTitleScreen.this, client.options)),
				() -> client.scheduleStop());
			List<Supplier<String>> suppliers = new ArrayList<>();
			for (String label : labels) suppliers.add(() -> label);
			suppliers.set(3, () -> "Mods (" + ModsScreens.count() + ")");

			// Size each button to its text, then shrink the padding if the row is too wide.
			int[] widths = new int[labels.size()];
			int textTotal = 0;
			for (int i = 0; i < widths.length; i++) {
				widths[i] = textRenderer.getWidth(suppliers.get(i).get());
				textTotal += widths[i];
			}
			int available = screenW - 12 - PAD * 2 - GAP * (widths.length - 1);
			int pad = MathHelper.clamp((available - textTotal) / widths.length, 8, 28);
			int x = PAD;
			for (int i = 0; i < widths.length; i++) {
				int bw = Math.max(widths[i] + pad, 36);
				buttons.add(new ObsidianButton(x, PAD, bw, BH, suppliers.get(i), actions.get(i)));
				x += bw + GAP;
			}
			w = x - GAP + PAD;
		}

		@Override
		protected int[] defaultPos(int sw, int sh) {
			return new int[]{(sw - w) / 2, sh - h - 32};
		}

		@Override
		protected void renderContent(DrawContext ctx, TextRenderer tr, int mx, int my, float delta) {
			Theme.panel(ctx, x, y, w, h, Theme.fade(0x9A0A0718, alpha), Theme.fade(Theme.PANEL_BORDER, alpha));
		}

		@Override
		public void render(DrawContext ctx, TextRenderer tr, int mx, int my, float delta, boolean dragging) {
			super.render(ctx, tr, mx, my, delta, dragging);
			// Quick Join: show which server it will join above the button.
			ObsidianButton quick = buttons.get(2);
			String name = ObsidianClient.CONFIG.lastServerName;
			if (alpha > 0.5f && quick.isHovered()) {
				String tip = name == null || name.isBlank() ? "No server yet: opens Multiplayer" : "Rejoin " + name;
				int tw = tr.getWidth(tip) + 8;
				int tx = MathHelper.clamp(x + quick.dx + quick.w / 2 - tw / 2, 2, width - tw - 2);
				int ty = y - 16;
				Theme.panel(ctx, tx, ty, tw, 13, 0xE00C0A1A, Theme.PANEL_BORDER);
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

	private class InfoPanel extends Panel {
		InfoPanel() {
			super("info", 0, 24);
			w = Math.max(textRenderer.getWidth("OBSIDIAN v" + ObsidianClient.VERSION), textRenderer.getWidth(versionLine())) + 14;
		}

		private String versionLine() {
			int mods = ModsScreens.count();
			return "Minecraft " + SharedConstants.getGameVersion().name() + "  ·  " + mods + (mods == 1 ? " mod" : " mods");
		}

		@Override
		protected int[] defaultPos(int sw, int sh) {
			return new int[]{6, sh - h - 5};
		}

		@Override
		protected void renderContent(DrawContext ctx, TextRenderer tr, int mx, int my, float delta) {
			Theme.panel(ctx, x, y, w, h, Theme.fade(Theme.PANEL, alpha), Theme.fade(Theme.PANEL_BORDER, alpha));
			ctx.fill(x + 1, y + 1, x + 3, y + h - 1, Theme.fade(Theme.ACCENT, alpha));
			int tx = x + 8, ty = y + 3;
			ctx.drawTextWithShadow(tr, "OBSIDIAN", tx, ty, Theme.fade(Theme.ACCENT, alpha));
			ctx.drawTextWithShadow(tr, "v" + ObsidianClient.VERSION, tx + tr.getWidth("OBSIDIAN") + 4, ty, Theme.fade(Theme.TEXT_DIM, alpha));
			ctx.drawTextWithShadow(tr, versionLine(), tx, ty + 10, Theme.fade(Theme.TEXT, alpha));
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
			super("vanilla", 84, 18);
			buttons.add(new ObsidianButton(0, 0, 84, 18, () -> "Vanilla Menu", () -> {
				ObsidianClient.CONFIG.vanillaMenu = true;
				ObsidianClient.CONFIG.save();
				client.setScreen(new TitleScreen());
			}));
		}

		@Override
		protected int[] defaultPos(int sw, int sh) {
			return new int[]{sw - w - 6, sh - h - 8};
		}

		@Override
		protected void renderContent(DrawContext ctx, TextRenderer tr, int mx, int my, float delta) {
		}
	}

	// ----------------------------------------------------------- particles

	/** A purple speck rising like a nether portal particle. It drifts away from the cursor. */
	private class Particle {
		float x, y, vx, vy, size, alpha, phase, pushX, pushY;
		int color;

		Particle(boolean anywhere) {
			respawn(anywhere);
		}

		void respawn(boolean anywhere) {
			x = random.nextFloat() * Math.max(1, width);
			y = anywhere ? random.nextFloat() * Math.max(1, height) : height + 6f;
			vx = (random.nextFloat() - 0.5f) * 5f;
			vy = -(3f + random.nextFloat() * 10f);
			size = 2.5f + random.nextFloat() * random.nextFloat() * 8f;
			alpha = 0.35f + random.nextFloat() * 0.55f;
			phase = random.nextFloat() * MathHelper.TAU;
			color = random.nextFloat() < 0.6f ? 0xFFB49BFF : 0xFF8FA8FF;
			pushX = pushY = 0f;
		}

		void update(float dt) {
			// Push away from the cursor (in the particles' own shifted space).
			float px = x + shiftX(DEPTH_PARTICLES), py = y + shiftY(DEPTH_PARTICLES);
			float dx = px - mouseX, dy = py - mouseY;
			float d2 = dx * dx + dy * dy;
			float radius = 34f;
			if (d2 < radius * radius && d2 > 0.01f) {
				float d = MathHelper.sqrt(d2);
				float force = (1f - d / radius) * 160f;
				pushX += dx / d * force * dt;
				pushY += dy / d * force * dt;
			}
			float damp = (float) Math.exp(-dt * 2.5f);
			pushX *= damp;
			pushY *= damp;

			x += (vx + MathHelper.sin(time * 0.7f + phase) * 3f + pushX) * dt;
			y += (vy + pushY) * dt;
			if (y < -10 || x < -20 || x > width + 20 || y > height + 20) respawn(false);
		}

		void render(DrawContext ctx) {
			float twinkle = 0.7f + 0.3f * MathHelper.sin(time * 2.1f + phase);
			Theme.glow(ctx, x, y, size, Theme.fade(color, alpha * twinkle));
		}
	}
}
