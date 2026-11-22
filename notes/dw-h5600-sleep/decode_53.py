import struct,sys
NAMES={-2:"WAKE",-3:"REM",-5:"LIGHT",-6:"DEEP"}
d=open(sys.argv[1],"rb").read()
n=struct.unpack("<I",d[1:5])[0]
print("header", d[5:13].hex())
ev=[]
for i in range(13,n-8+1,8):
    t,s=struct.unpack("<Ii",d[i:i+8])
    if s==0 and t==0 and ev: break
    ev.append((t,s))
tot={}
for (t,s),(t2,_) in zip(ev,ev[1:]):
    tot[s]=tot.get(s,0)+(t2-t)
print("events",len(ev),"last t",ev[-1], f"= {ev[-1][0]//3600}:{ev[-1][0]%3600//60:02d}")
for s,v in tot.items(): print(f"{NAMES.get(s,s):6} {v//3600}:{v%3600//60:02d} ({v}s)")
if "-v" in sys.argv:
    for t,s in ev: print(f"  +{t//3600}:{t%3600//60:02d}:{t%60:02d} {NAMES.get(s,s)}")
