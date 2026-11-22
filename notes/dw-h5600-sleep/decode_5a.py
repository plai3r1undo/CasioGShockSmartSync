import struct,sys
def rec(r):
    if r[5]==0xfe: return f"20{r[0]:02x}-{r[1]:02x}-{r[2]:02x}  (empty)"
    d=r[5:]
    f=lambda o: struct.unpack("<f",d[o:o+4])[0]
    u16=lambda o: struct.unpack("<H",d[o:o+2])[0]
    hm=lambda o: f"{d[o]}:{d[o+1]:02d}"
    return (f"20{r[0]:02x}-{r[1]:02x}-{r[2]:02x} sleepCharge={d[25]} ANS?={f(3):+.3f} f31?={f(31):.2f} "
            f"HR={d[7]}(base {d[8]}) RR={u16(9)}ms(base {u16(11)}) HRV={u16(13)}ms(base {u16(15)}) "
            f"breath={f(17):.2f}(base {f(21):.2f}) span={hm(27)} sleep={hm(29)} REM={hm(37)} deep={hm(39)} light={hm(41)} "
            f"bed={hm(43)} wake={hm(45)} unk o0={d[0]} o1={u16(1)} o26={d[26]} o35={d[35]} o36={d[36]} o47-49={d[47:50].hex()}")
for path in sys.argv[1:]:
    b=open(path,"rb").read(); n=struct.unpack("<I",b[1:5])[0]
    print("#",path.split('/')[-1])
    for i in range(5,n,55): print("  ",rec(b[i:i+55]))
