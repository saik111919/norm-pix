import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes app/src/main/res/raw/lucide_icons.txt: the Lucide drawings the map (res/raw/lucide_map.txt) names, as path data.
 *
 * Run (no Python on this PC; any JDK 11+):  java LucideBuild.java <svgDir> <lucide_map.txt> <lucide_icons.txt>
 * where <svgDir> holds one <name>.svg for each Lucide name in the map, from a tagged Lucide release
 * (https://raw.githubusercontent.com/lucide-icons/lucide/<tag>/icons/<name>.svg; the tag is in the map's header).
 *
 * Every Lucide icon is a 24 x 24 drawing of strokes (width 2, round caps and joins, no fill; a few dots say fill="currentColor", which
 * a 2-wide stroke on a radius of 1 or less already fills). So each shape becomes path data - a circle, ellipse, line, polyline and rect
 * as arcs and lines - and the app strokes them all alike. One line per icon: the name, then each path's data, separated by tabs.
 * Anything the tool does not know (a transform, another element) stops it, so a new Lucide release cannot be taken in silently wrong.
 */
public class LucideBuild {
    private static final Pattern ELEMENT = Pattern.compile("<(path|circle|ellipse|line|polyline|rect)\\b([^>]*?)/?>", Pattern.DOTALL);
    private static final Pattern ATTRIBUTE = Pattern.compile("([a-zA-Z][a-zA-Z0-9:-]*)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern ANY_TAG = Pattern.compile("<([a-zA-Z]+)\\b");
    /** Attributes that are the same on every Lucide shape, or that the geometry does not use. */
    private static final TreeSet<String> IGNORED = new TreeSet<>(List.of("fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin"));

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("usage: java LucideBuild.java <svgDir> <lucide_map.txt> <lucide_icons.txt>");
            System.exit(2);
        }
        Path svgDir = Path.of(args[0]);
        TreeSet<String> names = new TreeSet<>();
        for (String line : Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8)) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq < 0) throw new IllegalStateException("not Material=lucide: " + line);
            names.add(line.substring(eq + 1).strip());
        }
        TreeMap<String, List<String>> icons = new TreeMap<>();
        for (String name : names) {
            String svg = Files.readString(svgDir.resolve(name + ".svg"), StandardCharsets.UTF_8);
            icons.put(name, paths(name, svg));
        }
        StringBuilder out = new StringBuilder();
        out.append("# Made by native/design/lucide/LucideBuild.java from res/raw/lucide_map.txt: do not edit.\n");
        out.append("# Lucide (ISC, with the Feather-derived icons MIT; assets/licenses/lucide.txt). name, then each path's data, tab-separated.\n");
        int shapes = 0;
        for (var e : icons.entrySet()) {
            out.append(e.getKey());
            for (String d : e.getValue()) out.append('\t').append(d);
            out.append('\n');
            shapes += e.getValue().size();
        }
        Files.writeString(Path.of(args[2]), out.toString(), StandardCharsets.UTF_8);
        System.out.println(icons.size() + " icons, " + shapes + " shapes, " + out.length() + " characters -> " + args[2]);
    }

    private static List<String> paths(String name, String svg) {
        // Every tag in the file must be the svg itself or a shape this tool converts.
        Matcher tags = ANY_TAG.matcher(svg);
        while (tags.find()) {
            String tag = tags.group(1);
            if (!tag.equals("svg") && !ELEMENT.matcher("<" + tag + "/>").matches()) {
                throw new IllegalStateException(name + ": <" + tag + "> is not a shape this tool knows");
            }
        }
        List<String> result = new ArrayList<>();
        Matcher m = ELEMENT.matcher(svg);
        while (m.find()) {
            String kind = m.group(1);
            TreeMap<String, String> a = new TreeMap<>();
            Matcher attributes = ATTRIBUTE.matcher(m.group(2));
            while (attributes.find()) a.put(attributes.group(1), attributes.group(2));
            for (String key : a.keySet()) {
                if (!IGNORED.contains(key) && !GEOMETRY.contains(key)) throw new IllegalStateException(name + ": <" + kind + "> has " + key);
            }
            result.add(switch (kind) {
                case "path" -> need(a, "d", name).replaceAll("\\s+", " ").strip();
                case "circle" -> ellipse(num(a, "cx"), num(a, "cy"), num(a, "r"), num(a, "r"));
                case "ellipse" -> ellipse(num(a, "cx"), num(a, "cy"), num(a, "rx"), num(a, "ry"));
                case "line" -> "M" + n(num(a, "x1")) + " " + n(num(a, "y1")) + "L" + n(num(a, "x2")) + " " + n(num(a, "y2"));
                case "polyline" -> polyline(need(a, "points", name));
                case "rect" -> rect(num(a, "x"), num(a, "y"), num(a, "width"), num(a, "height"), a);
                default -> throw new IllegalStateException(name + ": " + kind);
            });
        }
        if (result.isEmpty()) throw new IllegalStateException(name + ": nothing drawn");
        return result;
    }

    private static final TreeSet<String> GEOMETRY = new TreeSet<>(
        List.of("d", "cx", "cy", "r", "rx", "ry", "x1", "y1", "x2", "y2", "points", "x", "y", "width", "height"));

    private static String need(TreeMap<String, String> a, String key, String name) {
        String v = a.get(key);
        if (v == null) throw new IllegalStateException(name + ": no " + key);
        return v;
    }

    private static double num(TreeMap<String, String> a, String key) {
        String v = a.get(key);
        return v == null ? 0.0 : Double.parseDouble(v);
    }

    private static String n(double v) {
        double r = Math.round(v * 10000.0) / 10000.0;
        return r == Math.rint(r) ? Long.toString((long) r) : Double.toString(r);
    }

    private static String ellipse(double cx, double cy, double rx, double ry) {
        return "M" + n(cx - rx) + " " + n(cy)
            + "a" + n(rx) + " " + n(ry) + " 0 1 0 " + n(2 * rx) + " 0"
            + "a" + n(rx) + " " + n(ry) + " 0 1 0 " + n(-2 * rx) + " 0z";
    }

    private static String polyline(String points) {
        String[] p = points.strip().split("[\\s,]+");
        StringBuilder d = new StringBuilder();
        for (int i = 0; i + 1 < p.length; i += 2) d.append(i == 0 ? "M" : "L").append(p[i]).append(' ').append(p[i + 1]);
        return d.toString();
    }

    private static String rect(double x, double y, double w, double h, TreeMap<String, String> a) {
        double rx = a.containsKey("rx") ? num(a, "rx") : num(a, "ry");
        double ry = a.containsKey("ry") ? num(a, "ry") : rx;
        rx = Math.min(rx, w / 2);
        ry = Math.min(ry, h / 2);
        if (rx == 0 && ry == 0) return "M" + n(x) + " " + n(y) + "h" + n(w) + "v" + n(h) + "h" + n(-w) + "z";
        return "M" + n(x + rx) + " " + n(y)
            + "h" + n(w - 2 * rx) + "a" + n(rx) + " " + n(ry) + " 0 0 1 " + n(rx) + " " + n(ry)
            + "v" + n(h - 2 * ry) + "a" + n(rx) + " " + n(ry) + " 0 0 1 " + n(-rx) + " " + n(ry)
            + "h" + n(-(w - 2 * rx)) + "a" + n(rx) + " " + n(ry) + " 0 0 1 " + n(-rx) + " " + n(-ry)
            + "v" + n(-(h - 2 * ry)) + "a" + n(rx) + " " + n(ry) + " 0 0 1 " + n(rx) + " " + n(-ry) + "z";
    }
}
