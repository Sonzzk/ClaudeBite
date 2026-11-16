from PIL import Image, ImageDraw
import glob,sys
pat=sys.argv[1]; out=sys.argv[2]; scale=int(sys.argv[3]) if len(sys.argv)>3 else 2
files=sorted(glob.glob(pat))
cell=0; ims=[Image.open(f) for f in files]
W=1400; x=y=0; rowh=0; pos=[]
for im in ims:
    w,h=im.width*scale,im.height*scale
    if x+w>W: x=0;y+=rowh+14;rowh=0
    pos.append((x,y)); x+=w+6; rowh=max(rowh,h)
H=y+rowh+14
sheet=Image.new('RGBA',(W,H),(255,240,228,255))  # light bg reveals cutout errors
d=ImageDraw.Draw(sheet)
for f,im,(x,y) in zip(files,ims,pos):
    sheet.alpha_composite(im.resize((im.width*scale,im.height*scale),Image.NEAREST),(x,y+12))
    d.text((x,y),f.split('/')[-1][:-4].split('_',1)[1] if '_' in f else '',fill=(120,80,60))
sheet.save(out)
