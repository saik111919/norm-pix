"""
Draws the clock widget's face as Android vector drawables (`res/drawable/widget_clock_*.xml`).

The widget is the platform's AnalogClock: it turns the "hands" itself - the minute ring once an hour, the seconds ring once a
minute (Android 12 and later) - so NormPix never wakes to redraw it. Each ring is a hand: a full-face drawable whose numerals are
laid out counter-clockwise from three o'clock, so turning it clockwise by the time brings the current minute (or second) to
the pill at three o'clock. The numerals are the app's own font (GS Flex), drawn as paths, top outward like a bezel's.

    pip install fonttools
    python3 clock_face.py            # writes into ../../app/src/main/res/drawable

All sizes are in the drawable's 200-unit viewport; `WidgetClockFace` in the app places the hour and minute text from the same
numbers, so keep the two in step.
"""

import math
import os
import sys

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, "..", "..", "app", "src", "main", "res"))
FONT = os.path.join(RES, "font", "gs_flex.ttf")

SIZE_DP = 200  # the face at its largest; AnalogClock scales it down to fit, never up
C = 100.0  # centre of the 200-unit viewport

# Rings: where the numerals sit (radius of their centres), how tall a digit is, and the tick band.
MINUTES = dict(numeral_r=53.0, digit_h=8.0, tick_in=61.5, tick_in_major=60.0, tick_out=67.5)
SECONDS = dict(numeral_r=80.0, digit_h=8.6, tick_in=91.0, tick_in_major=88.5, tick_out=96.5)

# The pill at three o'clock that the current minute shows in (drawn over the rings by its own drawable).
PILL_LEFT, PILL_RIGHT, PILL_HALF_H = 135.0, 171.0, 12.5
# The marker opposite it, between the two rings.
DOT_R, DOT_SIZE = 72.5, 2.6


def font():
    f = TTFont(FONT)
    return instantiateVariableFont(f, {"wght": 520, "wdth": 100, "ROND": 0})


class Glyphs:
    def __init__(self, f):
        self.f = f
        self.cmap = f.getBestCmap()
        self.gs = f.getGlyphSet()
        self.hmtx = f["hmtx"]
        # A digit's height: the top of "0" (digits sit on the baseline).
        from fontTools.pens.boundsPen import BoundsPen
        pen = BoundsPen(self.gs)
        self.gs[self.cmap[ord("0")]].draw(pen)
        self.digit_h = pen.bounds[3]

    def advance(self, ch):
        return self.hmtx[self.cmap[ord(ch)]][0]

    def path(self, text, cx, cy, height, angle_deg):
        """[text] centred on (cx, cy), its digits [height] units tall, turned [angle_deg] clockwise."""
        s = height / self.digit_h
        width = sum(self.advance(ch) for ch in text)
        a = math.radians(angle_deg)
        cos, sin = math.cos(a), math.sin(a)
        out = []
        pen_x = 0.0
        for ch in text:
            tx = s * (pen_x - width / 2)
            ty = s * self.digit_h / 2
            # Font units are y-up: flip, centre, then turn and move into place.
            t = (s * cos, s * sin, s * sin, -s * cos, cos * tx - sin * ty + cx, sin * tx + cos * ty + cy)
            svg = SVGPathPen(self.gs, ntos=lambda v: ("%.2f" % v).rstrip("0").rstrip("."))
            self.gs[self.cmap[ord(ch)]].draw(TransformPen(svg, t))
            out.append(svg.getCommands())
            pen_x += self.advance(ch)
        return " ".join(out)


def polar(r, phi_deg):
    """A point [r] from the centre, [phi_deg] counter-clockwise from three o'clock (screen y points down)."""
    p = math.radians(phi_deg)
    return C + r * math.cos(p), C - r * math.sin(p)


def num(v):
    return ("%.2f" % v).rstrip("0").rstrip(".")


def ring(glyphs, spec, turned_by=0.0):
    """One ring: 60 ticks, every fifth longer, and a numeral for every fifth, value v at 6v degrees counter-clockwise."""
    minor, major, numerals = [], [], []
    for v in range(60):
        phi = 6 * v - turned_by
        inner = spec["tick_in_major"] if v % 5 == 0 else spec["tick_in"]
        x1, y1 = polar(inner, phi)
        x2, y2 = polar(spec["tick_out"], phi)
        (major if v % 5 == 0 else minor).append("M%s,%s L%s,%s" % (num(x1), num(y1), num(x2), num(y2)))
        if v % 5 == 0:
            x, y = polar(spec["numeral_r"], phi)
            numerals.append(glyphs.path("%02d" % v, x, y, spec["digit_h"], 90 - phi))
    return " ".join(minor), " ".join(major), " ".join(numerals)


def circle(cx, cy, r):
    return "M%s,%s A%s,%s 0 1,1 %s,%s A%s,%s 0 1,1 %s,%s Z" % (
        num(cx - r), num(cy), num(r), num(r), num(cx + r), num(cy), num(r), num(r), num(cx - r), num(cy))


def pill(left, right, half_h):
    r = half_h
    return "M%s,%s H%s A%s,%s 0 0,1 %s,%s H%s A%s,%s 0 0,1 %s,%s Z" % (
        num(left + r), num(C - r), num(right - r), num(r), num(r), num(right - r), num(C + r),
        num(left + r), num(r), num(r), num(left + r), num(C - r))


def vector(comment, paths):
    body = "\n".join(paths)
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- {comment}
     Drawn by design/clock-widget/clock_face.py: change it there, not here. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{SIZE_DP}dp"
    android:height="{SIZE_DP}dp"
    android:viewportWidth="200"
    android:viewportHeight="200">
{body}
</vector>
"""


def fill(d, color):
    return f'    <path\n        android:fillColor="{color}"\n        android:pathData="{d}" />'


def stroke(d, color, width):
    return (f'    <path\n        android:strokeColor="{color}"\n        android:strokeWidth="{width}"\n'
            f'        android:strokeLineCap="round"\n        android:pathData="{d}" />')


def ring_paths(glyphs, spec, turned_by=0.0):
    minor, major, numerals = ring(glyphs, spec, turned_by)
    return [
        stroke(minor, "@color/widget_clock_tick", "0.9"),
        stroke(major, "@color/widget_clock_ink", "1.4"),
        fill(numerals, "@color/widget_clock_ink"),
    ]


def write(name, text):
    path = os.path.join(RES, "drawable", name)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)
    print("wrote", os.path.relpath(path, RES), len(text), "bytes")


def main():
    glyphs = Glyphs(font())
    face = [
        fill(circle(C, C, 100), "@color/widget_clock_face"),
        fill(circle(*polar(DOT_R, 180), DOT_SIZE), "@color/widget_clock_accent"),
    ]
    write("widget_clock_dial.xml", vector(
        "The clock widget's face: the disc, and the marker at nine o'clock. It stays still; the rings turn over it.", face))
    write("widget_clock_minutes.xml", vector(
        "The clock widget's minute ring, the AnalogClock's minute hand: turned clockwise by the minute, the current one "
        "comes to the pill at three o'clock.", ring_paths(glyphs, MINUTES)))
    write("widget_clock_seconds.xml", vector(
        "The clock widget's seconds ring (Android 12 and later), the AnalogClock's second hand: the outer ring, turned "
        "clockwise by the second.", ring_paths(glyphs, SECONDS)))
    write("widget_clock_pill.xml", vector(
        "The pill at three o'clock over the minute ring, which the widget's minute text sits in. Filled with the face, so "
        "the ring passes under it.",
        [fill(pill(PILL_LEFT, PILL_RIGHT, PILL_HALF_H), "@color/widget_clock_face"),
         stroke(pill(PILL_LEFT, PILL_RIGHT, PILL_HALF_H), "@color/widget_clock_ink", "1.5")]))
    write("widget_clock_hand_none.xml", vector(
        "The clock widget's hour hand: none - the hour is the text in the middle.",
        [fill("M100,100 L100,100 Z", "#00000000")]))

    # The widget picker's picture: the face at 10:09:30, as it would be drawn.
    hour, minute, second = 10, 9, 30
    minutes = minute + second / 60
    preview = list(face)
    preview += ring_paths(glyphs, SECONDS, turned_by=6 * second)
    preview += ring_paths(glyphs, MINUTES, turned_by=6 * minutes)
    preview += [fill(pill(PILL_LEFT, PILL_RIGHT, PILL_HALF_H), "@color/widget_clock_face"),
                stroke(pill(PILL_LEFT, PILL_RIGHT, PILL_HALF_H), "@color/widget_clock_ink", "1.5")]
    preview.append(fill(glyphs.path("%02d" % hour, C, C, HOUR_DIGIT_H, 0), "@color/widget_clock_ink"))
    preview.append(fill(glyphs.path("%02d" % minute, MINUTE_X, C, MINUTE_DIGIT_H, 0), "@color/widget_clock_ink"))
    write("widget_clock_preview.xml", vector("The clock widget in the widget picker: its face at 10:09.", preview))
    print("digit height / em:", glyphs.digit_h / glyphs.f["head"].unitsPerEm)


# The texts the widget places over the face (see WidgetClockFace): digit heights and the minute's centre, in face units.
HOUR_DIGIT_H = 30.0
MINUTE_DIGIT_H = 11.0
MINUTE_X = (PILL_LEFT + PILL_RIGHT) / 2

if __name__ == "__main__":
    sys.exit(main())
