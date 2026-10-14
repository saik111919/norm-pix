using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;
using System.Runtime.InteropServices;

// Turns one flattened icon picture (or one tile of a grid of them) into the two layers of an Android adaptive icon:
// a background that is a smooth fit of the picture's own background, and a foreground holding the symbol, faded out
// at its edge, sized so that the symbol sits inside the 66 dp safe zone of the 108 dp canvas.
public class Img {
    public int W, H;
    public float[] R, G, B;

    public static Img Load(string path) {
        using (var bmp = new Bitmap(path)) {
            var img = new Img { W = bmp.Width, H = bmp.Height };
            var data = bmp.LockBits(new Rectangle(0, 0, img.W, img.H), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
            var px = new int[img.W * img.H];
            Marshal.Copy(data.Scan0, px, 0, px.Length);
            bmp.UnlockBits(data);
            img.R = new float[px.Length]; img.G = new float[px.Length]; img.B = new float[px.Length];
            for (int i = 0; i < px.Length; i++) {
                img.R[i] = (px[i] >> 16) & 255; img.G[i] = (px[i] >> 8) & 255; img.B[i] = px[i] & 255;
            }
            return img;
        }
    }

    // Bilinear sample, clamped to the picture.
    public void Sample(double x, double y, out double r, out double g, out double b) {
        x -= 0.5; y -= 0.5;
        if (x < 0) x = 0; if (y < 0) y = 0; if (x > W - 1) x = W - 1; if (y > H - 1) y = H - 1;
        int x0 = (int)x, y0 = (int)y; int x1 = Math.Min(x0 + 1, W - 1), y1 = Math.Min(y0 + 1, H - 1);
        double fx = x - x0, fy = y - y0;
        int a = y0 * W + x0, bI = y0 * W + x1, c = y1 * W + x0, d = y1 * W + x1;
        double w00 = (1 - fx) * (1 - fy), w10 = fx * (1 - fy), w01 = (1 - fx) * fy, w11 = fx * fy;
        r = R[a] * w00 + R[bI] * w10 + R[c] * w01 + R[d] * w11;
        g = G[a] * w00 + G[bI] * w10 + G[c] * w01 + G[d] * w11;
        b = B[a] * w00 + B[bI] * w10 + B[c] * w01 + B[d] * w11;
    }
}

public class Spec {
    public string Name;
    public string Source;
    // The part of the picture that belongs to this icon, inside any frame (source pixels), and its corner radius.
    public double X0, Y0, X1, Y1, Corner;
    // Areas never fitted nor taken as the symbol (a watermark).
    public List<RectangleF> Exclude = new List<RectangleF>();
    // Optional fixed symbol box (source pixels) when detection is fooled by the background's pattern.
    public RectangleF? SymbolBox;
    public double SymbolThreshold = 40;
    // Room kept around the symbol for its shadow or glow, and the width of the fade (source pixels).
    public double Margin = 14, Feather = 14;
    // Half-diagonal of the symbol's box on the 108-unit canvas (66 dp safe zone = radius 33).
    public double Reach = 33.5;
    // Fit the background with a quadratic surface (true) or a plane (false).
    public bool Quadratic = true;

    public bool Inside(double x, double y) {
        if (x < X0 || y < Y0 || x > X1 || y > Y1) return false;
        double cx = Math.Max(X0 + Corner, Math.Min(x, X1 - Corner));
        double cy = Math.Max(Y0 + Corner, Math.Min(y, Y1 - Corner));
        double dx = x - cx, dy = y - cy;
        if (dx * dx + dy * dy > Corner * Corner) return false;
        foreach (var e in Exclude) if (e.Contains((float)x, (float)y)) return false;
        return true;
    }

    // Distance from (x,y) to the edge of the region, inwards (0 outside).
    public double DepthInside(double x, double y) {
        if (!Inside(x, y)) return 0;
        double cx = Math.Max(X0 + Corner, Math.Min(x, X1 - Corner));
        double cy = Math.Max(Y0 + Corner, Math.Min(y, Y1 - Corner));
        double dx = x - cx, dy = y - cy;
        double toCorner = Corner - Math.Sqrt(dx * dx + dy * dy);
        double toSide = Math.Min(Math.Min(x - X0, X1 - x), Math.Min(y - Y0, Y1 - y));
        return (dx != 0 || dy != 0) ? Math.Min(toCorner, toSide + 0) : toSide;
    }
}

public class Surface {
    public double[][] C = new double[3][];
    public double Cx, Cy, S;
    public bool Quadratic;

    public double[] Terms(double x, double y) {
        double u = (x - Cx) / S, v = (y - Cy) / S;
        return Quadratic ? new[] { 1, u, v, u * u, u * v, v * v } : new[] { 1.0, u, v };
    }

    public void Eval(double x, double y, out double r, out double g, out double b) {
        var t = Terms(x, y);
        r = 0; g = 0; b = 0;
        for (int i = 0; i < t.Length; i++) { r += C[0][i] * t[i]; g += C[1][i] * t[i]; b += C[2][i] * t[i]; }
    }

    public static Surface Fit(Img img, Spec spec, Func<int, int, bool> use, int step) {
        var s = new Surface { Cx = (spec.X0 + spec.X1) / 2, Cy = (spec.Y0 + spec.Y1) / 2, S = Math.Max(spec.X1 - spec.X0, spec.Y1 - spec.Y0) / 2, Quadratic = spec.Quadratic };
        int n = spec.Quadratic ? 6 : 3;
        var ata = new double[n, n];
        var atb = new double[3, n];
        int count = 0;
        for (int y = (int)spec.Y0; y <= (int)spec.Y1; y += step)
            for (int x = (int)spec.X0; x <= (int)spec.X1; x += step) {
                if (x < 0 || y < 0 || x >= img.W || y >= img.H) continue;
                if (!spec.Inside(x + 0.5, y + 0.5) || !use(x, y)) continue;
                var t = s.Terms(x + 0.5, y + 0.5);
                int i0 = y * img.W + x;
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) ata[i, j] += t[i] * t[j];
                    atb[0, i] += t[i] * img.R[i0]; atb[1, i] += t[i] * img.G[i0]; atb[2, i] += t[i] * img.B[i0];
                }
                count++;
            }
        if (count < n * 4) throw new Exception(spec.Name + ": too few background pixels to fit (" + count + ")");
        for (int ch = 0; ch < 3; ch++) {
            var m = new double[n, n + 1];
            for (int i = 0; i < n; i++) { for (int j = 0; j < n; j++) m[i, j] = ata[i, j]; m[i, n] = atb[ch, i]; }
            for (int col = 0; col < n; col++) {
                int piv = col;
                for (int r = col + 1; r < n; r++) if (Math.Abs(m[r, col]) > Math.Abs(m[piv, col])) piv = r;
                for (int k = 0; k <= n; k++) { var tmp = m[col, k]; m[col, k] = m[piv, k]; m[piv, k] = tmp; }
                for (int r = 0; r < n; r++) {
                    if (r == col) continue;
                    double f = m[r, col] / m[col, col];
                    for (int k = col; k <= n; k++) m[r, k] -= f * m[col, k];
                }
            }
            s.C[ch] = new double[n];
            for (int i = 0; i < n; i++) s.C[ch][i] = m[i, n] / m[i, i];
        }
        return s;
    }

    public double Residual(Img img, int x, int y) {
        double r, g, b; Eval(x + 0.5, y + 0.5, out r, out g, out b);
        int i = y * img.W + x;
        return Math.Abs(img.R[i] - r) + Math.Abs(img.G[i] - g) + Math.Abs(img.B[i] - b);
    }
}

public class Result {
    public Spec Spec;
    public Img Img;
    public Surface Bg;
    public RectangleF Symbol;
    public double FitError;
    public double K; // canvas units per source pixel
    public double Scx, Scy; // source point at the canvas centre

    public override string ToString() {
        return string.Format("{0}: symbol=({1:0},{2:0})-({3:0},{4:0}) {5:0}x{6:0}, background error {7:0.0}, slack L{8:0} T{9:0} R{10:0} B{11:0}",
            Spec.Name, Symbol.Left, Symbol.Top, Symbol.Right, Symbol.Bottom, Symbol.Width, Symbol.Height, FitError,
            Symbol.Left - Spec.X0, Symbol.Top - Spec.Y0, Spec.X1 - Symbol.Right, Spec.Y1 - Symbol.Bottom);
    }
}

public static class IconKit {
    static readonly Dictionary<string, Img> loaded = new Dictionary<string, Img>();

    public static Img Get(string path) {
        Img img;
        if (!loaded.TryGetValue(path, out img)) { img = Img.Load(path); loaded[path] = img; }
        return img;
    }

    // The generator's sparkle is white laid over the picture at a varying strength. Its strength is measured where it
    // sits on a plain background (reference), then taken back out of every picture that has it in the same place.
    public static string Unmark(string referencePath, string[] targets, int cx, int cy, int half, int ring) {
        var refImg = Get(referencePath);
        int n = 2 * half + 1;
        var alpha = new double[n * n];
        // A plane per channel through the pixels around the mark.
        var fit = new double[3][];
        for (int ch = 0; ch < 3; ch++) {
            double s1 = 0, sx = 0, sy = 0, sxx = 0, sxy = 0, syy = 0, sv = 0, sxv = 0, syv = 0;
            for (int dy = -half; dy <= half; dy++) for (int dx = -half; dx <= half; dx++) {
                if (dx * dx + dy * dy < ring * ring) continue;
                int i = (cy + dy) * refImg.W + cx + dx;
                double v = ch == 0 ? refImg.R[i] : ch == 1 ? refImg.G[i] : refImg.B[i];
                s1++; sx += dx; sy += dy; sxx += dx * dx; sxy += dx * dy; syy += dy * dy; sv += v; sxv += dx * v; syv += dy * v;
            }
            // Solve the 3x3 normal equations.
            var m = new double[,] { { s1, sx, sy, sv }, { sx, sxx, sxy, sxv }, { sy, sxy, syy, syv } };
            for (int c = 0; c < 3; c++) for (int r = 0; r < 3; r++) {
                if (r == c) continue;
                double f = m[r, c] / m[c, c];
                for (int k = c; k < 4; k++) m[r, k] -= f * m[c, k];
            }
            fit[ch] = new[] { m[0, 3] / m[0, 0], m[1, 3] / m[1, 1], m[2, 3] / m[2, 2] };
        }
        double peak = 0;
        for (int dy = -half; dy <= half; dy++) for (int dx = -half; dx <= half; dx++) {
            int i = (cy + dy) * refImg.W + cx + dx;
            double a = 0;
            for (int ch = 0; ch < 3; ch++) {
                double bg = fit[ch][0] + fit[ch][1] * dx + fit[ch][2] * dy;
                double v = ch == 0 ? refImg.R[i] : ch == 1 ? refImg.G[i] : refImg.B[i];
                a += (v - bg) / Math.Max(1, 255 - bg);
            }
            a /= 3;
            if (a < 0.015) a = 0;
            alpha[(dy + half) * n + dx + half] = Math.Min(0.95, a);
            peak = Math.Max(peak, a);
        }
        foreach (var path in targets) {
            var img = Get(path);
            for (int dy = -half; dy <= half; dy++) for (int dx = -half; dx <= half; dx++) {
                double a = alpha[(dy + half) * n + dx + half];
                if (a <= 0) continue;
                int i = (cy + dy) * img.W + cx + dx;
                img.R[i] = (float)Math.Max(0, Math.Min(255, (img.R[i] - a * 255) / (1 - a)));
                img.G[i] = (float)Math.Max(0, Math.Min(255, (img.G[i] - a * 255) / (1 - a)));
                img.B[i] = (float)Math.Max(0, Math.Min(255, (img.B[i] - a * 255) / (1 - a)));
            }
        }
        return "mark peak strength " + peak.ToString("0.00");
    }

    public static Bitmap Crop(string path, int x, int y, int w, int h, int scale) {
        var img = Get(path);
        var bmp = new Bitmap(w * scale, h * scale, PixelFormat.Format32bppArgb);
        for (int j = 0; j < h * scale; j++) for (int i = 0; i < w * scale; i++) {
            int k = (y + j / scale) * img.W + x + i / scale;
            bmp.SetPixel(i, j, Color.FromArgb((int)img.R[k], (int)img.G[k], (int)img.B[k]));
        }
        return bmp;
    }

    public static Result Analyze(Spec spec) {
        var img = Get(spec.Source);
        // Where the background can be seen: outside the symbol and its shadow.
        Func<int, int, bool> open = (x, y) => true;
        if (spec.SymbolBox.HasValue) {
            var sb = spec.SymbolBox.Value; double m = spec.Margin;
            open = (x, y) => x < sb.Left - m || x > sb.Right + m || y < sb.Top - m || y > sb.Bottom + m;
        }
        // 1. A first fit on the band just inside the region's edge, which is background in every one of these designs.
        var bg = Surface.Fit(img, spec, (x, y) => spec.DepthInside(x + 0.5, y + 0.5) < 16 && open(x, y), 1);
        // 2. Refit on everything that looks like background, a few times.
        for (int it = 0; it < 5; it++) {
            var prev = bg;
            bg = Surface.Fit(img, spec, (x, y) => open(x, y) && prev.Residual(img, x, y) < 30, 1);
        }
        // 3. The symbol: where most of a 9x9 neighbourhood stands out from the background.
        RectangleF box;
        if (spec.SymbolBox.HasValue) box = spec.SymbolBox.Value;
        else {
            int w = img.W, h = img.H;
            var hit = new int[w * h];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
                if (spec.Inside(x + 0.5, y + 0.5) && spec.DepthInside(x + 0.5, y + 0.5) > 3 && bg.Residual(img, x, y) > spec.SymbolThreshold) hit[y * w + x] = 1;
            // Summed-area table for the neighbourhood count.
            var sat = new int[(w + 1) * (h + 1)];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
                sat[(y + 1) * (w + 1) + x + 1] = hit[y * w + x] + sat[y * (w + 1) + x + 1] + sat[(y + 1) * (w + 1) + x] - sat[y * (w + 1) + x];
            int x0 = w, y0 = h, x1 = -1, y1 = -1;
            const int rad = 4;
            for (int y = rad; y < h - rad; y++) for (int x = rad; x < w - rad; x++) {
                if (hit[y * w + x] == 0) continue;
                int a = sat[(y - rad) * (w + 1) + x - rad], b = sat[(y - rad) * (w + 1) + x + rad + 1];
                int c = sat[(y + rad + 1) * (w + 1) + x - rad], d = sat[(y + rad + 1) * (w + 1) + x + rad + 1];
                if (d - b - c + a < 41) continue;
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y;
            }
            box = new RectangleF(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
        }
        // How well the background fits away from the symbol.
        double err = 0; int n = 0;
        for (int y = (int)spec.Y0; y < (int)spec.Y1; y += 2) for (int x = (int)spec.X0; x < (int)spec.X1; x += 2) {
            if (!spec.Inside(x + 0.5, y + 0.5)) continue;
            if (x > box.Left - spec.Margin - spec.Feather && x < box.Right + spec.Margin + spec.Feather && y > box.Top - spec.Margin - spec.Feather && y < box.Bottom + spec.Margin + spec.Feather) continue;
            err += bg.Residual(img, x, y); n++;
        }
        var res = new Result { Spec = spec, Img = img, Bg = bg, Symbol = box, FitError = n > 0 ? err / n / 3 : -1 };
        double half = Math.Sqrt(box.Width * box.Width + box.Height * box.Height) / 2;
        res.K = spec.Reach / half;
        res.Scx = box.Left + box.Width / 2.0;
        res.Scy = box.Top + box.Height / 2.0;
        return res;
    }

    static double Smooth(double e0, double e1, double x) {
        double t = Math.Max(0, Math.Min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    // How much of the picture is kept at a source point: all of it around the symbol, fading out beyond its margin,
    // and none of what lies outside the region (frames, the grid's gutters).
    static double Keep(Result r, double x, double y) {
        var s = r.Spec; var b = r.Symbol;
        double ex0 = b.Left - s.Margin, ey0 = b.Top - s.Margin, ex1 = b.Right + s.Margin, ey1 = b.Bottom + s.Margin;
        double rad = Math.Min(b.Width, b.Height) * 0.2;
        double cx = Math.Max(ex0 + rad, Math.Min(x, ex1 - rad)), cy = Math.Max(ey0 + rad, Math.Min(y, ey1 - rad));
        double dx = x - cx, dy = y - cy;
        double outside = Math.Sqrt(dx * dx + dy * dy) - rad;
        double keep = 1 - Smooth(0, s.Feather, outside);
        double depth = s.DepthInside(x, y);
        return keep * Smooth(0, 3, depth);
    }

    static void SourceOf(Result r, int size, double px, double py, out double x, out double y) {
        double u = px * 108.0 / size - 54, v = py * 108.0 / size - 54;
        x = r.Scx + u / r.K; y = r.Scy + v / r.K;
    }

    public static Bitmap Foreground(Result r, int size) {
        var bmp = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        var px = new int[size * size];
        double srcPerPx = 108.0 / size / r.K;
        int ss = (int)Math.Max(2, Math.Min(6, Math.Ceiling(srcPerPx * 1.5)));
        for (int j = 0; j < size; j++) for (int i = 0; i < size; i++) {
            double sa = 0, sr = 0, sg = 0, sb = 0;
            for (int b = 0; b < ss; b++) for (int a = 0; a < ss; a++) {
                double x, y; SourceOf(r, size, i + (a + 0.5) / ss, j + (b + 0.5) / ss, out x, out y);
                double keep = Keep(r, x, y);
                if (keep <= 0) continue;
                double cr, cg, cb; r.Img.Sample(x, y, out cr, out cg, out cb);
                sa += keep; sr += keep * cr; sg += keep * cg; sb += keep * cb;
            }
            int n = ss * ss;
            int alpha = (int)Math.Round(sa / n * 255);
            if (alpha <= 0) { px[j * size + i] = 0; continue; }
            int R = Clamp(sr / sa), G = Clamp(sg / sa), B = Clamp(sb / sa);
            px[j * size + i] = (Math.Min(255, alpha) << 24) | (R << 16) | (G << 8) | B;
        }
        var data = bmp.LockBits(new Rectangle(0, 0, size, size), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
        Marshal.Copy(px, 0, data.Scan0, px.Length);
        bmp.UnlockBits(data);
        return bmp;
    }

    public static Bitmap Background(Result r, int size) {
        var bmp = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        var px = new int[size * size];
        var s = r.Spec;
        for (int j = 0; j < size; j++) for (int i = 0; i < size; i++) {
            double x, y; SourceOf(r, size, i + 0.5, j + 0.5, out x, out y);
            // Beyond the region the surface is not extrapolated: it keeps the colour at the region's edge.
            x = Math.Max(s.X0, Math.Min(s.X1, x)); y = Math.Max(s.Y0, Math.Min(s.Y1, y));
            double cr, cg, cb; r.Bg.Eval(x, y, out cr, out cg, out cb);
            px[j * size + i] = (255 << 24) | (Clamp(cr) << 16) | (Clamp(cg) << 8) | Clamp(cb);
        }
        var data = bmp.LockBits(new Rectangle(0, 0, size, size), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
        Marshal.Copy(px, 0, data.Scan0, px.Length);
        bmp.UnlockBits(data);
        return bmp;
    }

    static int Clamp(double v) { return (int)Math.Max(0, Math.Min(255, Math.Round(v))); }

    public static void SaveOpaque(Bitmap bmp, string path) {
        using (var rgb = new Bitmap(bmp.Width, bmp.Height, PixelFormat.Format24bppRgb)) {
            using (var g = Graphics.FromImage(rgb)) g.DrawImageUnscaled(bmp, 0, 0);
            Directory.CreateDirectory(Path.GetDirectoryName(path));
            rgb.Save(path, ImageFormat.Png);
        }
    }

    public static void Save(Bitmap bmp, string path) {
        Directory.CreateDirectory(Path.GetDirectoryName(path));
        bmp.Save(path, ImageFormat.Png);
    }

    // The icon as a launcher shows it: both layers, the 72-unit viewport, cut to a circle (round) or a squircle.
    public static Bitmap Masked(Bitmap bg, Bitmap fg, int outSize, bool round) {
        int size = bg.Width;
        var full = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(full)) { g.DrawImageUnscaled(bg, 0, 0); g.DrawImageUnscaled(fg, 0, 0); }
        var outBmp = new Bitmap(outSize, outSize, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(outBmp)) {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            using (var path = new GraphicsPath()) {
                if (round) path.AddEllipse(0, 0, outSize, outSize);
                else {
                    float rr = outSize * 0.3f;
                    path.AddArc(0, 0, rr * 2, rr * 2, 180, 90); path.AddArc(outSize - rr * 2, 0, rr * 2, rr * 2, 270, 90);
                    path.AddArc(outSize - rr * 2, outSize - rr * 2, rr * 2, rr * 2, 0, 90); path.AddArc(0, outSize - rr * 2, rr * 2, rr * 2, 90, 90);
                    path.CloseFigure();
                }
                g.SetClip(path);
                float v = size * 18f / 108f;
                g.DrawImage(full, new RectangleF(0, 0, outSize, outSize), new RectangleF(v, v, size - 2 * v, size - 2 * v), GraphicsUnit.Pixel);
            }
        }
        full.Dispose();
        return outBmp;
    }
}
