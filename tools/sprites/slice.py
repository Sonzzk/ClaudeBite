from PIL import Image, ImageDraw
import numpy as np, json
im=Image.open('sheet.png').convert('RGB')
a=np.array(im).astype(int)
bg=np.array([16,12,13])
mask=(np.abs(a-bg).max(axis=2)>28)

def runs(profile, thr, mingap):
    """return segments where profile>thr, merging gaps smaller than mingap"""
    on=profile>thr
    segs=[];start=None
    for i,v in enumerate(on):
        if v and start is None: start=i
        if not v and start is not None: segs.append([start,i]); start=None
    if start is not None: segs.append([start,len(on)])
    merged=[]
    for s in segs:
        if merged and s[0]-merged[-1][1]<mingap: merged[-1][1]=s[1]
        else: merged.append(s)
    return merged

def rcut(y0,y1,x0,x1,thr,depth=0,minsize=14):
    sub=mask[y0:y1,x0:x1]
    ys=np.where(sub.any(1))[0]; xs=np.where(sub.any(0))[0]
    if len(ys)==0: return []
    y0,y1,x0,x1=y0+ys[0],y0+ys[-1]+1,x0+xs[0],x0+xs[-1]+1
    sub=mask[y0:y1,x0:x1]
    if depth<8:
        r=runs(sub.sum(1),thr,4)
        if len(r)>1:
            return sum([rcut(y0+a,y0+b,x0,x1,thr,depth+1) for a,b in r],[])
        c=runs(sub.sum(0),thr,5)
        if len(c)>1:
            return sum([rcut(y0,y1,x0+a,x0+b,thr,depth+1) for a,b in c],[])
    if (y1-y0)<minsize and (x1-x0)<minsize: return []
    return [(y0,y1,x0,x1)]

def xycut(y0,y1,x0,x1,rthr=1,cthr=1,rgap=4,cgap=6,minsize=14):
    return rcut(y0,y1,x0,x1,rthr)
def _old(y0,y1,x0,x1,rthr=1,cthr=1,rgap=4,cgap=6,minsize=14):
    out=[]
    sub=mask[y0:y1,x0:x1]
    for ry in runs(sub.sum(1),rthr,rgap):
        band=sub[ry[0]:ry[1]]
        for cx in runs(band.sum(0),cthr,cgap):
            cell=band[:,cx[0]:cx[1]]
            ys=np.where(cell.any(1))[0]
            if len(ys)==0: continue
            by0=y0+ry[0]+ys[0]; by1=y0+ry[0]+ys[-1]+1
            bx0=x0+cx[0]; bx1=x0+cx[1]
            if (by1-by0)<minsize and (bx1-bx0)<minsize: continue
            out.append((by0,by1,bx0,bx1))
    return out

sections={
 'expressions':(38,280,325,1305,3,3),
 'poses':(300,590,318,1305,3,3),
 'outfits':(612,873,10,940,3,3),
 'props':(622,873,955,1305,1,1),
 'emotes':(903,1192,10,642,1,1),
 'misc':(905,1105,660,1075,2,2),
 'icons':(1106,1192,660,1075,1,1),
}
res={}
for k,(y0,y1,x0,x1,rt,ct) in sections.items():
    res[k]=xycut(y0,y1,x0,x1,rt,ct)
    print(k,len(res[k]))
res['portrait']=[(68,589,13,315)]
res['banner']=[(1036,1153,1099,1281)]
json.dump({k:[list(map(int,b)) for b in v] for k,v in res.items()},open('boxes.json','w'))
dbg=im.copy(); d=ImageDraw.Draw(dbg)
for k,v in res.items():
    for i,(y0,y1,x0,x1) in enumerate(v):
        d.rectangle([x0,y0,x1-1,y1-1],outline=(0,255,0)); d.text((x0+1,y0+1),str(i),fill=(0,255,255))
dbg.save('debug_boxes.png')
