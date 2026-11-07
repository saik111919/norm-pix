"""
Writes the launch screen's animated icon: the cookie's intro as an animated vector drawable that Android plays itself.

    python LaunchSplashAvd.py            (from this folder; stdlib only)

Output, in native/app/src/main/res/drawable/:
    avd_splash.xml   the intro: the cookie spins in, its lobes grow out of a circle, the app's mark springs up inside it
    splash_icon.xml  the last frame of it (the cookie settled), which the tests compare with the Compose cookie

Why it is generated and not typed: the numbers below are CookieMotion's (navigation/CookieSplash.kt) and have to stay the same,
or the cookie Android draws would not be the one Compose takes over from. Every curve is sampled from the same springs and
easings, and LaunchSplashAvdTest reads the written file and checks it against CookieMotion, so a change to either one fails there.

Why Android plays it: a cold start has nothing of the app to draw with for the first second or more (the process, the activity and
the first composition do not exist yet). The launch screen is drawn by the system from the first moment, so an intro that lives
there is on screen at once and cannot stutter behind a busy main thread. (The idea is DropSauce's; this is drawn from scratch.)

The icon is 288 x 288 dp and Android shows a circle of 192 dp of it, so the cookie - at most about 1.17 of its radius while it
overshoots - has to stay inside that: radius 80 dp.
"""
import math
import os
import re

# ---- CookieMotion's numbers (keep in step with navigation/CookieSplash.kt) -------------------------------------------------
LOBES = 9
WAVE = 0.0615
POINTS = LOBES * 24
INTRO_MS = 600
SPIN_DEGREES = 150.0
LOGO_FROM = 0.6
LOGO_OF_RADIUS = 2.6
RADIUS_DP = 80.0
ENTER = (0.05, 0.7, 0.1, 1.0)  # AppMotion.enter, a cubic-bezier

CANVAS = 288.0  # dp: the size Android gives an icon without a background
CENTRE = CANVAS / 2.0
SAMPLE_MS = 10
# How long every animator waits before it starts (CookieMotion.LAUNCH_DELAY_MS). Android starts the icon the moment it makes the launch
# screen, but the window is not on screen until the launcher's opening animation has run (~100 ms and more on a phone seen), so an
# intro that began at once was already a hundred milliseconds in when the person first saw it: a disc that popped into a cookie.
START_DELAY_MS = 100


def spring(t, damping, omega):
    if t <= 0.0:
        return 0.0
    if t >= 1.0:
        return 1.0
    wd = omega * math.sqrt(1.0 - damping * damping)
    decay = math.exp(-damping * omega * t)
    return 1.0 - decay * (math.cos(wd * t) + damping / math.sqrt(1.0 - damping * damping) * math.sin(wd * t))


def bezier(x1, y1, x2, y2, x):
    """y of a CSS-style cubic-bezier easing at x (bisection on the parameter, as Compose does)."""
    if x <= 0.0:
        return 0.0
    if x >= 1.0:
        return 1.0
    lo, hi = 0.0, 1.0
    for _ in range(60):
        mid = (lo + hi) / 2.0
        bx = 3 * (1 - mid) ** 2 * mid * x1 + 3 * (1 - mid) * mid ** 2 * x2 + mid ** 3
        if bx < x:
            lo = mid
        else:
            hi = mid
    t = (lo + hi) / 2.0
    return 3 * (1 - t) ** 2 * t * y1 + 3 * (1 - t) * t ** 2 * y2 + t ** 3


def clamp01(v):
    return max(0.0, min(1.0, v))


# Each curve is the share of the way from the animator's valueFrom to its valueTo, at t = 0..1 of the intro.
def scale_curve(t):
    return spring(t, 0.6, 15.1)


def rotation_curve(t):
    return bezier(*ENTER, clamp01(t / 0.65))  # rotation = -150 + 150 * this


def lobes_curve(t):
    return spring((t - 0.075) / 0.925, 0.7, 11.8)


def logo_scale_curve(t):
    return spring((t - 0.15) / 0.65, 0.6, 15.1)


def logo_alpha_curve(t):
    return clamp01((t - 0.15) / 0.15)


def fmt(v):
    s = f"{v:.2f}"
    s = s.rstrip("0").rstrip(".") if "." in s else s
    return "0" if s in ("-0", "") else s


def cookie_path(amplitude):
    """CookieMotion.cookie, unrotated, at the icon's centre: 216 points on the lobed circle, smoothed through their midpoints."""
    xs, ys = [], []
    for i in range(POINTS):
        a = 2.0 * math.pi * i / POINTS
        r = RADIUS_DP + amplitude * math.cos(LOBES * a)
        xs.append(CENTRE + r * math.cos(a))
        ys.append(CENTRE + r * math.sin(a))
    n = POINTS
    out = [f"M{fmt((xs[n - 1] + xs[0]) / 2)},{fmt((ys[n - 1] + ys[0]) / 2)}"]
    for i in range(n):
        j = (i + 1) % n
        out.append(f"Q{fmt(xs[i])},{fmt(ys[i])} {fmt((xs[i] + xs[j]) / 2)},{fmt((ys[i] + ys[j]) / 2)}")
    out.append("Z")
    return "".join(out)


def interpolator(curve):
    pts = []
    steps = INTRO_MS // SAMPLE_MS
    for k in range(steps + 1):
        t = k / steps
        pts.append(("M" if k == 0 else "L") + f"{t:.4f},{curve(t):.4f}")
    return " ".join(pts)


def animator(prop, frm, to, curve, value_type="floatType"):
    return (
        f'        <objectAnimator\n'
        f'            android:duration="{INTRO_MS}"\n'
        f'            android:startOffset="{START_DELAY_MS}"\n'
        f'            android:propertyName="{prop}"\n'
        f'            android:valueFrom="{frm}"\n'
        f'            android:valueTo="{to}"\n'
        f'            android:valueType="{value_type}">\n'
        f'            <aapt:attr name="android:interpolator">\n'
        f'                <pathInterpolator android:pathData="{interpolator(curve)}" />\n'
        f'            </aapt:attr>\n'
        f'        </objectAnimator>\n'
    )


def mark_xml(res_dir, alpha):
    """The app's monochrome mark, from ic_launcher_monochrome.xml, drawn in the cookie's ink and with its paths named for animating."""
    src = open(os.path.join(res_dir, "drawable", "ic_launcher_monochrome.xml"), encoding="utf-8").read()
    body = src[src.index(">", src.index("<vector")) + 1: src.rindex("</vector>")]
    body = re.sub(r"<!--.*?-->", "", body, flags=re.S)
    count = [0]

    def name_path(match):
        count[0] += 1
        ind = match.group(1)
        return (f'{ind}android:name="mark{count[0]}"\n{ind}android:fillAlpha="{alpha}"\n'
                f'{ind}android:fillColor="@color/splash_cookie_ink"')

    body = re.sub(r'([ \t]*)android:fillColor="#FFFFFFFF"', name_path, body)
    lines = [ln for ln in body.replace("\r", "").split("\n") if ln.strip()]
    return "\n".join("        " + ln for ln in lines), count[0]


def vector(res_dir, final):
    side = RADIUS_DP * LOGO_OF_RADIUS
    cookie = cookie_path(RADIUS_DP * WAVE * (1.0 if final else 0.0))
    mark, paths = mark_xml(res_dir, 1 if final else 0)
    logo_scale = 1 if final else LOGO_FROM
    cookie_scale = 1 if final else 0
    cookie_rotation = 0 if final else -SPIN_DEGREES
    return (
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="288dp"\n'
        '    android:height="288dp"\n'
        '    android:viewportWidth="288"\n'
        '    android:viewportHeight="288">\n'
        '    <group\n'
        '        android:name="cookie"\n'
        f'        android:pivotX="{fmt(CENTRE)}"\n'
        f'        android:pivotY="{fmt(CENTRE)}"\n'
        f'        android:rotation="{fmt(cookie_rotation)}"\n'
        f'        android:scaleX="{cookie_scale}"\n'
        f'        android:scaleY="{cookie_scale}">\n'
        '        <path\n'
        '            android:name="cookie_shape"\n'
        '            android:fillColor="@color/splash_cookie"\n'
        f'            android:pathData="{cookie}" />\n'
        '    </group>\n'
        '    <group\n'
        '        android:name="logo"\n'
        f'        android:pivotX="{fmt(CENTRE)}"\n'
        f'        android:pivotY="{fmt(CENTRE)}"\n'
        f'        android:scaleX="{logo_scale}"\n'
        f'        android:scaleY="{logo_scale}">\n'
        '        <group\n'
        f'            android:scaleX="{side / 108.0:.5f}"\n'
        f'            android:scaleY="{side / 108.0:.5f}"\n'
        f'            android:translateX="{fmt((CANVAS - side) / 2.0)}"\n'
        f'            android:translateY="{fmt((CANVAS - side) / 2.0)}">\n'
        f'{mark}\n'
        '        </group>\n'
        '    </group>\n'
        '</vector>\n'
    ), paths


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    res_dir = os.path.normpath(os.path.join(here, "..", "..", "app", "src", "main", "res"))
    banner = (
        "<!--\n"
        "  Generated by native/design/launch-splash/LaunchSplashAvd.py - do not edit by hand (edit the script, or CookieMotion, and run it).\n"
    )
    start, paths = vector(res_dir, final=False)
    end, _ = vector(res_dir, final=True)
    indented = "".join("    " + ln if ln.strip() else ln for ln in start.splitlines(True))

    animations = []
    animations.append('    <target android:name="cookie">\n        <aapt:attr name="android:animation">\n            <set>\n')
    animations.append(animator("scaleX", 0, 1, scale_curve))
    animations.append(animator("scaleY", 0, 1, scale_curve))
    animations.append(animator("rotation", fmt(-SPIN_DEGREES), 0, rotation_curve))
    animations.append('            </set>\n        </aapt:attr>\n    </target>\n')
    animations.append('    <target android:name="cookie_shape">\n        <aapt:attr name="android:animation">\n            <set>\n')
    animations.append(animator("pathData", cookie_path(0.0), cookie_path(RADIUS_DP * WAVE), lobes_curve, "pathType"))
    animations.append('            </set>\n        </aapt:attr>\n    </target>\n')
    animations.append('    <target android:name="logo">\n        <aapt:attr name="android:animation">\n            <set>\n')
    animations.append(animator("scaleX", LOGO_FROM, 1, logo_scale_curve))
    animations.append(animator("scaleY", LOGO_FROM, 1, logo_scale_curve))
    animations.append('            </set>\n        </aapt:attr>\n    </target>\n')
    for k in range(1, paths + 1):
        animations.append(f'    <target android:name="mark{k}">\n        <aapt:attr name="android:animation">\n            <set>\n')
        animations.append(animator("fillAlpha", 0, 1, logo_alpha_curve))
        animations.append('            </set>\n        </aapt:attr>\n    </target>\n')

    avd = (
        '<?xml version="1.0" encoding="utf-8"?>\n' + banner +
        "  The cookie's intro, played by Android itself as the launch screen's icon from the first moment of a cold start (12 and later);\n"
        "  Compose takes over from its last frame, splash_icon.xml, which is why every number here is CookieMotion's.\n"
        "-->\n"
        '<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    xmlns:aapt="http://schemas.android.com/aapt">\n'
        '    <aapt:attr name="android:drawable">\n'
        f'{indented}'
        '    </aapt:attr>\n'
        + "".join(animations) +
        '</animated-vector>\n'
    )
    icon = (
        '<?xml version="1.0" encoding="utf-8"?>\n' + banner +
        "  The last frame of avd_splash.xml: the cookie settled, with the mark in it. The tests draw it and compare it with the Compose cookie.\n"
        "-->\n" + end
    )
    for name, text in (("avd_splash.xml", avd), ("splash_icon.xml", icon)):
        with open(os.path.join(res_dir, "drawable", name), "w", encoding="utf-8", newline="\r\n") as out:
            out.write(text)
        print(f"wrote {name}: {len(text):,} chars")


if __name__ == "__main__":
    main()
