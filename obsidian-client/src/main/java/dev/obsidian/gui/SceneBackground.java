package dev.obsidian.gui;

import dev.obsidian.ObsidianClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * The calm sunset scene behind the menus (layers made by tools/make_scene.py).
 * Clouds drift, the portal shimmers with the real nether portal animation and
 * gives off particles, the lake sparkles and the sun glow breathes. The sky,
 * clouds and ground slide by different amounts as the mouse moves, mostly side
 * to side. One shared instance, so the scene carries on smoothly from the
 * title screen into Singleplayer, Multiplayer and Options.
 */
public final class SceneBackground {
	public static final SceneBackground INSTANCE = new SceneBackground();

	private static final Identifier SKY = scene("sky");
	private static final Identifier GROUND = scene("ground");
	private static final Identifier[] CLOUDS = {scene("cloud_0"), scene("cloud_1"), scene("cloud_2")};
	private static final Identifier PORTAL = Identifier.ofVanilla("textures/block/nether_portal.png");
	/** How far each layer slides at full mouse offset, as a fraction of the screen width. */
	private static final float DEPTH_SKY = 0.008f, DEPTH_CLOUDS = 0.014f, DEPTH_GROUND = 0.03f, DEPTH_MOTES = 0.04f;
	/** Vertical movement is kept small so it feels like a gentle side-to-side pan. */
	private static final float VERTICAL = 0.25f;

	private final Random random = Random.create();
	private final List<Sparkle> sparkles = new ArrayList<>();
	private final List<PortalBit> portalBits = new ArrayList<>();
	private final List<Mote> motes = new ArrayList<>();

	private int width = -1, height = -1;
	private float coverX, coverY, coverW, coverH;
	private long lastFrame = -1;
	private float time, lookX, lookY;
	private int mouseX, mouseY;

	private SceneBackground() {
		for (int i = 0; i < 18; i++) sparkles.add(new Sparkle());
		for (int i = 0; i < 22; i++) portalBits.add(new PortalBit());
		for (int i = 0; i < 22; i++) motes.add(new Mote(true));
	}

	private static Identifier scene(String name) {
		return Identifier.of(ObsidianClient.MOD_ID, "textures/gui/scene/" + name + ".png");
	}

	public float time() {
		return time;
	}

	public float lookX() {
		return lookX;
	}

	public float lookY() {
		return lookY;
	}

	/** Where the logo looks best: the open sky in the upper middle, in screen units. */
	public float logoCenterY() {
		return coverY + coverH * 0.25f;
	}

	/** Lays the scene out for a screen size; called when a screen opens. */
	public void prepare(int w, int h) {
		if (w != width || h != height) resize(w, h);
	}

	private void resize(int w, int h) {
		width = w;
		height = h;
		float margin = w * (DEPTH_MOTES + 0.01f);
		coverW = Math.max(w + margin * 2, (h + margin * 2) * SceneLayout.WIDTH / (float) SceneLayout.HEIGHT);
		coverH = coverW * SceneLayout.HEIGHT / SceneLayout.WIDTH;
		coverX = (w - coverW) / 2f;
		coverY = (h - coverH) / 2f;
		for (Mote m : motes) m.respawn(true);
	}

	private float scale() {
		return coverW / SceneLayout.WIDTH;
	}

	private float shiftX(float depth) {
		return -lookX * depth * width;
	}

	private float shiftY(float depth) {
		return -lookY * depth * width * VERTICAL;
	}

	/** Advances the animation; call once per frame before drawing. */
	private void tick(int w, int h, int mx, int my) {
		if (w != width || h != height) resize(w, h);
		long now = Util.getMeasuringTimeNano();
		float dt = lastFrame < 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		time += dt;
		mouseX = mx;
		mouseY = my;
		// Slow easing keeps the motion calm.
		float targetX = MathHelper.clamp(mx / (float) Math.max(1, w) * 2f - 1f, -1f, 1f);
		float targetY = MathHelper.clamp(my / (float) Math.max(1, h) * 2f - 1f, -1f, 1f);
		float k = 1f - (float) Math.exp(-dt * 2.2f);
		lookX += (targetX - lookX) * k;
		lookY += (targetY - lookY) * k;
		for (Sparkle s : sparkles) s.update(dt);
		for (PortalBit p : portalBits) p.update(dt);
		for (Mote m : motes) m.update(dt);
	}

	public void render(DrawContext ctx, int w, int h, int mx, int my) {
		tick(w, h, mx, my);
		float s = scale();

		// Sky with the lake and distant hills.
		Theme.image(ctx, SKY, coverX + shiftX(DEPTH_SKY), coverY + shiftY(DEPTH_SKY), coverW, coverH, SceneLayout.WIDTH, SceneLayout.HEIGHT, -1);

		// The sunset breathes.
		float breathe = 0.5f + 0.5f * MathHelper.sin(time * 0.5f);
		Theme.glow(ctx, coverX + SceneLayout.SUN_X * s + shiftX(DEPTH_SKY), coverY + SceneLayout.SUN_Y * s + shiftY(DEPTH_SKY),
			coverW * (0.42f + 0.04f * breathe), Theme.fade(0xFFFFD7B0, 0.22f + 0.1f * breathe));

		for (Sparkle sp : sparkles) sp.render(ctx, s);

		// Clouds drift slowly back and forth.
		for (int i = 0; i < CLOUDS.length; i++) {
			int[] c = SceneLayout.CLOUDS[i];
			float drift = MathHelper.sin(time * (0.035f + i * 0.012f) + i * 2f) * 90f + MathHelper.sin(time * 0.11f + i) * 12f;
			float bob = MathHelper.sin(time * 0.2f + i * 1.7f) * 4f;
			Theme.image(ctx, CLOUDS[i], coverX + (c[0] + drift) * s + shiftX(DEPTH_CLOUDS), coverY + (c[1] + bob) * s + shiftY(DEPTH_CLOUDS),
				c[2] * s, c[3] * s, c[2], c[3], -1);
		}

		// Ground and portal in front.
		float gx = coverX + shiftX(DEPTH_GROUND), gy = coverY + shiftY(DEPTH_GROUND);
		Theme.image(ctx, GROUND, gx, gy, coverW, coverH, SceneLayout.WIDTH, SceneLayout.HEIGHT, -1);
		renderPortal(ctx, gx, gy, s);

		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(shiftX(DEPTH_MOTES), shiftY(DEPTH_MOTES));
		for (Mote m : motes) m.render(ctx);
		ctx.getMatrices().popMatrix();
	}

	/** The real animated nether portal texture inside the opening, a soft glow and drifting particles. */
	private void renderPortal(DrawContext ctx, float gx, float gy, float s) {
		float px = gx + SceneLayout.PORTAL_X * s, py = gy + SceneLayout.PORTAL_Y * s;
		float pw = SceneLayout.PORTAL_W * s, ph = SceneLayout.PORTAL_H * s;
		float pulse = 0.5f + 0.5f * MathHelper.sin(time * 1.6f);
		Theme.glow(ctx, px + pw / 2f, py + ph / 2f, ph * (2.6f + 0.4f * pulse), Theme.fade(0xFFA35CFF, 0.30f + 0.15f * pulse));

		int frame = (int) (time * 20f) % 32;
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(px, py);
		ctx.getMatrices().scale(pw / 16f, ph / 32f);
		// Two tiles stacked, so the swirl keeps its square pixels in the tall opening.
		ctx.drawTexture(RenderPipelines.GUI_TEXTURED, PORTAL, 0, 0, 0f, frame * 16f, 16, 16, 16, 512, 0xA0FFFFFF);
		ctx.drawTexture(RenderPipelines.GUI_TEXTURED, PORTAL, 0, 16, 0f, frame * 16f, 16, 16, 16, 512, 0xA0FFFFFF);
		ctx.getMatrices().popMatrix();

		for (PortalBit b : portalBits) b.render(ctx, px, py, pw, ph);
	}

	/** A crisp square centred on (x, y), placed with sub-pixel precision. */
	private static void square(DrawContext ctx, float x, float y, float w, float h, int color) {
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(x - w / 2f, y - h / 2f);
		ctx.getMatrices().scale(w, h);
		ctx.fill(0, 0, 1, 1, color);
		ctx.getMatrices().popMatrix();
	}

	// ------------------------------------------------------------- pieces

	/** A glint of light on the lake that fades in and out. */
	private class Sparkle {
		float x, y, len, life, speed;

		Sparkle() {
			respawn();
			life = random.nextFloat();
		}

		void respawn() {
			// A random point inside the lake's ellipse, more of them near the sun's reflection.
			float a = random.nextFloat() * MathHelper.TAU;
			float r = MathHelper.sqrt(random.nextFloat());
			x = SceneLayout.LAKE_X + MathHelper.cos(a) * r * SceneLayout.LAKE_RX;
			y = SceneLayout.LAKE_Y + MathHelper.sin(a) * r * SceneLayout.LAKE_RY;
			len = 14f + random.nextFloat() * 40f;
			life = 0f;
			speed = 0.25f + random.nextFloat() * 0.35f;
		}

		void update(float dt) {
			life += dt * speed;
			if (life >= 1f) respawn();
		}

		void render(DrawContext ctx, float s) {
			float a = MathHelper.sin(life * MathHelper.PI);
			square(ctx, coverX + x * s + shiftX(DEPTH_SKY), coverY + y * s + shiftY(DEPTH_SKY),
				len * s, Math.max(0.6f, 2.5f * s), Theme.fade(0xFFFFF0F6, 0.55f * a));
		}
	}

	/** Purple specks that drift out of the portal, like in the game. */
	private class PortalBit {
		float x, y, vx, vy, life, size;

		PortalBit() {
			respawn();
			life = random.nextFloat();
		}

		void respawn() {
			x = random.nextFloat();
			y = 0.2f + random.nextFloat() * 0.7f;
			vx = (random.nextFloat() - 0.5f) * 0.9f;
			vy = -0.15f - random.nextFloat() * 0.35f;
			life = 0f;
			size = 0.08f + random.nextFloat() * 0.08f;
		}

		void update(float dt) {
			life += dt * 0.45f;
			x += vx * dt * 0.6f;
			y += vy * dt * 0.6f;
			if (life >= 1f) respawn();
		}

		void render(DrawContext ctx, float px, float py, float pw, float ph) {
			float a = MathHelper.sin(life * MathHelper.PI);
			float sz = Math.max(0.7f, pw * size);
			square(ctx, px + x * pw, py + y * ph, sz, sz, Theme.fade(0xFFC27BFF, 0.85f * a));
		}
	}

	/** A soft warm speck of light floating in the evening air; it drifts away from the cursor. */
	private class Mote {
		float x, y, vx, vy, size, alpha, phase, pushX, pushY;

		Mote(boolean anywhere) {
			respawn(anywhere);
		}

		void respawn(boolean anywhere) {
			x = random.nextFloat() * Math.max(1, width);
			y = anywhere ? random.nextFloat() * Math.max(1, height) : height + 4f;
			vx = (random.nextFloat() - 0.5f) * 4f;
			vy = -(1.5f + random.nextFloat() * 4f);
			size = 1.5f + random.nextFloat() * 3.5f;
			alpha = 0.25f + random.nextFloat() * 0.45f;
			phase = random.nextFloat() * MathHelper.TAU;
			pushX = pushY = 0f;
		}

		void update(float dt) {
			float px = x + shiftX(DEPTH_MOTES), py = y + shiftY(DEPTH_MOTES);
			float dx = px - mouseX, dy = py - mouseY;
			float d2 = dx * dx + dy * dy;
			if (d2 < 900f && d2 > 0.01f) {
				float d = MathHelper.sqrt(d2);
				float force = (1f - d / 30f) * 90f;
				pushX += dx / d * force * dt;
				pushY += dy / d * force * dt;
			}
			float damp = (float) Math.exp(-dt * 2f);
			pushX *= damp;
			pushY *= damp;
			x += (vx + MathHelper.sin(time * 0.5f + phase) * 2f + pushX) * dt;
			y += (vy + pushY) * dt;
			if (y < -8 || x < -20 || x > width + 20 || y > height + 20) respawn(false);
		}

		void render(DrawContext ctx) {
			float twinkle = 0.6f + 0.4f * MathHelper.sin(time * 1.3f + phase);
			Theme.glow(ctx, x, y, size * 2.2f, Theme.fade(0xFFFFE2C4, alpha * twinkle));
		}
	}
}
