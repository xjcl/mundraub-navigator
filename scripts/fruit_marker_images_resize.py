
import os
import glob
import shutil
import PIL.Image
import PIL.ImageOps

__dir__ = os.path.dirname(os.path.abspath(__file__))
src_dir = os.path.dirname(__dir__) + '/images/fruit_markers_vector_2021'
os.makedirs(src_dir.replace('2021', '2021_resized'), exist_ok=True)
print('src_dir=', src_dir)


# *** make resized images ***
for fn in glob.glob(src_dir + '/*.png'):
    image = PIL.Image.open(fn).convert('RGBA')

    # add 1px border so antialiasing looks good near the border
    image = PIL.ImageOps.expand(image, border=1)
    image = image.resize((100, 160), PIL.Image.ANTIALIAS)

    fn_new = fn.replace('2021', '2021_resized')
    print(fn, '->', fn_new)
    image.save(fn_new)


# # *** make photo placeholders ***
# for fn in glob.glob(src_dir + '/*.png'):
#     image = PIL.Image.open(fn).convert('RGBA')

#     # add 1px border so antialiasing looks good near the border
#     image = PIL.ImageOps.expand(image, border=1)
#     image = image.resize((100, 160), PIL.Image.ANTIALIAS)

#     fn_new = fn.replace('2021', '2021_resized')
#     print(fn, '->', fn_new)
#     image.save(fn_new)
