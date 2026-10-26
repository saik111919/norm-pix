/*
 * Draws the cookie clock widgets' faces as Android vector drawables (res/drawable/widget_cookie_*.xml), and a sheet of
 * previews (PNG) to look at before the app is built.
 *
 * The widgets (CookieAnalogWidget, CookieNumbersWidget, CookieDigitalWidget) are the platform's AnalogClock and
 * TextClock, which keep time without NormPix. An AnalogClock draws a dial and turns its hands over it, so the hands are
 * drawables too: pictures of a hand pointing at twelve, with the pivot at the middle of a 200-unit square.
 *
 * The shape is Material 3 Expressive's four-sided cookie: eight points rounded one by one, the corners 0.258 and the middles
 * 0.233 (the cubics below are androidx.graphics:graphics-shapes' RoundedPolygon for them, in a unit square), grown to fill
 * the 200-unit face. Numerals are the app's own font (GS Flex) drawn as paths, like the watch face's.
 *
 *     java CookieFace.java                 # writes into ../../app/src/main/res/drawable, and a sheet of previews to the temp folder
 *     java CookieFace.java <res-dir> <png>
 *
 * All sizes are in the drawable's 200-unit viewport. CookieFace.kt in the app places the digital widget's text from the
 * same numbers (HOUR_DIGIT_UNITS and the rest): keep the two in step.
 */

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

public class CookieFace {
    // ---- the shape -------------------------------------------------------------------------------------------------
    static final String CUBICS = """
        0.9239047 0.9234172 0.8543776 0.9930584 0.7464076 1.0243196 0.63910896 0.9780226
        0.63910896 0.9780226 0.62350875 0.9712914 0.6079086 0.9645603 0.5923084 0.9578291
        0.5923084 0.9578291 0.5333945 0.93240905 0.46660545 0.93240905 0.4076916 0.9578291
        0.4076916 0.9578291 0.3920914 0.9645603 0.37649122 0.9712914 0.360891 0.9780225
        0.360891 0.9780225 0.14629383 1.0706167 -0.07098896 0.8529776 0.021956757 0.6385324
        0.021956757 0.6385324 0.028584316 0.6232412 0.03521188 0.60795003 0.04183945 0.5926588
        0.04183945 0.5926588 0.06746159 0.53354317 0.06746158 0.4664568 0.041839443 0.40734115
        0.041839443 0.40734115 0.03521187 0.39204994 0.028584298 0.37675875 0.021956757 0.36146754
        0.021956757 0.36146754 -0.07098893 0.14702238 0.14629386 -0.07061675 0.36089104 0.021977425
        0.36089104 0.021977425 0.37649122 0.02870857 0.39209142 0.035439715 0.4076916 0.042170867
        0.4076916 0.042170867 0.46660542 0.067590944 0.5333946 0.067590944 0.5923084 0.04217086
        0.5923084 0.04217086 0.6079086 0.035439715 0.6235088 0.02870857 0.63910896 0.021977454
        0.63910896 0.021977454 0.8537061 -0.07061675 1.0709889 0.14702237 0.97804326 0.36146754
        0.97804326 0.36146754 0.97141564 0.37675875 0.9647881 0.39204997 0.9581605 0.40734115
        0.9581605 0.40734115 0.9325384 0.4664568 0.9325384 0.53354317 0.9581605 0.5926588
        0.9581605 0.5926588 0.964788 0.60795 0.97141564 0.6232412 0.9780432 0.6385324
        0.9780432 0.6385324 1.0245161 0.74575496 0.9934318 0.85377604 0.9239047 0.9234172
        """;

    static final double C = 100.0; // the middle of the 200-unit viewport
    static final int INTRINSIC_DP = 280; // AnalogClock never draws larger than its drawables: the widget's largest size

    // ---- the face --------------------------------------------------------------------------------------------------
    static final double TICK_INSET = 8.0;   // how far inside the edge the ticks end
    static final double TICK_MINOR = 3.0, TICK_MAJOR = 6.0, TICK_TWELVE = 8.5;
    static final double NUMERAL_FACTOR = 0.66, NUMERAL_H = 15.0; // numerals sit this share of the way to the edge: they follow the lobes

    // ---- the hands (pointing at twelve) -----------------------------------------------------------------------------
    // [tail, length, width]; the plain face has long hands, the one with numerals shorter so they stay off the numbers.
    static final double[] HOUR_PLAIN = {9, 44, 6.6}, MINUTE_PLAIN = {9, 66, 4.6}, SECOND_PLAIN = {17, 74, 1.8};
    static final double[] HOUR_NUMBERS = {9, 32, 6.4}, MINUTE_NUMBERS = {9, 44, 4.4}, SECOND_NUMBERS = {17, 70, 1.8};
    static final double HALO = 2.0;          // the face's colour round a hand, so it reads over ticks and numerals
    static final double CAP_R = 5.6, CAP_DOT = 2.2, SECOND_CAP_R = 3.8;

    // ---- the digital face (CookieFace.kt reads these) ---------------------------------------------------------------
    static final double HOUR_DIGIT = 44, MINUTE_DIGIT = 44, DATE_DIGIT = 10;
    static final double HOUR_Y = 68, MINUTE_Y = 122, DATE_Y = 160; // where the digits' middles sit in the 200-unit face
    static final double MARKER_R = 2.6;

    // ---- colours: role -> theme values (dark, light), the app's widget_clock_* resources -----------------------------
    static final String[] ROLES = {"face", "ink", "secondary", "tick", "accent"};
    static final int[][] DARK = {{0x08, 0x09, 0x0D}, {0xF5, 0xF5, 0xFA}, {0xB9, 0xB9, 0xF2}, {0x48, 0x4B, 0x5D}, {0xFF, 0x8A, 0x24}};
    static final int[][] LIGHT = {{0xF7, 0xF8, 0xFC}, {0x17, 0x19, 0x23}, {0x64, 0x6B, 0x7A}, {0xD5, 0xD9, 0xE3}, {0xFF, 0x8A, 0x24}};

    // ---- primitives, drawn both as vector XML and as pixels --------------------------------------------------------
    record Item(Shape shape, String role, double strokeWidth, boolean fill, boolean round, double alpha) {}

    static Item fill(Shape s, String role) { return new Item(s, role, 0, true, false, 1); }
    static Item bold(Shape s, String role, double w) { return new Item(s, role, w, true, true, 1); }
    static Item stroke(Shape s, String role, double w) { return new Item(s, role, w, false, true, 1); }
    static Item faded(Item i, double alpha) { return new Item(i.shape(), i.role(), i.strokeWidth(), i.fill(), i.round(), alpha); }

    static Path2D cookie;                 // the outline, in 200 units
    static final List<Point2D.Double> outline = new ArrayList<>();
    static Font font;

    public static void main(String[] args) throws Exception {
        Path base = Path.of("").toAbsolutePath(); // run it from this folder
        File res = new File(args.length > 0 ? args[0] : base.resolve("../../app/src/main/res/drawable").normalize().toString());
        File png = new File(args.length > 1 ? args[1] : System.getProperty("java.io.tmpdir") + File.separator + "normpix-cookie-clock.png");
        File ttf = new File(res.getParentFile(), "font/gs_flex.ttf");
        font = Font.createFont(Font.TRUETYPE_FONT, ttf);

        buildCookie();
        System.out.printf(Locale.ROOT, "edge: %.1f at twelve, %.1f at the corner%n", edge(0), edge(45));

        // Drawables
        write(res, "widget_cookie_dial", "The cookie analog clock's face: the cookie and the minute ticks round it.", INTRINSIC_DP, dial(false));
        write(res, "widget_cookie_dial_numbers", "The cookie clock's face with numerals: the cookie, minute ticks and 1 to 12.", INTRINSIC_DP, dial(true));
        write(res, "widget_cookie_hour", "The cookie analog clock's hour hand, pointing at twelve.", INTRINSIC_DP, hourHand(HOUR_PLAIN));
        write(res, "widget_cookie_minute", "The cookie analog clock's minute hand, pointing at twelve; its cap is the pivot.", INTRINSIC_DP, minuteHand(MINUTE_PLAIN, false));
        write(res, "widget_cookie_second", "The cookie analog clock's second hand (Android 12 and later), pointing at twelve.", INTRINSIC_DP, secondHand(SECOND_PLAIN));
        write(res, "widget_cookie_hour_numbers", "The hour hand of the cookie clock with numerals: shorter, to stay off the numbers.", INTRINSIC_DP, hourHand(HOUR_NUMBERS));
        write(res, "widget_cookie_minute_numbers", "The minute hand of the cookie clock with numerals.", INTRINSIC_DP, minuteHand(MINUTE_NUMBERS, true));
        write(res, "widget_cookie_second_numbers", "The second hand of the cookie clock with numerals (Android 12 and later).", INTRINSIC_DP, secondHand(SECOND_NUMBERS));
        write(res, "widget_cookie_face", "The cookie digital clock's face: the cookie and a marker at nine o'clock. The time is text over it.", 200, digitalFace());

        // Previews for the widget picker: 10:09 and 36 seconds.
        write(res, "widget_cookie_analog_preview", "The cookie analog clock in the widget picker, at 10:09.", 200,
            concat(dial(false), hands(HOUR_PLAIN, MINUTE_PLAIN, SECOND_PLAIN, false)));
        write(res, "widget_cookie_numbers_preview", "The cookie clock with numerals in the widget picker, at 10:09.", 200,
            concat(dial(true), hands(HOUR_NUMBERS, MINUTE_NUMBERS, SECOND_NUMBERS, true)));
        write(res, "widget_cookie_digital_preview", "The cookie digital clock in the widget picker, at 10:09.", 200, digitalPreview());
        write(res, "widget_clock_preview", "The watch dial widget in the widget picker, at 12:45 - the picture ClockWidget draws every minute.", 200,
            watchDial(45, "12", "45"));

        sheet(png);
        System.out.println("wrote drawables to " + res + " and " + png);
    }

    // ---- the cookie ------------------------------------------------------------------------------------------------
    static void buildCookie() {
        double[][] cubics = new double[17][8];
        String[] lines = CUBICS.strip().split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String[] p = lines[i].trim().split("\\s+");
            for (int j = 0; j < 8; j++) cubics[i][j] = Double.parseDouble(p[j]);
        }
        // The curve's own extent (the control points reach further than the curve does).
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double[] c : cubics) {
            for (int s = 0; s <= 200; s++) {
                double t = s / 200.0;
                for (int axis = 0; axis < 2; axis++) {
                    double v = bez(c[axis], c[2 + axis], c[4 + axis], c[6 + axis], t);
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
            }
        }
        double k = 200.0 / (max - min);
        cookie = new Path2D.Double();
        cookie.moveTo((cubics[0][0] - min) * k, (cubics[0][1] - min) * k);
        for (double[] c : cubics) {
            cookie.curveTo((c[2] - min) * k, (c[3] - min) * k, (c[4] - min) * k, (c[5] - min) * k, (c[6] - min) * k, (c[7] - min) * k);
            for (int s = 0; s < 40; s++) {
                double t = s / 40.0;
                outline.add(new Point2D.Double((bez(c[0], c[2], c[4], c[6], t) - min) * k, (bez(c[1], c[3], c[5], c[7], t) - min) * k));
            }
        }
        cookie.closePath();
    }

    static double bez(double a, double b, double c, double d, double t) {
        double u = 1 - t;
        return u * u * u * a + 3 * u * u * t * b + 3 * u * t * t * c + t * t * t * d;
    }

    /** How far the cookie's edge is from the middle, [deg] clockwise from twelve. */
    static double edge(double deg) {
        double a = Math.toRadians(deg), dx = Math.sin(a), dy = -Math.cos(a);
        double best = 0;
        for (int i = 0; i < outline.size(); i++) {
            Point2D.Double p = outline.get(i), q = outline.get((i + 1) % outline.size());
            double ex = q.x - p.x, ey = q.y - p.y;
            double den = dx * ey - dy * ex;
            if (Math.abs(den) < 1e-12) continue;
            double t = ((p.x - C) * ey - (p.y - C) * ex) / den; // along the ray
            double u = ((p.x - C) * dy - (p.y - C) * dx) / den; // along the segment
            if (t > 0 && u >= 0 && u <= 1) best = Math.max(best, t);
        }
        return best;
    }

    static Point2D at(double deg, double r) {
        double a = Math.toRadians(deg);
        return new Point2D.Double(C + r * Math.sin(a), C - r * Math.cos(a));
    }

    // ---- the faces -------------------------------------------------------------------------------------------------
    static List<Item> dial(boolean numerals) {
        List<Item> items = new ArrayList<>();
        items.add(fill(cookie, "face"));
        Path2D minor = new Path2D.Double(), major = new Path2D.Double(), twelve = new Path2D.Double();
        for (int i = 0; i < 60; i++) {
            double deg = i * 6.0, outer = edge(deg) - TICK_INSET;
            boolean hour = i % 5 == 0;
            double len = i == 0 && !numerals ? TICK_TWELVE : hour && !numerals ? TICK_MAJOR : TICK_MINOR;
            Path2D into = i == 0 && !numerals ? twelve : hour && !numerals ? major : minor;
            Point2D a = at(deg, outer), b = at(deg, outer - len);
            into.append(new Line2D.Double(a, b), false);
        }
        items.add(stroke(minor, "tick", 1.1));
        if (!numerals) {
            items.add(stroke(major, "secondary", 1.9));
            items.add(stroke(twelve, "accent", 2.4));
        } else {
            for (int h = 1; h <= 12; h++) {
                boolean cardinal = h % 3 == 0;
                Point2D p = at(h * 30.0, NUMERAL_FACTOR * edge(h * 30.0));
                items.add(bold(text(String.valueOf(h), p.getX(), p.getY(), NUMERAL_H), cardinal ? "ink" : "secondary", 0.45));
            }
            // The twelve gets its accent in a dot beside the ticks, so the top of the face is known at a glance.
            Point2D dot = at(0, edge(0) - TICK_INSET - TICK_MINOR - 3.2);
            items.add(fill(new Ellipse2D.Double(dot.getX() - 1.6, dot.getY() - 1.6, 3.2, 3.2), "accent"));
        }
        return items;
    }

    static List<Item> digitalFace() {
        List<Item> items = new ArrayList<>();
        items.add(fill(cookie, "face"));
        double x = C - (edge(270) - TICK_INSET - 1);
        items.add(fill(new Ellipse2D.Double(x - MARKER_R, C - MARKER_R, 2 * MARKER_R, 2 * MARKER_R), "accent"));
        return items;
    }

    static List<Item> digitalPreview() {
        List<Item> items = new ArrayList<>(digitalFace());
        items.add(bold(text("10", C, HOUR_Y, HOUR_DIGIT), "ink", 0.9));
        items.add(bold(text("09", C, MINUTE_Y, MINUTE_DIGIT), "secondary", 0.9));
        items.add(bold(text("MON 26 OCT", C, DATE_Y, DATE_DIGIT), "secondary", 0.3));
        return items;
    }

    // ---- the watch dial (WatchDialFace.kt has the same numbers: keep them in step) ----------------------------------
    static final double W_OUTER_OUT = 99.5, W_OUTER_MINOR = 94.5, W_OUTER_MAJOR = 90.5, W_OUTER_R = 82.4, W_OUTER_SIZE = 11.7;
    static final double W_INNER_OUT = 69, W_INNER_MINOR = 64.5, W_INNER_MAJOR = 61, W_INNER_R = 54, W_INNER_SIZE = 12.8;
    static final double W_PILL_LEFT = 128.6, W_PILL_H = 15.2, W_CLEARANCE = 22, W_FADE = 6, DIGIT_EM = 0.7326;

    static double wrap(double deg) { return ((deg % 360) + 360) % 360; }
    static double wInner(int value, int minute) { return wrap(90 - 6.0 * (value - minute)); }
    static double wOuter(int value) { return wrap(90 - 6.0 * value); }

    /** The watch dial with its minute ring turned to [minute], and [hour] and [min] as the text over it. */
    static List<Item> watchDial(int minute, String hour, String min) {
        List<Item> items = new ArrayList<>();
        items.add(fill(new Ellipse2D.Double(0, 0, 200, 200), "face"));
        Path2D innerMinor = new Path2D.Double(), innerMajor = new Path2D.Double();
        for (int i = 0; i < 60; i++) {
            boolean big = i % 5 == 0;
            (big ? innerMajor : innerMinor).append(new Line2D.Double(at(wInner(i, minute), big ? W_INNER_MAJOR : W_INNER_MINOR), at(wInner(i, minute), W_INNER_OUT)), false);
        }
        items.add(stroke(innerMinor, "tick", 0.8));
        items.add(faded(stroke(innerMajor, "secondary", 1.4), 190 / 255.0));
        for (int v = 0; v < 60; v += 5) {
            double a = wInner(v, minute);
            double off = Math.abs(a - 90);
            double opacity = Math.max(0, Math.min(1, (Math.min(off, 360 - off) - W_CLEARANCE) / W_FADE));
            if (opacity == 0) continue;
            Point2D p = at(a, W_INNER_R);
            items.add(faded(bold(text(String.format("%02d", v), p.getX(), p.getY(), W_INNER_SIZE * DIGIT_EM), "secondary", 0.3), opacity));
        }
        // The pill, filled with the face and open at the right, where the face's edge clips it.
        double edgeX = C + Math.sqrt(100 * 100 - W_PILL_H * W_PILL_H);
        java.awt.geom.Area pill = new java.awt.geom.Area(new java.awt.geom.RoundRectangle2D.Double(W_PILL_LEFT, C - W_PILL_H, 200, 2 * W_PILL_H, 2 * W_PILL_H, 2 * W_PILL_H));
        pill.intersect(new java.awt.geom.Area(new Ellipse2D.Double(0, 0, 200, 200)));
        items.add(fill(pill, "face"));
        Path2D rim = new Path2D.Double();
        rim.moveTo(edgeX, C - W_PILL_H);
        rim.lineTo(W_PILL_LEFT + W_PILL_H, C - W_PILL_H);
        rim.append(new java.awt.geom.Arc2D.Double(W_PILL_LEFT, C - W_PILL_H, 2 * W_PILL_H, 2 * W_PILL_H, 90, 180, java.awt.geom.Arc2D.OPEN), true);
        rim.lineTo(edgeX, C + W_PILL_H);
        items.add(faded(stroke(rim, "secondary", 1.3), 150 / 255.0));
        // The seconds ring, still.
        Path2D outerMinor = new Path2D.Double(), outerMajor = new Path2D.Double();
        for (int i = 0; i < 60; i++) {
            boolean big = i % 5 == 0;
            (big ? outerMajor : outerMinor).append(new Line2D.Double(at(wOuter(i), big ? W_OUTER_MAJOR : W_OUTER_MINOR), at(wOuter(i), W_OUTER_OUT)), false);
        }
        items.add(stroke(outerMinor, "tick", 0.9));
        items.add(faded(stroke(outerMajor, "secondary", 1.6), 190 / 255.0));
        for (int v = 0; v < 60; v += 5) {
            Point2D p = at(wOuter(v), W_OUTER_R);
            items.add(bold(text(String.format("%02d", v), p.getX(), p.getY(), W_OUTER_SIZE * DIGIT_EM), "secondary", 0.3));
        }
        items.add(stroke(new Line2D.Double(at(90, W_INNER_MAJOR), at(90, W_INNER_OUT)), "accent", 1.9));
        // The text the launcher keeps, here as outlines: the hour in the middle, the minute in the pill.
        items.add(bold(text(hour, C, C, 30), "ink", 0.9));
        items.add(bold(text(min, 151, C, 11), "ink", 0.5));
        return items;
    }

    // ---- the hands -------------------------------------------------------------------------------------------------
    static Item haloOf(double[] hand) {
        return stroke(handLine(hand), "face", hand[2] + 2 * HALO);
    }

    static Path2D handLine(double[] hand) {
        Path2D p = new Path2D.Double();
        p.moveTo(C, C + hand[0]);
        p.lineTo(C, C - hand[1]);
        return p;
    }

    static List<Item> hourHand(double[] hand) {
        return List.of(haloOf(hand), stroke(handLine(hand), "ink", hand[2]));
    }

    static List<Item> minuteHand(double[] hand, boolean numerals) {
        List<Item> items = new ArrayList<>(List.of(haloOf(hand), stroke(handLine(hand), "ink", hand[2])));
        items.add(fill(new Ellipse2D.Double(C - CAP_R, C - CAP_R, 2 * CAP_R, 2 * CAP_R), "ink"));
        items.add(fill(new Ellipse2D.Double(C - CAP_DOT, C - CAP_DOT, 2 * CAP_DOT, 2 * CAP_DOT), "accent"));
        return items;
    }

    static List<Item> secondHand(double[] hand) {
        List<Item> items = new ArrayList<>();
        items.add(stroke(handLine(hand), "accent", hand[2]));
        items.add(fill(new Ellipse2D.Double(C - SECOND_CAP_R, C - SECOND_CAP_R, 2 * SECOND_CAP_R, 2 * SECOND_CAP_R), "accent"));
        items.add(fill(new Ellipse2D.Double(C - 1.3, C - 1.3, 2.6, 2.6), "face"));
        return items;
    }

    /** The three hands at 10:09 and 36 seconds. */
    static List<Item> hands(double[] hour, double[] minute, double[] second, boolean numerals) {
        double hourDeg = (10 + 9 / 60.0) * 30, minuteDeg = (9 + 36 / 60.0) * 6, secondDeg = 36 * 6;
        List<Item> items = new ArrayList<>();
        items.addAll(turned(hourHand(hour), hourDeg));
        items.addAll(turned(minuteHand(minute, numerals), minuteDeg));
        items.addAll(turned(secondHand(second), secondDeg));
        return items;
    }

    static List<Item> turned(List<Item> items, double deg) {
        AffineTransform t = AffineTransform.getRotateInstance(Math.toRadians(deg), C, C);
        List<Item> out = new ArrayList<>();
        for (Item i : items) out.add(new Item(t.createTransformedShape(i.shape()), i.role(), i.strokeWidth(), i.fill(), i.round(), i.alpha()));
        return out;
    }

    @SafeVarargs
    static List<Item> concat(List<Item>... parts) {
        List<Item> out = new ArrayList<>();
        for (List<Item> p : parts) out.addAll(p);
        return out;
    }

    // ---- text ------------------------------------------------------------------------------------------------------
    static double digitHeight;

    /** [text] centred on (cx, cy), its digits [height] units tall. */
    static Shape text(String text, double cx, double cy, double height) {
        FontRenderContext frc = new FontRenderContext(null, true, true);
        Font f = font.deriveFont(100f);
        if (digitHeight == 0) digitHeight = -f.createGlyphVector(frc, "0").getOutline().getBounds2D().getY();
        GlyphVector gv = f.createGlyphVector(frc, text);
        Rectangle2D b = gv.getOutline().getBounds2D();
        double s = height / digitHeight;
        AffineTransform t = new AffineTransform();
        t.translate(cx, cy);
        t.scale(s, s);
        t.translate(-b.getCenterX(), digitHeight / 2);
        return t.createTransformedShape(gv.getOutline());
    }

    // ---- writing ---------------------------------------------------------------------------------------------------
    static String num(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.equals("-0") ? "0" : s;
    }

    static String data(Shape shape) {
        StringBuilder sb = new StringBuilder();
        double[] c = new double[6];
        for (PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO -> sb.append(sb.isEmpty() ? "" : " ").append('M').append(num(c[0])).append(',').append(num(c[1]));
                case PathIterator.SEG_LINETO -> sb.append(" L").append(num(c[0])).append(',').append(num(c[1]));
                case PathIterator.SEG_QUADTO -> sb.append(" Q").append(num(c[0])).append(',').append(num(c[1])).append(' ').append(num(c[2])).append(',').append(num(c[3]));
                case PathIterator.SEG_CUBICTO -> sb.append(" C").append(num(c[0])).append(',').append(num(c[1])).append(' ').append(num(c[2])).append(',').append(num(c[3])).append(' ').append(num(c[4])).append(',').append(num(c[5]));
                case PathIterator.SEG_CLOSE -> sb.append(" Z");
                default -> throw new IllegalStateException();
            }
        }
        return sb.toString();
    }

    static void write(File res, String name, String about, int dp, List<Item> items) throws IOException {
        StringBuilder x = new StringBuilder();
        x.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        x.append("<!-- ").append(about).append("\n     Drawn by design/cookie-clock/CookieFace.java: change it there, not here. -->\n");
        // Larger than 200 on purpose: AnalogClock never draws larger than its drawables, so these are as large as the widget can be.
        boolean large = dp > 200;
        x.append("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
        if (large) x.append("    xmlns:tools=\"http://schemas.android.com/tools\"\n    tools:ignore=\"VectorRaster\"\n");
        x.append("    android:width=\"").append(dp).append("dp\"\n    android:height=\"").append(dp).append("dp\"\n");
        x.append("    android:viewportWidth=\"200\"\n    android:viewportHeight=\"200\">\n");
        for (Item i : items) {
            x.append("    <path\n");
            if (i.fill()) x.append("        android:fillColor=\"@color/widget_clock_").append(i.role()).append("\"\n");
            if (i.fill() && i.alpha() < 1) x.append("        android:fillAlpha=\"").append(num(i.alpha())).append("\"\n");
            if (i.strokeWidth() > 0) {
                x.append("        android:strokeColor=\"@color/widget_clock_").append(i.role()).append("\"\n");
                x.append("        android:strokeWidth=\"").append(num(i.strokeWidth())).append("\"\n");
                if (i.alpha() < 1) x.append("        android:strokeAlpha=\"").append(num(i.alpha())).append("\"\n");
                x.append("        android:strokeLineCap=\"round\"\n");
                if (i.round()) x.append("        android:strokeLineJoin=\"round\"\n");
            }
            x.append("        android:pathData=\"").append(data(i.shape())).append("\" />\n");
        }
        x.append("</vector>\n");
        Files.writeString(new File(res, name + ".xml").toPath(), x.toString(), StandardCharsets.UTF_8);
    }

    // ---- the sheet -------------------------------------------------------------------------------------------------
    static void sheet(File png) throws IOException {
        int cell = 360, pad = 24;
        List<List<Item>> faces = List.of(
            concat(dial(false), hands(HOUR_PLAIN, MINUTE_PLAIN, SECOND_PLAIN, false)),
            concat(dial(true), hands(HOUR_NUMBERS, MINUTE_NUMBERS, SECOND_NUMBERS, true)),
            digitalPreview(),
            watchDial(45, "12", "45"));
        int[][][] themes = {DARK, LIGHT};
        int[] grounds = {0x2B2D33, 0xCFCFD6};
        BufferedImage img = new BufferedImage(pad + (cell + pad) * faces.size(), pad + (cell + pad) * 2, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        for (int row = 0; row < 2; row++) {
            g.setColor(new Color(grounds[row]));
            g.fillRect(0, row * (cell + pad), img.getWidth(), cell + pad);
            for (int col = 0; col < faces.size(); col++) {
                AffineTransform at = new AffineTransform();
                at.translate(pad + col * (cell + pad), pad / 2 + row * (cell + pad));
                at.scale(cell / 200.0, cell / 200.0);
                for (Item i : faces.get(col)) {
                    int[] rgb = themes[row][java.util.Arrays.asList(ROLES).indexOf(i.role())];
                    g.setColor(new Color(rgb[0], rgb[1], rgb[2], (int) Math.round(255 * i.alpha())));
                    Shape s = at.createTransformedShape(i.shape());
                    if (i.fill()) g.fill(s);
                    if (i.strokeWidth() > 0) {
                        g.setStroke(new BasicStroke((float) (i.strokeWidth() * cell / 200.0), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g.draw(s);
                    }
                }
            }
        }
        g.dispose();
        ImageIO.write(img, "png", png);
    }
}
