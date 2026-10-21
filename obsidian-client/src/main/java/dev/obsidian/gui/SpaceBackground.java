package dev.obsidian.gui;

import dev.obsidian.ObsidianClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.List;

/**
 * The animated space scene behind the menu. The picture's ships, planet and
 * rocks are separate sprites (see tools/make_space.py) so they can bob, drift
 * and tilt on their own; on top of that come twinkling stars, a portal swirl in
 * the obsidian ring, ships flying past, and real Minecraft blocks floating in
 * the foreground. Everything shifts with the mouse at its own depth.
 */
public class SpaceBackground {
	private static final Identifier BASE = space("base");
	private static final float BASE_DEPTH = 0.010f;
	private static final float BLOCK_DEPTH = 0.075f;
	private static final Identifier[] BLOCK_TEXTURES = {
		Identifier.ofVanilla("textures/block/obsidian.png"),
		Identifier.ofVanilla("textures/block/obsidian.png"),
		Identifier.ofVanilla("textures/block/crying_obsidian.png"),
		Identifier.ofVanilla("textures/block/deepslate.png"),
	};
	/** Ships that look like they point right, used for fly-bys. */
	private static final String[] FLYBY_SHIPS = {"ship_white", "ship_yellow", "ship_small", "ship_tiny"};

	private final Random random = Random.create();
	private final Identifier[] spriteTextures = new Identifier[SpaceLayout.SPRITES.length];
	private final float[] spritePhase = new float[SpaceLayout.SPRITES.length];
	private final List<Star> stars = new ArrayList<>();
	private final List<Spark> sparks = new ArrayList<>();
	private final List<FloatingBlock> blocks = new ArrayList<>();
	private Flyby flyby;
	private float nextFlyby = 4f;

	private int width, height;
	private float lookX, lookY;
	private float time;
	/** Cover rectangle of the base picture on screen, before parallax. */
	private float coverX, coverY, coverW, coverH;

	public SpaceBackground() {
		for (int i = 0; i < SpaceLayout.SPRITES.length; i++) {
			spriteTextures[i] = space(SpaceLayout.SPRITES[i].name());
			spritePhase[i] = random.nextFloat() * MathHelper.TAU;
		}
		for (int i = 0; i < 70; i++) stars.add(new Star());
		for (int i = 0; i < 70; i++) sparks.add(new Spark(true));
		for (int i = 0; i < 7; i++) blocks.add(new FloatingBlock(true));
	}

	private static Identifier space(String name) {
		return Identifier.of(ObsidianClient.MOD_ID, "textures/gui/space/" + name + ".png");
	}

	public void resize(int width, int height) {
		this.width = width;
		this.height = height;
		// Just enough extra around the edges for the layers to slide.
		float margin = width * 0.03f;
		coverW = Math.max(width + margin * 2, (height + margin * 2) * SpaceLayout.WIDTH / (float) SpaceLayout.HEIGHT);
		coverH = coverW * SpaceLayout.HEIGHT / SpaceLayout.WIDTH;
		coverX = (width - coverW) / 2f;
		coverY = (height - coverH) / 2f;
	}

	/** Picture pixels to screen units. */
	private float scale() {
		return coverW / SpaceLayout.WIDTH;
	}

	private float shiftX(float depth) {
		return -lookX * depth * width;
	}

	private float shiftY(float depth) {
		return -lookY * depth * width * 0.6f;
	}

	public void update(float dt, float time, float lookX, float lookY, int mouseX, int mouseY) {
		this.time = time;
		this.lookX = lookX;
		this.lookY = lookY;
		for (Spark s : sparks) s.update(dt, mouseX, mouseY);
		for (FloatingBlock b : blocks) b.update(dt);
		if (flyby != null) {
			flyby.t += dt / flyby.duration;
			if (flyby.t >= 1f) flyby = null;
		} else if ((nextFlyby -= dt) <= 0f) {
			flyby = new Flyby();
			nextFlyby = 9f + random.nextFloat() * 10f;
		}
	}

	/** Where the logo looks best: the empty sky above the middle, in screen units. */
	public float logoCenterY() {
		return coverY + coverH * 0.2f;
	}

	public void render(DrawContext ctx, float reveal) {
		float s = scale();
		ctx.fill(0, 0, width, height, 0xFF04040C);
		Theme.image(ctx, BASE, coverX + shiftX(BASE_DEPTH), coverY + shiftY(BASE_DEPTH), coverW, coverH,
			SpaceLayout.WIDTH, SpaceLayout.HEIGHT, -1);

		// Twinkling stars, fixed to the picture.
		for (Star star : stars) star.render(ctx, s);

		renderPortal(ctx, s);

		for (int i = 0; i < SpaceLayout.SPRITES.length; i++) renderSprite(ctx, i, s);

		if (flyby != null) flyby.render(ctx, s);

		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(shiftX(0.045f), shiftY(0.045f));
		for (Spark spark : sparks) spark.render(ctx);
		ctx.getMatrices().popMatrix();

		for (FloatingBlock b : blocks) b.render(ctx);

		// Darken the bottom so the button bar reads clearly.
		ctx.fillGradient(0, height * 3 / 5, width, height, 0x00000000, 0xC0020108);
	}

	private void renderSprite(DrawContext ctx, int i, float s) {
		SpaceLayout.Sprite sp = SpaceLayout.SPRITES[i];
		float ph = spritePhase[i];
		// Each kind moves differently: planets barely, rocks tumble, ships cruise.
		float bobY, bobX, angle;
		switch (sp.kind()) {
			case PLANET -> {
				bobY = MathHelper.sin(time * 0.35f + ph) * 4f;
				bobX = 0f;
				angle = MathHelper.sin(time * 0.2f + ph) * 0.03f;
			}
			case ROCK -> {
				bobY = MathHelper.sin(time * 0.5f + ph) * 7f;
				bobX = MathHelper.cos(time * 0.37f + ph) * 5f;
				angle = MathHelper.sin(time * 0.25f + ph) * 0.12f;
			}
			default -> {
				bobY = MathHelper.sin(time * 0.9f + ph) * 6f;
				bobX = MathHelper.sin(time * 0.31f + ph) * 22f;
				angle = MathHelper.sin(time * 0.6f + ph) * 0.06f;
			}
		}
		float cx = coverX + (sp.x() + sp.w() / 2f + bobX) * s + shiftX(sp.depth());
		float cy = coverY + (sp.y() + sp.h() / 2f + bobY) * s + shiftY(sp.depth());
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(cx, cy);
		rotate(ctx, angle);
		Theme.image(ctx, spriteTextures[i], -sp.w() * s / 2f, -sp.h() * s / 2f, sp.w() * s, sp.h() * s, sp.w(), sp.h(), -1);
		ctx.getMatrices().popMatrix();
	}

	/** Pulsing glow and purple pixels spiralling into the middle of the obsidian ring. */
	private void renderPortal(DrawContext ctx, float s) {
		float cx = coverX + SpaceLayout.RING_X * s + shiftX(0.014f);
		float cy = coverY + SpaceLayout.RING_Y * s + shiftY(0.014f);
		float r = SpaceLayout.RING_R * s;
		float pulse = 0.5f + 0.5f * MathHelper.sin(time * 1.3f);
		Theme.glow(ctx, cx, cy, r * (2.6f + 0.3f * pulse), Theme.fade(0xFF7B4DFF, 0.30f + 0.15f * pulse));
		Theme.glow(ctx, cx, cy, r * 1.1f, Theme.fade(0xFFB59BFF, 0.18f + 0.12f * pulse));
		for (int i = 0; i < 26; i++) {
			// Each speck travels from the rim to the centre, then starts again.
			float life = (time * 0.18f + i * 0.0385f) % 1f;
			float ang = i * 2.4f + life * 4.5f;
			float dist = r * (1f - life) * 0.95f;
			float px = cx + MathHelper.cos(ang) * dist;
			float py = cy + MathHelper.sin(ang) * dist * 0.75f;
			float a = MathHelper.sin(life * MathHelper.PI);
			float size = Math.max(0.8f, s * 7f * (1f - life * 0.6f));
			int color = i % 3 == 0 ? 0xFF8FB4FF : 0xFFC08CFF;
			square(ctx, px, py, size, Theme.fade(color, a * 0.9f));
		}
	}

	private static void rotate(DrawContext ctx, float angle) {
		float c = MathHelper.cos(angle), sn = MathHelper.sin(angle);
		ctx.getMatrices().mul(new Matrix3x2f(c, sn, -sn, c, 0f, 0f));
	}

	/** A crisp square centred on (x, y), placed with sub-pixel precision. */
	private static void square(DrawContext ctx, float x, float y, float size, int color) {
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(x - size / 2f, y - size / 2f);
		ctx.getMatrices().scale(size, size);
		ctx.fill(0, 0, 1, 1, color);
		ctx.getMatrices().popMatrix();
	}

	// ------------------------------------------------------------- pieces

	private class Star {
		final float x = random.nextFloat() * SpaceLayout.WIDTH;
		final float y = random.nextFloat() * SpaceLayout.HEIGHT;
		final float size = 2f + random.nextFloat() * 3f;
		final float speed = 0.8f + random.nextFloat() * 2.2f;
		final float phase = random.nextFloat() * MathHelper.TAU;

		void render(DrawContext ctx, float s) {
			float a = MathHelper.sin(time * speed + phase);
			if (a <= 0.05f) return;
			float px = coverX + x * s + shiftX(BASE_DEPTH);
			float py = coverY + y * s + shiftY(BASE_DEPTH);
			float sz = Math.max(0.6f, size * s);
			square(ctx, px, py, sz, Theme.fade(0xFFE6E8FF, a));
			if (size > 4f) {
				// The brightest ones get a small plus-shaped sparkle.
				square(ctx, px - sz, py, sz * 0.7f, Theme.fade(0xFFB9C4FF, a * 0.5f));
				square(ctx, px + sz, py, sz * 0.7f, Theme.fade(0xFFB9C4FF, a * 0.5f));
				square(ctx, px, py - sz, sz * 0.7f, Theme.fade(0xFFB9C4FF, a * 0.5f));
				square(ctx, px, py + sz, sz * 0.7f, Theme.fade(0xFFB9C4FF, a * 0.5f));
			}
		}
	}

	/** A pixel of purple portal dust that rises slowly and drifts away from the cursor. */
	private class Spark {
		float x, y, vy, size, alpha, phase, pushX, pushY;
		int color;

		Spark(boolean anywhere) {
			respawn(anywhere);
		}

		void respawn(boolean anywhere) {
			x = random.nextFloat() * Math.max(1, width);
			y = anywhere ? random.nextFloat() * Math.max(1, height) : height + 4f;
			vy = -(2f + random.nextFloat() * 7f);
			size = 1f + random.nextFloat() * random.nextFloat() * 2.5f;
			alpha = 0.4f + random.nextFloat() * 0.5f;
			phase = random.nextFloat() * MathHelper.TAU;
			color = random.nextFloat() < 0.65f ? 0xFFB98CFF : 0xFF8FB0FF;
			pushX = pushY = 0f;
		}

		void update(float dt, int mouseX, int mouseY) {
			float px = x + shiftX(0.045f), py = y + shiftY(0.045f);
			float dx = px - mouseX, dy = py - mouseY;
			float d2 = dx * dx + dy * dy;
			float radius = 36f;
			if (d2 < radius * radius && d2 > 0.01f) {
				float d = MathHelper.sqrt(d2);
				float force = (1f - d / radius) * 170f;
				pushX += dx / d * force * dt;
				pushY += dy / d * force * dt;
			}
			float damp = (float) Math.exp(-dt * 2.5f);
			pushX *= damp;
			pushY *= damp;
			x += (MathHelper.sin(time * 0.7f + phase) * 3f + pushX) * dt;
			y += (vy + pushY) * dt;
			if (y < -6 || x < -20 || x > width + 20 || y > height + 20) respawn(false);
		}

		void render(DrawContext ctx) {
			float twinkle = 0.65f + 0.35f * MathHelper.sin(time * 2.4f + phase);
			square(ctx, x, y, size, Theme.fade(color, alpha * twinkle));
		}
	}

	/** A real Minecraft block drifting through the foreground, drawn as an isometric cube. */
	private class FloatingBlock {
		float x, y, vx, vy, size, phase;
		Identifier texture;

		FloatingBlock(boolean anywhere) {
			respawn(anywhere);
		}

		void respawn(boolean anywhere) {
			size = 7f + random.nextFloat() * random.nextFloat() * 16f;
			boolean fromLeft = random.nextBoolean();
			x = anywhere ? random.nextFloat() * width : (fromLeft ? -size * 2 : width + size * 2);
			y = random.nextFloat() * height * 0.9f;
			vx = (2f + random.nextFloat() * 4f) * (anywhere ? (random.nextBoolean() ? 1 : -1) : (fromLeft ? 1 : -1));
			vy = (random.nextFloat() - 0.5f) * 1.5f;
			phase = random.nextFloat() * MathHelper.TAU;
			texture = BLOCK_TEXTURES[random.nextInt(BLOCK_TEXTURES.length)];
		}

		void update(float dt) {
			x += vx * dt;
			y += vy * dt;
			if (x < -size * 3 || x > width + size * 3 || y < -size * 3 || y > height + size * 3) respawn(false);
		}

		void render(DrawContext ctx) {
			// Bigger blocks are closer, so they move more with the mouse.
			float depth = BLOCK_DEPTH * (0.4f + size / 23f);
			float px = x + shiftX(depth);
			float py = y + shiftY(depth) + MathHelper.sin(time * 0.6f + phase) * 3f;
			isoCube(ctx, texture, px, py, size);
		}
	}

	/**
	 * Draws a block as three textured faces: top (lit), left and right (shaded).
	 * (x, y) is the cube's centre, size the length of one edge on screen.
	 */
	private static void isoCube(DrawContext ctx, Identifier tex, float x, float y, float size) {
		float c = 0.866f, h = 0.5f;
		float k = size / 16f;
		// Top face: square (u, v) -> ((u - v) * c, (u + v) * h)
		face(ctx, tex, x, y - size, new Matrix3x2f(c * k, h * k, -c * k, h * k, 0f, 0f), 0xFFE0D8FF);
		// Left face: (u, v) -> (u * c - size * c, u * h + v - size * h)
		face(ctx, tex, x - size * c, y - size * h, new Matrix3x2f(c * k, h * k, 0f, k, 0f, 0f), 0xFF9A90C0);
		// Right face: (u, v) -> (u * c, -u * h + v)
		face(ctx, tex, x, y, new Matrix3x2f(c * k, -h * k, 0f, k, 0f, 0f), 0xFF6A6290);
	}

	private static void face(DrawContext ctx, Identifier tex, float ox, float oy, Matrix3x2f m, int color) {
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(ox, oy);
		ctx.getMatrices().mul(m);
		ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, 16, 16, 16, 16, 16, 16, color);
		ctx.getMatrices().popMatrix();
	}

	/** A ship that crosses the whole screen now and then, leaving a short trail. */
	private class Flyby {
		final Identifier texture;
		final SpaceLayout.Sprite sprite;
		final float startY, endY, duration, size;
		float t;

		Flyby() {
			String name = FLYBY_SHIPS[random.nextInt(FLYBY_SHIPS.length)];
			SpaceLayout.Sprite found = SpaceLayout.SPRITES[0];
			for (SpaceLayout.Sprite sp : SpaceLayout.SPRITES) if (sp.name().equals(name)) found = sp;
			sprite = found;
			texture = space(name);
			startY = height * (0.15f + random.nextFloat() * 0.45f);
			endY = startY + (random.nextFloat() - 0.6f) * height * 0.25f;
			duration = 6f + random.nextFloat() * 4f;
			size = 0.45f + random.nextFloat() * 0.35f;
		}

		void render(DrawContext ctx, float s) {
			float w = sprite.w() * s * size, h = sprite.h() * s * size;
			float e = Theme.easeInOut(t);
			float x = MathHelper.lerp(t, -w * 1.5f, width + w * 1.5f) + shiftX(0.05f);
			float y = MathHelper.lerp(e, startY, endY) + MathHelper.sin(t * 12f) * 2f + shiftY(0.05f);
			// Engine trail: a few fading squares behind the ship.
			for (int i = 1; i <= 8; i++) {
				square(ctx, x - w * 0.45f - i * w * 0.12f, y + h * 0.05f, Math.max(0.8f, h * 0.16f * (1f - i / 10f)),
					Theme.fade(0xFF9FC2FF, 0.55f * (1f - i / 9f)));
			}
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(x, y);
			rotate(ctx, (endY - startY) / width * 0.6f);
			Theme.image(ctx, texture, -w / 2f, -h / 2f, w, h, sprite.w(), sprite.h(), -1);
			ctx.getMatrices().popMatrix();
		}
	}
}
