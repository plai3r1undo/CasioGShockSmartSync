#!/usr/bin/env python3
"""Minimal btsnoop -> ATT dump. Usage: btsnoop_att.py <log> [--json out.json]"""
import struct, sys, json, datetime

EPOCH_DELTA = 0x00dcddb30f2f8000  # us between 0 AD and 1970

ATT_OPS = {0x12: "WRITE_REQ", 0x52: "WRITE_CMD", 0x1b: "NOTIFY", 0x1d: "INDICATE",
           0x0a: "READ_REQ", 0x0b: "READ_RSP", 0x13: "WRITE_RSP", 0x01: "ERROR",
           0x08: "READ_BY_TYPE_REQ", 0x09: "READ_BY_TYPE_RSP", 0x10: "READ_BY_GROUP_REQ",
           0x11: "READ_BY_GROUP_RSP", 0x04: "FIND_INFO_REQ", 0x05: "FIND_INFO_RSP",
           0x02: "MTU_REQ", 0x03: "MTU_RSP"}


def records(path):
    with open(path, "rb") as f:
        data = f.read()
    assert data[:8] == b"btsnoop\0", "not btsnoop"
    off = 16
    while off + 24 <= len(data):
        orig, incl, flags, drops, ts = struct.unpack(">IIIIq", data[off:off + 24])
        off += 24
        pkt = data[off:off + incl]
        off += incl
        t = datetime.datetime.fromtimestamp((ts - EPOCH_DELTA) / 1e6)
        yield t, flags, pkt


def att_packets(path):
    partial = {}  # (handle, dir) -> [expected_len, bytes]
    for t, flags, pkt in records(path):
        if not pkt or pkt[0] != 0x02:  # H4 ACL
            continue
        direction = "RX" if flags & 1 else "TX"
        hdr, = struct.unpack("<H", pkt[1:3])
        conn = hdr & 0x0fff
        pb = (hdr >> 12) & 0x3
        payload = pkt[5:]
        key = (conn, direction)
        if pb in (0, 2):  # start
            if len(payload) < 4:
                continue
            l2len, cid = struct.unpack("<HH", payload[:4])
            partial[key] = [l2len, cid, bytearray(payload[4:])]
        elif pb == 1 and key in partial:  # continuation
            partial[key][2] += payload
        else:
            continue
        l2len, cid, buf = partial[key]
        if len(buf) >= l2len:
            del partial[key]
            if cid == 4 and buf:
                yield t, direction, conn, bytes(buf[:l2len])


def main():
    path = sys.argv[1]
    out = []
    for t, d, conn, att in att_packets(path):
        op = att[0]
        name = ATT_OPS.get(op, hex(op))
        handle = struct.unpack("<H", att[1:3])[0] if op in (0x12, 0x52, 0x1b, 0x1d, 0x0a) and len(att) >= 3 else None
        value = att[3:] if handle is not None else att[1:]
        out.append({"t": t.isoformat(timespec="milliseconds"), "dir": d, "conn": conn, "op": name,
                    "handle": handle, "value": value.hex()})
    if "--json" in sys.argv:
        with open(sys.argv[sys.argv.index("--json") + 1], "w") as f:
            json.dump(out, f, indent=0)
    for r in out:
        h = f"0x{r['handle']:04x}" if r["handle"] is not None else "      "
        print(f"{r['t']} {r['dir']} c{r['conn']:03x} {r['op']:<17} {h} {r['value']}")


if __name__ == "__main__":
    main()
