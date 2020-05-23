
import re
import os

with open('fruit_marker_images_location.html') as f:
    # https://stackoverflow.com/questions/766372/python-non-greedy-regexes
    ls = re.findall('"https://mundraub.org.*?png"', f.read())
    print(len(ls))
    os.system('wget ' + ' '.join(ls))











