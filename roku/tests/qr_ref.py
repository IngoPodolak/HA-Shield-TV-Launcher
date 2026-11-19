import math
def gfmul(x,y):
    z=0
    for i in range(7,-1,-1):
        z=((z<<1)&0x1FF)^((z>>7)*0x11D)
        if (y>>i)&1: z^=x
    return z&0xFF
def divisor(d):
    res=[0]*(d-1)+[1]; root=1
    for i in range(d):
        for j in range(d):
            res[j]=gfmul(res[j],root)
            if j+1<d: res[j]^=res[j+1]
        root=gfmul(root,2)
    return res
def remainder(data,div):
    n=len(div); res=[0]*n
    for b in data:
        f=b^res[0]; res.pop(0); res.append(0)
        for i in range(n): res[i]^=gfmul(div[i],f)
    return res
T=[None,[10,1,16,0,0],[16,1,28,0,0],[26,1,44,0,0],[18,2,32,0,0],[24,2,43,0,0],[16,4,27,0,0],[18,4,31,0,0],[22,2,38,2,39],[22,3,36,2,37],[26,4,43,1,44]]
AL=[None,[],[6,18],[6,22],[6,26],[6,30],[6,34],[6,22,38],[6,24,42],[6,26,46],[6,28,50]]
def matrix(text):
    by=list(text.encode())
    ver=0
    for v in range(1,11):
        t=T[v]; dcw=t[1]*t[2]+t[3]*t[4]; cc=16 if v>=10 else 8
        if 4+cc+len(by)*8<=dcw*8: ver=v;break
    t=T[ver]; dcw=t[1]*t[2]+t[3]*t[4]; cc=16 if ver>=10 else 8
    bits=[]
    def add(v,c):
        for i in range(c-1,-1,-1): bits.append((v>>i)&1)
    add(4,4); add(len(by),cc)
    for b in by: add(b,8)
    cap=dcw*8; term=min(4,cap-len(bits)); bits+= [0]*term
    while len(bits)%8: bits.append(0)
    pad=0xEC
    while len(bits)<cap:
        add(pad,8); pad=0x11 if pad==0xEC else 0xEC
    data=[int(''.join(map(str,bits[i*8:i*8+8])),2) for i in range(dcw)]
    ec=t[0]; div=divisor(ec); blocks=[];eccs=[];k=0
    for g in range(2):
        for _ in range(t[1+g*2]):
            blk=data[k:k+t[2+g*2]]; k+=t[2+g*2]; blocks.append(blk); eccs.append(remainder(blk,div))
    final=[]
    for i in range(max(t[2],t[4])):
        for b in blocks:
            if i<len(b): final.append(b[i])
    for i in range(ec):
        for e in eccs: final.append(e[i])
    size=ver*4+17
    mods=[[False]*size for _ in range(size)]; fn=[[False]*size for _ in range(size)]
    def setf(x,y,d): mods[y][x]=d; fn[y][x]=True
    for i in range(size): setf(6,i,i%2==0); setf(i,6,i%2==0)
    def finder(cx,cy):
        for dy in range(-4,5):
            for dx in range(-4,5):
                d=max(abs(dx),abs(dy)); xx=cx+dx; yy=cy+dy
                if 0<=xx<size and 0<=yy<size: setf(xx,yy,d!=2 and d!=4)
    finder(3,3);finder(size-4,3);finder(3,size-4)
    al=AL[ver]; n=len(al)
    for i in range(n):
        for j in range(n):
            if (i==0 and j==0) or (i==0 and j==n-1) or (i==n-1 and j==0): continue
            for dy in range(-2,3):
                for dx in range(-2,3): setf(al[i]+dx,al[j]+dy,max(abs(dx),abs(dy))!=1)
    def fmt(mask):
        d=mask; r=d
        for i in range(10): r=(r<<1)^((r>>9)*0x537)
        b=((d<<10)|r)^0x5412
        for i in range(6): setf(8,i,(b>>i)&1==1)
        setf(8,7,(b>>6)&1==1); setf(8,8,(b>>7)&1==1); setf(7,8,(b>>8)&1==1)
        for i in range(9,15): setf(14-i,8,(b>>i)&1==1)
        for i in range(8): setf(size-1-i,8,(b>>i)&1==1)
        for i in range(8,15): setf(8,size-15+i,(b>>i)&1==1)
        setf(8,size-8,True)
    fmt(0)
    if ver>=7:
        r=ver
        for i in range(12): r=(r<<1)^((r>>11)*0x1F25)
        vb=(ver<<12)|r
        for i in range(18):
            bit=(vb>>i)&1==1; a=size-11+i%3; b=i//3; setf(a,b,bit); setf(b,a,bit)
    i=0; total=len(final)*8; right=size-1
    while right>=1:
        if right==6: right=5
        for vert in range(size):
            for j in range(2):
                x=right-j; up=((right+1)&2)==0; y=size-1-vert if up else vert
                if not fn[y][x] and i<total:
                    mods[y][x]=(final[i>>3]>>(7-(i&7)))&1==1; i+=1
        right-=2
    def mb(m,x,y):
        return [(x+y)%2==0,y%2==0,x%3==0,(x+y)%3==0,(x//3+y//2)%2==0,(x*y)%2+(x*y)%3==0,((x*y)%2+(x*y)%3)%2==0,((x+y)%2+(x*y)%3)%2==0][m]
    def apply(m):
        for y in range(size):
            for x in range(size):
                if not fn[y][x] and mb(m,x,y): mods[y][x]=not mods[y][x]
    def pen():
        m=mods; pts=0
        for r in range(size):
            sr=sc=0; lr=lc=None
            for c in range(size):
                v=m[r][c]
                if lr is not None and v==lr: sr+=1
                else:
                    if sr>=5: pts+=3+sr-5
                    lr=v; sr=1
                v=m[c][r]
                if lc is not None and v==lc: sc+=1
                else:
                    if sc>=5: pts+=3+sc-5
                    lc=v; sc=1
            if sr>=5: pts+=3+sr-5
            if sc>=5: pts+=3+sc-5
        p2=0
        for r in range(size-1):
            for c in range(size-1):
                s=m[r][c]+m[r][c+1]+m[r+1][c]+m[r+1][c+1]
                if s in (0,4): p2+=1
        pts+=p2*3; p3=0
        for r in range(size):
            br=bc=0
            for c in range(size):
                br=((br<<1)&0x7FF)|int(m[r][c])
                if c>=10 and br in (0x5D0,0x05D): p3+=1
                bc=((bc<<1)&0x7FF)|int(m[c][r])
                if c>=10 and bc in (0x5D0,0x05D): p3+=1
        pts+=p3*40
        dark=sum(sum(1 for v in row if v) for row in m)
        k=abs(math.ceil((dark*100/(size*size))/5)-10)
        return pts+k*10
    best=-1;bs=0
    for mk in range(8):
        apply(mk); fmt(mk); s=pen()
        if best<0 or s<bs: best=mk; bs=s
        apply(mk)
    apply(best); fmt(best)
    return mods
