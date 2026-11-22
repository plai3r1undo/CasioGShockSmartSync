#!/usr/bin/env python3
"""Reassemble DW-H5600 file transfers from att.json.
Control handle 0x0010 (RX notify): 00 <cat> <id> <len LE24> 00  -> start
                                   07 <cat> ...                  -> end
Data handle 0x0013 (RX notify): 05 <payload...>; per 512-byte block a 2-byte CRC trails.
"""
import json, sys, os

CTRL, DATA = 0x0010, 0x0013


def main(att_json, outdir):
    rows = json.load(open(att_json))
    os.makedirs(outdir, exist_ok=True)
    cur = None
    files = []
    for r in rows:
        if r["dir"] != "RX" or r["op"] != "NOTIFY":
            continue
        v = bytes.fromhex(r["value"])
        if r["handle"] == CTRL and len(v) >= 7 and v[0] == 0x00 and v[1] in (0x2e,) and v[2:6] != b"\xff\xff\xff\xff":
            cur = {"t": r["t"], "conn": r["conn"], "cat": v[1], "id": v[2],
                   "len": int.from_bytes(v[3:6], "little"), "raw": bytearray()}
        elif r["handle"] == CTRL and v[:1] == b"\x07" and cur:
            files.append(cur); cur = None
        elif r["handle"] == DATA and v[:1] == b"\x05" and cur is not None:
            cur["raw"] += v[1:]
    for f in files:
        raw = bytes(f["raw"])
        # strip 2-byte CRC after each 512-byte block (and after the final partial block)
        out = bytearray(); i = 0
        while i < len(raw):
            take = min(512, f["len"] - len(out))
            out += raw[i:i + take]; i += take + 2
            if len(out) >= f["len"]:
                break
        inv = bytes(b ^ 0xFF for b in out)
        name = f"{f['t'][:19].replace(':','')}_c{f['conn']:03x}_{f['cat']:02x}_{f['id']:02x}.bin"
        open(os.path.join(outdir, name), "wb").write(inv)
        print(f"{name}  len={f['len']} got={len(out)} rawlen={len(raw)}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
