
import os
import glob
import shutil
import PIL.Image
import PIL.ImageOps

__dir__ = os.path.dirname(os.path.abspath(__file__))
src_dir = os.path.dirname(__dir__) + '/images/fruit_markers_vector_2021'
os.makedirs(src_dir.replace('2021', '2021_resized'), exist_ok=True)
os.makedirs(src_dir.replace('2021', '2021_framed'), exist_ok=True)
print('src_dir=', src_dir)


# *** make resized images ***
for fn in glob.glob(src_dir + '/*.png'):
    image = PIL.Image.open(fn).convert('RGBA')

    # add 1px border so antialiasing looks good near the border
    image = PIL.ImageOps.expand(image, border=1)
    image = image.resize((100, 160), PIL.Image.ANTIALIAS)

    fn_new = fn.replace('2021/', '2021_resized/icon_')
    print(fn, '->', fn_new)
    image.save(fn_new)


# *** make photo placeholders ***
for fn in glob.glob(src_dir + '/*.png'):
    image = PIL.Image.open(fn).convert('RGBA')

    try:
        col = image.getpixel((50, 500))
    except:
        image = image.resize((662, 1056))
        col = image.getpixel((50, 500))

    print(col)

    border = PIL.Image.open(fn).convert('RGBA')
    border = border.resize((int(image.width * 1.2), int(image.height * 1.2)))  # not good

    out = PIL.Image.new("RGBA", (2500, 1400), (col[0], col[1], col[2], 100))
    # out.paste(border, box=(1250 - border.width//2, 700 - border.height//2), mask=border)
    out.paste(image, box=(1250 - image.width//2, 700 - image.height//2), mask=image)

    fn_new = fn.replace('2021/', '2021_framed/frame_')
    print(fn, '->', fn_new)
    out.save(fn_new)
