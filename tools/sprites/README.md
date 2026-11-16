# Sprite tools

Turns `art/pixel/claude-chan-sheet.png` into separate, cleaned sprites in `art/pixel/native/`.

Run from a folder containing `sheet.png` (a copy of the sheet):

```
pip install pillow numpy scipy opencv-python-headless
python3 slice.py    # finds every sprite, writes boxes.json + debug_boxes.png
python3 clean.py    # cuts out each sprite, removes the dark background, snaps colours to one 40-colour palette
python3 contact.py 'art/native/poses_*.png' preview.png 3   # preview grid
```

Sprites are kept at their native pixel size. Show them in the app with `FilterQuality.None` so they scale up crisp.
