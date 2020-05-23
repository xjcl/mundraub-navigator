
import os
import glob
import shutil
import PIL.Image
import PIL.ImageOps

shutil.rmtree('fruit_markers_resized')
os.mkdir('fruit_markers_resized')

for fn in glob.glob('fruit_markers_drawable/*.png'):
    image = PIL.Image.open(fn).convert('RGBA')

    # add 1px border so antialiasing looks good near the border
    image = PIL.ImageOps.expand(image, border=1)
    image.thumbnail((40, 40), PIL.Image.ANTIALIAS)

    fn_new = fn.replace('drawable', 'resized')
    print(fn, '->', fn_new)
    image.save(fn_new)
