import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * The logo: a block of the underground, in the isometric view the game gives blocks in an inventory,
 * on Immaterial Drawers' plate - so the mods' icons read as a set in a launcher.
 *
 * <p>The block is cut from the layers where the large veins live: stone with a copper vein (granite
 * and copper ore) over deepslate with an iron vein (tuff and iron ore). The lit left face shows both
 * veins as vanilla makes them; on the right face they are gone, and only their outline is left, in
 * the plate's light. Every face is drawn in texture pixels and lit like a block.
 *
 * <p>512 x 512: the mod list, CurseForge's project avatar and the GitHub social preview all scale it
 * down from there.
 *
 * <pre>java tools/GenerateLogo.java</pre>
 */
public final class GenerateLogo {

    private static final int SIZE = 512;
    /** Screen pixels per texture pixel. */
    private static final double K = 13;
    private static final double COS = Math.cos(Math.toRadians(30));
    private static final double SIN = 0.5;
    /** The block's edge, in texture pixels. */
    private static final int B = 16;

    private static final Color PLATE_TOP = new Color(0x2A2244);
    private static final Color PLATE_BOTTOM = new Color(0x100D1C);
    private static final Color LIGHT = new Color(0x55D0E0);

    private static final int[] STONE = {0x8A8A8A, 0x7D7D7D, 0x959595, 0x727272};
    private static final int[] DEEPSLATE = {0x4C4C53, 0x434349, 0x56565E, 0x3C3C42};
    private static final int[] GRANITE = {0xA3705D, 0x956553, 0xB2806C, 0x895A4A};
    private static final int[] TUFF = {0x64665D, 0x5A5C53, 0x6E7066, 0x52544C};
    private static final int[] COPPER_ORE = {0xE3784D, 0xF29C70, 0xC85F37};
    private static final int COPPER_OXIDE = 0x5BB59C;
    private static final int[] IRON_ORE = {0xE2B999, 0xF3D4B9, 0xC4987A};
    private static final int RAW_COPPER = 0xB4603E;
    private static final int RAW_IRON = 0xD8B89B;

    private static double cx;
    private static double cy;

    public static void main(String[] args) throws IOException {
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        RoundRectangle2D plate = new RoundRectangle2D.Double(16, 16, SIZE - 32, SIZE - 32, 104, 104);
        g.setPaint(new GradientPaint(0, 16, PLATE_TOP, 0, SIZE - 16, PLATE_BOTTOM));
        g.fill(plate);
        g.setClip(plate);
        grid(g);

        // The block's origin: the centre of its base, placed so the whole cube is centred.
        cx = SIZE / 2.0;
        cy = SIZE / 2.0 + B * K * 0.5;

        glow(g, cx, cy - B * K * 0.55, 250, 90);
        shadow(g);
        box(g);
        strippedOutline(g);

        g.setClip(null);
        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(255, 255, 255, 34));
        g.draw(plate);
        g.dispose();
        ImageIO.write(out, "PNG", new File("src/main/resources/logo.png"));
        System.out.println("Logo written.");
    }

    // ------------------------------------------------------------------ the block's texture

    /** Where each vein runs, per column; u runs on around the corner, so the bands continue. */
    private static double copperCentre(int u) {
        return 12 + 1.2 * Math.sin(u * 0.45);
    }

    private static double ironCentre(int u) {
        return 4 + 1.2 * Math.sin(u * 0.45 + 2);
    }

    private static boolean inBand(double centre, int v) {
        return Math.abs(v + 0.5 - centre) <= 1.8;
    }

    private static int noise(int u, int v, int salt) {
        int h = u * 73856093 ^ v * 19349663 ^ salt * 83492791;
        h ^= h >>> 13;
        h *= 0x5BD1E995;
        return (h ^ h >>> 15) & 0x7FFFFFFF;
    }

    private static int pick(int[] palette, int u, int v, int salt) {
        return palette[noise(u, v, salt) % palette.length];
    }

    private static Color rock(int u, int v) {
        return new Color(v < B / 2 ? pick(DEEPSLATE, u, v, 1) : pick(STONE, u, v, 1));
    }

    /** A vein as vanilla builds it: filler, about a third ore, the odd raw block. */
    private static Color vein(int u, int v, boolean copper) {
        int roll = noise(u, v, 7) % 100;
        if (roll < 5) return new Color(copper ? RAW_COPPER : RAW_IRON);
        if (roll < 40) {
            if (copper && noise(u, v, 5) % 5 == 0) return new Color(COPPER_OXIDE);
            return new Color(copper ? pick(COPPER_ORE, u, v, 4) : pick(IRON_ORE, u, v, 4));
        }
        return new Color(copper ? pick(GRANITE, u, v, 2) : pick(TUFF, u, v, 2));
    }

    /** @param face 0 top, 1 left, 2 right; the right face continues the left one's u from 16. */
    private static Color texel(int face, int u, int v) {
        if (face == 0) return new Color(pick(STONE, u, v, 9));
        if (face == 2) return rock(u + B, v); // stripped: the veins are gone
        if (inBand(copperCentre(u), v)) return vein(u, v, true);
        if (inBand(ironCentre(u), v)) return vein(u, v, false);
        return rock(u, v);
    }

    // ------------------------------------------------------------------ drawing

    /** The cube by its three visible faces, each in texture pixels, lit like a block. */
    private static void box(Graphics2D g) {
        double h = B / 2.0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        // Left face (z = h): u along x, v along y.
        for (int u = 0; u < B; u++) {
            for (int v = 0; v < B; v++) {
                double x = -h + u;
                g.setColor(shade(texel(1, u, v), 0.82));
                g.fill(quad(x, v, h, x + 1, v, h, x + 1, v + 1, h, x, v + 1, h));
            }
        }
        // Right face (x = h): u along -z, v along y.
        for (int u = 0; u < B; u++) {
            for (int v = 0; v < B; v++) {
                double z = h - u - 1;
                g.setColor(shade(texel(2, u, v), 0.6));
                g.fill(quad(h, v, z, h, v, z + 1, h, v + 1, z + 1, h, v + 1, z));
            }
        }
        // Top (y = B).
        for (int u = 0; u < B; u++) {
            for (int w = 0; w < B; w++) {
                double x = -h + u;
                double z = -h + w;
                g.setColor(texel(0, u, w));
                g.fill(quad(x, B, z, x + 1, B, z, x + 1, B, z + 1, x, B, z + 1));
            }
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    /** Where the veins were on the right face: their top and bottom edge, in the plate's light. */
    private static void strippedOutline(Graphics2D g) {
        double h = B / 2.0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(new Color(LIGHT.getRed(), LIGHT.getGreen(), LIGHT.getBlue(), 225));
        for (int u = 0; u < B; u++) {
            double z = h - u - 1;
            for (int v = 0; v < B; v++) {
                double c = inBand(copperCentre(u + B), v) ? copperCentre(u + B)
                        : inBand(ironCentre(u + B), v) ? ironCentre(u + B) : Double.NaN;
                if (Double.isNaN(c)) continue;
                if (!inBand(c, v - 1) || !inBand(c, v + 1)) {
                    g.fill(quad(h, v, z, h, v, z + 1, h, v + 1, z + 1, h, v + 1, z));
                }
            }
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // The light the outline gives off, so the empty face reads as deliberate rather than unfinished.
        double[] mid = project(h, B / 2.0, 0);
        glow(g, mid[0], mid[1], 150, 60);
    }

    private static double[] project(double x, double y, double z) {
        return new double[]{cx + (x - z) * K * COS, cy + (x + z) * K * SIN - y * K};
    }

    private static Polygon quad(double... xyz) {
        Polygon p = new Polygon();
        for (int i = 0; i < xyz.length; i += 3) {
            double[] s = project(xyz[i], xyz[i + 1], xyz[i + 2]);
            p.addPoint((int) Math.round(s[0]), (int) Math.round(s[1]));
        }
        return p;
    }

    private static void shadow(Graphics2D g) {
        double[] c = project(0, 0, 0);
        float r = (float) (13 * K);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(c[0], c[1] + K * 2), r, new float[]{0f, 1f},
                new Color[]{new Color(0, 0, 0, 150), new Color(0, 0, 0, 0)}));
        g.fill(new Ellipse2D.Double(c[0] - r, c[1] + K * 2 - r * 0.5, r * 2, r));
    }

    private static void glow(Graphics2D g, double x, double y, float radius, int alpha) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), radius, new float[]{0f, 1f},
                new Color[]{new Color(LIGHT.getRed(), LIGHT.getGreen(), LIGHT.getBlue(), alpha),
                        new Color(LIGHT.getRed(), LIGHT.getGreen(), LIGHT.getBlue(), 0)}));
        g.fill(new Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2));
    }

    private static void grid(Graphics2D g) {
        g.setColor(new Color(255, 255, 255, 12));
        for (int i = 16; i < SIZE; i += 32) {
            g.drawLine(i, 0, i, SIZE);
            g.drawLine(0, i, SIZE, i);
        }
    }

    private static Color shade(Color c, double f) {
        return new Color((int) (c.getRed() * f), (int) (c.getGreen() * f), (int) (c.getBlue() * f));
    }

    private GenerateLogo() {}
}
