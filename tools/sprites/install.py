"""Copies the cleaned sprites in art/pixel/native into the app as lossless WebP (res/drawable-nodpi/px_*.webp).
Run from the repo root: python3 tools/sprites/install.py"""
from PIL import Image
import glob, os
SRC = 'art/pixel/native'
DST = 'app/src/main/res/drawable-nodpi'
PREFIX = {'poses': 'px_pose', 'expressions': 'px_expr', 'outfits': 'px_outfit', 'props': 'px_prop',
          'emotes': 'px_emote', 'icons': 'px_icon', 'misc': 'px_misc', 'portrait': 'px_portrait', 'banner': 'px_banner'}
SKIP = {'outfits_21'}  # not used in the app
for old in glob.glob(f'{DST}/px_*.webp'):
    os.remove(old)
n = 0
for f in sorted(glob.glob(f'{SRC}/*.png')):
    base = os.path.basename(f)[:-4]
    if base in SKIP:
        continue
    sec, idx = base.rsplit('_', 1)
    name = PREFIX[sec] if sec in ('portrait', 'banner') else f'{PREFIX[sec]}_{idx}'
    Image.open(f).save(f'{DST}/{name}.webp', lossless=True, quality=100, method=6)
    n += 1
print('installed', n)
