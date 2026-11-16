from PIL import Image, ImageDraw
import numpy as np, json, os, cv2
from scipy import ndimage as nd
exec(open('slice.py').read().split("sections={")[0])  # mask, rcut
boxes=json.load(open('boxes.json'))
# split merged outfit boxes with a stricter threshold
fixed=[]
for b in boxes['outfits']:
    y0,y1,x0,x1=b
    if (y1-y0)<20: continue  # header underline
    if (x1-x0)>100 or (y1-y0)>150:
        parts=rcut(y0,y1,x0,x1,8)
        fixed+= [p for p in parts if (p[1]-p[0])>40]
    else: fixed.append(b)
boxes['outfits']=fixed
boxes['poses']=[b for b in boxes['poses'] if (b[1]-b[0])>30]
sheet=np.array(Image.open('sheet.png').convert('RGB')).astype(np.int32)
bg=np.array([16,12,13])

# global palette from the sheet's art pixels
art=sheet[mask].reshape(-1,3).astype(np.float32)
crit=(cv2.TERM_CRITERIA_EPS+cv2.TERM_CRITERIA_MAX_ITER,30,0.5)
np.random.seed(0)
sample=art[np.random.choice(len(art),min(len(art),120000),replace=False)]
_,_,centers=cv2.kmeans(sample,40,None,crit,4,cv2.KMEANS_PP_CENTERS)
pal=np.vstack([centers,[[16,12,13]]]).astype(np.float32)
json.dump(pal.astype(int).tolist(),open('palette.json','w'))

def quantize(rgb):
    flat=rgb.reshape(-1,3).astype(np.float32)
    d=((flat[:,None,:]-pal[None,:,:])**2).sum(2)
    return pal[d.argmin(1)].reshape(rgb.shape).astype(np.uint8)

def clean(y0,y1,x0,x1,pad=3):
    y0=max(0,y0-pad);x0=max(0,x0-pad);y1=min(sheet.shape[0],y1+pad);x1=min(sheet.shape[1],x1+pad)
    c=sheet[y0:y1,x0:x1]
    dist=np.abs(c-bg).max(2)
    soft=dist<=16   # background (noise stays under 16)
    # open the background mask first so it can't leak into dark clothes through 1-2 px gaps
    sp=np.pad(soft,3,constant_values=True)
    opened=nd.binary_opening(sp,iterations=2)
    lab,_=nd.label(opened)
    border=set(np.unique(np.concatenate([lab[0],lab[-1],lab[:,0],lab[:,-1]])))-{0}
    reach=np.isin(lab,list(border))
    transparent=(nd.binary_dilation(reach,iterations=2)&sp)[3:-3,3:-3]
    # enclosed pure-background holes (inside the halo, between arms): also transparent
    hl,hn=nd.label(soft&~transparent)
    if hn:
        areas=nd.sum(np.ones_like(dist),hl,range(1,hn+1)); means=nd.mean(dist,hl,range(1,hn+1))
        for i,(ar,mn) in enumerate(zip(areas,means)):
            if ar<12 or mn>14: continue
            comp=hl==i+1
            ring=nd.binary_dilation(comp,iterations=2)&~comp
            # a hole framed by bright art (halo, hair) is see-through; one framed by dark clothes is shirt
            ys,xs=np.where(comp); h=ys.max()-ys.min()+1; w=xs.max()-xs.min()+1
            halo_like = w>=2*h and ys.max()<0.25*comp.shape[0]
            big_gap = ar>=60 and mn<=6
            if (halo_like and dist[ring].mean()>50) or big_gap: transparent[comp]=True
    # keep thin dark outline pixels that touch art (dist>18) so edges stay crisp
    # drop tiny floating specks (noise), keep things >= 4 px
    opaque=~transparent
    l2,n2=nd.label(opaque)
    sizes=nd.sum(opaque,l2,range(1,n2+1))
    for i,s in enumerate(sizes):
        if s<4: opaque[l2==i+1]=False
    q=quantize(c)
    rgba=np.dstack([q,(opaque*255).astype(np.uint8)])
    # trim
    ys=np.where(opaque.any(1))[0]; xs=np.where(opaque.any(0))[0]
    rgba=rgba[ys[0]:ys[-1]+1, xs[0]:xs[-1]+1]
    return Image.fromarray(rgba,'RGBA')

os.makedirs('art/native',exist_ok=True); os.makedirs('art/x4',exist_ok=True)
names={}
for sec,lst in boxes.items():
    def rows(lst):
        lst=sorted(lst,key=lambda b:(b[0]+b[1])/2); out=[]; cur=[]
        for b in lst:
            if cur and (b[0]+b[1])/2-(cur[0][0]+cur[0][1])/2 > 0.5*(cur[0][1]-cur[0][0]): out.append(cur); cur=[]
            cur.append(b)
        if cur: out.append(cur)
        return [b for r in out for b in sorted(r,key=lambda b:b[2])]
    lst=rows(lst)
    for i,b in enumerate(lst):
        img=clean(*b)
        n=f"{sec}_{i:02d}"
        img.save(f'art/native/{n}.png')
        img.resize((img.width*4,img.height*4),Image.NEAREST).save(f'art/x4/{n}.png')
        names[n]=[int(v) for v in b]
json.dump(names,open('art/index.json','w'),indent=0)
print({k:len(v) for k,v in boxes.items()}, len(names))
