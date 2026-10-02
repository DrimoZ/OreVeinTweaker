import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import java.util.zip.ZipFile;

/**
 * CurseForge art in Minecraft's font - FactoryIO's {@code docs/showcase/Banners.java} and BeaconPack's
 * {@code tools/Banners.java}, so the mods' pages share one look. Those draw over captures of their
 * screens; this mod has no screen, so the backdrop is the underground itself, drawn in texture pixels
 * with the logo's palettes: each section shows a different vein.
 *
 * <pre>java tools/Banners.java</pre>
 *
 * Writes {@code docs/store-art/}, which the store page links to on GitHub. The font comes from the
 * game jar the build already unpacked: build once first. CurseForge's description editor refuses any
 * image wider than 850 px.
 */
public class Banners {

    static final int W = 850;
    /** Screen pixels per texture pixel. */
    static final int P = 10;
    static final Color INK = new Color(12, 13, 20);
    static final Color ACCENT = new Color(85, 208, 224);
    static final Color SUB = new Color(168, 244, 250);

    /** A rock layer and the vein it holds: base stone, the vein's filler, its ore shades, its raw block. */
    record Layer(int[] base, int[] filler, int[] ore, int raw) {}

    static final int[] STONE = {0x8A8A8A, 0x7D7D7D, 0x959595, 0x727272};
    static final int[] DEEPSLATE = {0x4C4C53, 0x434349, 0x56565E, 0x3C3C42};

    static final Layer COPPER = new Layer(STONE, new int[]{0xA3705D, 0x956553, 0xB2806C, 0x895A4A},
            new int[]{0xE3784D, 0xF29C70, 0xC85F37, 0x5BB59C}, 0xB4603E);
    static final Layer IRON = new Layer(DEEPSLATE, new int[]{0x64665D, 0x5A5C53, 0x6E7066, 0x52544C},
            new int[]{0xE2B999, 0xF3D4B9, 0xC4987A}, 0xD8B89B);
    static final Layer GOLD = new Layer(DEEPSLATE, new int[]{0x4A4A4F, 0x424247, 0x535358, 0x3B3B40},
            new int[]{0xF5D84A, 0xFCEE6B, 0xD8A62A}, 0xDDA93A);
    static final Layer COAL = new Layer(STONE, new int[]{0x8A8B8E, 0x7E7F82, 0x96979A, 0x737477},
            new int[]{0x2E2E2E, 0x3B3B3B, 0x1F1F1F}, 0x161616);
    static final Layer REDSTONE = new Layer(DEEPSLATE, new int[]{0xDEDFDA, 0xD2D3CD, 0xE8E9E4, 0xC6C7C1},
            new int[]{0xE01B1B, 0xFF4040, 0xA80F0F}, 0xB0130F);

    static BufferedImage font;
    static final int[] widths = new int[256];

    public static void main(String[] args) throws Exception {
        File out = new File("docs/store-art");
        out.mkdirs();
        loadFont();

        banner(out);
        header(out, "header_features", 1, COPPER, IRON, "What it does", "Off, resized, richer, rebuilt - or brand new");
        header(out, "header_start", 2, COPPER, COPPER, "Quick start", "Three steps, no restart");
        header(out, "header_options", 3, IRON, IRON, "Options", "One file, one section per vein");
        header(out, "header_recipes", 4, GOLD, GOLD, "Recipes", "Copy, paste, explore new chunks");
        header(out, "header_faq", 5, COAL, COAL, "FAQ", "The short answers");
        header(out, "header_versions", 6, COPPER, REDSTONE, "Versions", "Same features on every loader");
    }

    /** The top banner: the title large, the logo beside it, over a copper vein above an iron one. */
    static void banner(File out) throws Exception {
        int h = 280, logo = 232;
        BufferedImage b = underground(W, h, 0, COPPER, IRON);
        Graphics2D g = b.createGraphics();
        g.setColor(alpha(INK, 120));
        g.fillRect(0, 0, W, h);
        g.setPaint(new GradientPaint(0, 0, alpha(INK, 235), W * 0.8f, 0, alpha(INK, 60)));
        g.fillRect(0, 0, W, h);
        // The logo is drawn at 512 for this: smoothing, unlike the pixel art, only helps it shrink.
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(ImageIO.read(new File("src/main/resources/logo.png")), W - 24 - logo, 24, logo, logo, null);
        int x = 34, room = W - 24 - logo - 24 - x;
        fit(g, "Ore Vein Tweaker", x, 52, 6, room, Color.WHITE);
        fit(g, "The large ore veins, your way.", x + 2, 128, 3, room, new Color(236, 236, 240));
        fit(g, "Off, resized, richer, rebuilt - or new.", x + 2, 172, 2, room, SUB);
        fit(g, "Forge 1.20.1 - NeoForge 1.21.1 - 26.1", x + 2, 226, 2, room, alpha(new Color(220, 220, 220), 220));
        g.dispose();
        ImageIO.write(b, "png", new File(out, "banner.png"));
    }

    /** A section header: a slice of underground behind a title. */
    static void header(File out, String name, long seed, Layer top, Layer bottom, String title, String subtitle) throws Exception {
        int h = 100;
        BufferedImage img = underground(W, h, seed, top, bottom);
        Graphics2D g = img.createGraphics();
        g.setColor(alpha(INK, 100));
        g.fillRect(0, 0, W, h);
        g.setPaint(new GradientPaint(0, 0, alpha(INK, 210), W * 0.75f, 0, alpha(INK, 30)));
        g.fillRect(0, 0, W, h);
        g.setColor(ACCENT);
        g.fillRect(0, 0, 6, h);
        text(g, title, 28, 20, 4, Color.WHITE);
        text(g, subtitle, 30, 64, 2, SUB);
        g.dispose();
        ImageIO.write(img, "png", new File(out, name + ".png"));
    }

    // The underground: two rock layers split along a wavy line, each crossed by its vein's ribbons.

    static BufferedImage underground(int w, int h, long seed, Layer top, Layer bottom) {
        Random r = new Random(seed * 31 + 7);
        double phase = r.nextDouble() * 10, tilt = 0.6 + r.nextDouble() * 0.5;
        int cols = w / P + 1, rows = h / P + 1;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        for (int ty = 0; ty < rows; ty++) {
            for (int tx = 0; tx < cols; tx++) {
                double split = rows * 0.5 + Math.sin(tx * 0.09 + phase) * rows * 0.12;
                Layer l = ty < split ? top : bottom;
                // A ribbon where this sum crosses zero: vanilla's veins are ridges of a noise, this is
                // the cheap look-alike.
                double f = Math.sin(tx * 0.11 * tilt + 1.6 * Math.sin(ty * 0.23 + phase) + phase)
                        + 0.55 * Math.sin(tx * 0.047 - ty * 0.13 + phase * 2);
                int rgb;
                if (Math.abs(f) < 0.28) {
                    double roll = r.nextDouble();
                    rgb = roll < 0.012 ? l.raw() : roll < 0.3 ? pick(l.ore(), r) : pick(l.filler(), r);
                } else {
                    rgb = pick(l.base(), r);
                }
                g.setColor(new Color(rgb));
                g.fillRect(tx * P, ty * P, P, P);
            }
        }
        g.dispose();
        return img;
    }

    static int pick(int[] shades, Random r) {
        return shades[r.nextInt(shades.length)];
    }

    // Minecraft's font: 8-pixel cells, one pixel between letters, a shadow at a quarter of the colour.

    /** Any unpacked game jar will do: Forge's client-extra and NeoForge's patched jar both hold the font. */
    static void loadFont() throws Exception {
        File[] jars = new File("build/moddev/artifacts").listFiles((d, n) -> n.endsWith(".jar"));
        if (jars != null) {
            for (File jar : jars) {
                try (ZipFile zip = new ZipFile(jar)) {
                    var entry = zip.getEntry("assets/minecraft/textures/font/ascii.png");
                    if (entry != null) {
                        font = ImageIO.read(zip.getInputStream(entry));
                        break;
                    }
                }
            }
        }
        if (font == null) throw new IllegalStateException("no game jar under build/moddev/artifacts: build once first");
        for (int c = 0; c < 256; c++) {
            int gx = (c % 16) * 8, gy = (c / 16) * 8, w = 0;
            for (int x = 7; x >= 0 && w == 0; x--)
                for (int y = 0; y < 8; y++) if ((font.getRGB(gx + x, gy + y) >>> 24) > 0) { w = x + 1; break; }
            widths[c] = c == ' ' ? 4 : w;
        }
    }

    static int width(String s) {
        int w = 0;
        for (char ch : s.toCharArray()) w += widths[ch] + 1;
        return w;
    }

    /** Text at the largest scale up to {@code scale} that fits in {@code room}: the logo sits right of it. */
    static void fit(Graphics2D g, String s, int x, int y, int scale, int room, Color c) {
        while (scale > 1 && width(s) * scale > room) scale--;
        text(g, s, x, y, scale, c);
    }

    static void text(Graphics2D g, String s, int x, int y, int scale, Color c) {
        glyphs(g, s, x + scale, y + scale, scale, new Color(c.getRed() / 4, c.getGreen() / 4, c.getBlue() / 4, c.getAlpha()));
        glyphs(g, s, x, y, scale, c);
    }

    static void glyphs(Graphics2D g, String s, int x, int y, int scale, Color c) {
        g.setColor(c);
        for (char ch : s.toCharArray()) {
            int gx = (ch % 16) * 8, gy = (ch / 16) * 8;
            for (int yy = 0; yy < 8; yy++)
                for (int xx = 0; xx < 8; xx++)
                    if ((font.getRGB(gx + xx, gy + yy) >>> 24) > 0) g.fillRect(x + xx * scale, y + yy * scale, scale, scale);
            x += (widths[ch] + 1) * scale;
        }
    }

    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }
}
