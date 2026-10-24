"""Draw the original Simple Trade Reroll icon with Pillow."""

from pathlib import Path

from PIL import Image, ImageDraw


SCALE = 4
SIZE = 256


def point(xy):
    return tuple(round(value * SCALE) for value in xy)


def polygon(draw, vertices, fill):
    draw.polygon([point(vertex) for vertex in vertices], fill=fill)


image = Image.new("RGBA", (SIZE * SCALE, SIZE * SCALE), (0, 0, 0, 0))
draw = ImageDraw.Draw(image)
draw.rounded_rectangle((*point((7, 7)), *point((249, 249))), radius=34 * SCALE,
                       fill="#16252a", outline="#081417", width=6 * SCALE)
draw.rounded_rectangle((*point((17, 17)), *point((239, 239))), radius=26 * SCALE,
                       outline="#38565c", width=3 * SCALE)

# Two arrows suggest searching through offers without copying game artwork.
draw.arc((*point((34, 34)), *point((222, 222))), 190, 300,
         fill="#f2bb55", width=12 * SCALE)
polygon(draw, [(181, 25), (216, 29), (198, 58)], "#f2bb55")
draw.arc((*point((34, 34)), *point((222, 222))), 10, 120,
         fill="#f2bb55", width=12 * SCALE)
polygon(draw, [(75, 231), (40, 227), (58, 198)], "#f2bb55")

# Faceted gem, drawn from original polygons.
polygon(draw, [(128, 46), (170, 80), (170, 164), (128, 206),
               (86, 164), (86, 80)], "#051e21")
polygon(draw, [(128, 54), (162, 83), (162, 160), (128, 196),
               (94, 160), (94, 83)], "#13a577")
polygon(draw, [(128, 54), (162, 83), (145, 99), (111, 99), (94, 83)], "#9bf3b3")
polygon(draw, [(94, 83), (111, 99), (111, 152), (94, 160)], "#4dde91")
polygon(draw, [(162, 83), (145, 99), (145, 152), (162, 160)], "#147d6c")
polygon(draw, [(111, 99), (145, 99), (145, 152), (128, 182),
               (111, 152)], "#35c790")
polygon(draw, [(94, 160), (111, 152), (128, 182), (128, 196)], "#1c966c")
polygon(draw, [(162, 160), (145, 152), (128, 182), (128, 196)], "#096a61")

destination = Path(__file__).resolve().parents[1] / "src/main/resources/assets/simple_trade_reroll/icon.png"
destination.parent.mkdir(parents=True, exist_ok=True)
image.resize((SIZE, SIZE), Image.Resampling.LANCZOS).save(destination)
print(destination)
