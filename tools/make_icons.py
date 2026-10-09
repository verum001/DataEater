#!/usr/bin/env python3
"""Turn a square logo into the full set of Android launcher icons.

Android needs more than one file for an icon:

  * an adaptive icon  (background colour + foreground artwork)
  * a square icon for older launchers
  * a circular icon for launchers that ask for one
  * all of that at five screen densities

This script produces them all from one source image.

HOW THE ADAPTIVE ICON IS SIZED
    An Android adaptive icon is a 108 x 108 dp canvas, but the launcher may
    crop away anything outside the middle 66 x 66 dp. Artwork that fills the
    whole canvas would therefore lose its edges.

    So the image is scaled to a share of the canvas chosen by the caller and
    centred on a background colour sampled from the image itself. Because
    the background matches the artwork, the join is invisible.
"""

import os
import sys

from PIL import Image, ImageDraw

RES = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "app", "src", "main", "res")

# 108 dp canvas, one image per screen density.
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

# Legacy launcher icon is 48 dp.
LEGACY_DP = 48


def corner_colour(image):
    """Take the background colour from the image's own corners."""
    width, height = image.size
    samples = [
        image.getpixel((4, 4)),
        image.getpixel((width - 5, 4)),
        image.getpixel((4, height - 5)),
        image.getpixel((width - 5, height - 5)),
    ]
    # Use the most common sample so one stray pixel cannot change the colour.
    return max(set(samples), key=samples.count)


def colour_to_xml(colour, name="ic_launcher_background"):
    red, green, blue = colour[:3]
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- Solid background sampled from the corners of the logo image. -->\n'
        '<resources>\n'
        '    <color name="%s">#%02X%02X%02X</color>\n'
        '</resources>\n' % (name, red, green, blue)
    )


def artwork_bounds(image, background, tolerance=60):
    """Find how much of the width the drawing actually uses.

    Knowing this lets us scale the image so the *drawing*, not the empty
    margin, lands inside Android's safe zone.
    """
    width, height = image.size
    pixels = image.load()
    left, right = width, 0

    step = max(1, width // 300)
    for y in range(0, height, step):
        for x in range(0, width, step):
            r, g, b = pixels[x, y][:3]
            if abs(r - background[0]) + abs(g - background[1]) + abs(b - background[2]) > tolerance:
                if x < left:
                    left = x
                if x > right:
                    right = x

    if right <= left:
        return 1.0
    return (right - left) / float(width)


def main():
    if len(sys.argv) < 2:
        print("usage: make_icons.py <logo.png> [safe_zone_share_of_canvas]")
        print("  safe_zone_share: how much of the 108 dp canvas the image may fill.")
        print("                  Default 0.694 (image at 75 of 108 dp).")
        return 1

    source_path = sys.argv[1]
    share = float(sys.argv[2]) if len(sys.argv) > 2 else 0.694

    if not os.path.exists(source_path):
        print("No such file: %s" % source_path)
        return 1

    source = Image.open(source_path).convert("RGBA")
    background = corner_colour(source)
    fraction = artwork_bounds(source, background)

    print("logo           : %s" % os.path.basename(source_path))
    print("size           : %dx%d" % source.size)
    print("background     : #%02X%02X%02X" % background[:3])
    print("artwork uses   : %.1f%% of the image width" % (fraction * 100))
    print("canvas share   : %.1f%% (image at %.0f of 108 dp)" % (share * 100, share * 108))
    print()

    # --- adaptive foreground -------------------------------------------
    for density, factor in DENSITIES.items():
        folder = os.path.join(RES, "mipmap-%s" % density)
        os.makedirs(folder, exist_ok=True)

        canvas_px = int(round(108 * factor))
        canvas = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
        art_px = int(round(canvas_px * share))
        resized = source.resize((art_px, art_px), Image.LANCZOS)
        offset = (canvas_px - art_px) // 2
        canvas.paste(resized, (offset, offset), resized)
        canvas.save(os.path.join(folder, "ic_launcher_foreground.png"))

    # --- square and round legacy icons --------------------------------
    for density, factor in DENSITIES.items():
        folder = os.path.join(RES, "mipmap-%s" % density)
        size = int(round(LEGACY_DP * factor))

        square = source.resize((size, size), Image.LANCZOS).convert("RGB")
        square.save(os.path.join(folder, "ic_launcher.png"))

        mask = Image.new("L", (size * 4, size * 4), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
        mask = mask.resize((size, size), Image.LANCZOS)

        rounded = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        rounded.paste(square, (0, 0), mask)
        rounded.save(os.path.join(folder, "ic_launcher_round.png"))

    # --- background colour --------------------------------------------
    values = os.path.join(RES, "values")
    os.makedirs(values, exist_ok=True)
    with open(os.path.join(values, "ic_launcher_background.xml"), "w") as handle:
        handle.write(colour_to_xml(background))

    print("written: 5 densities x (foreground, square, round) + background colour")
    return 0


if __name__ == "__main__":
    sys.exit(main())