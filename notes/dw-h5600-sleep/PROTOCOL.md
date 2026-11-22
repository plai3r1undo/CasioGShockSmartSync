# DW-H5600 sleep / Nightly Recharge protocol (decoded 2026-11-22 from btsnoop of CASIO WATCHES)

## Transport
- Handles (this phone): 0x0010 = control (WRITE|NOTIFY, likely 26eb0023), 0x0013 = data (WRITE_NO_RSP|NOTIFY, likely 26eb0024),
  0x0011/0x0014 = their CCCDs. Normal Casio cmds still on 0x0016 (write) / 0x0018 (notify).
- Phone enables CCCDs, writes ctrl `00 2e ff ff ff ff ff` = "send all files of category 0x2e".
  Handshake on data: `000000` (repeat until watch answers `000000`), `04 00..`, `04 01 18 00 18 00 00 00 dc 05`, `06 00..` -> echo watch's `06 ...` back.
- Per file, watch notifies ctrl `00 2e <id> <len u24 LE> 00`; phone echoes it to accept.
  Data arrives as `05 <payload>` notifications; payload = blocks of 512 bytes, each followed by 2-byte CRC.
  Watch sends ctrl `07 2e <id> <len>`; phone echoes, then writes data `07 00...`.
  End of all files: ctrl `04 2e ff ff ff ff ff`, then data `04 00..`, `04 01 30 01 40 01 04 00 dc 05`.
- **All file bytes are bit-inverted (XOR 0xFF).**

## Files seen (category 0x2e)
00 (20B, header w/ timestamp), 65 (99B), 50, 51, 52, 53, 56, 59, 5a, 5b, 63, 72. Only new data is sent on later syncs.

## File 0x5a = Nightly Recharge (one 55-byte record per night)
Header: `5a <total len u32 LE>`; record: `YY MM DD` (BCD, night start date) `04 08` then 50 bytes d[]:
| d off | type | meaning | 2026-11-21 example |
|---|---|---|---|
| 0 | u8 | ? (status/category) | 4 |
| 1 | u16 | ? | 812 |
| 3 | f32 | **ANS charge** (-10..+10) | +0.386 (app: +0.3) |
| 7 / 8 | u8 | avg HR / baseline HR (bpm) | 55 / 57 |
| 9 / 11 | u16 | avg RR / baseline RR (ms) | 1084 / 1048 |
| 13 / 15 | u16 | HRV / baseline (ms) | 70 / 74 |
| 17 / 21 | f32 | breathing rate / baseline | 13.93 / 13.85 |
| 25 | u8 | **Sleep charge** | 67 |
| 26 | u8 | ? | 3 |
| 27-28 | h,m | sleep span | 8:27 |
| 29-30 | h,m | actual sleep | 7:34 (interruptions = span - sleep) |
| 31 | f32 | ? | 1.90 |
| 35 / 36 | u8 | ? | 33 / 5 |
| 37-38 | h,m | REM | 1:48 |
| 39-40 | h,m | Deep | 1:44 |
| 41-42 | h,m | Light | 4:02 |
| 43-44 | h,m | bedtime, **UTC** | 22:49 (app: 23:49 CET) |
| 45-46 | h,m | wake, **UTC** | 7:16 (app: 8:16 CET) |
| 47-49 | ? | | ff 23 ff |
Empty night: d[0..] = fe/ff filler.

## File 0x53 = Hypnogram (sleep stages timeline)
Header `53 <len u32>` `YY MM DD` (BCD) + 5 bytes (`22 00 03 04 08`, unknown), then from offset 13
8-byte entries `<u32 seconds since sleep start> <i32 state>` (state change events), terminated by zeros, file padded with 0x00/0xff.
States = Polar PbSleepWakeState: -2 WAKE, -3 REM, -5 LIGHT (N1+N2), -6 DEEP (N3). Last event (WAKE) time = sleep span.
Verified 2026-11-21 night: WAKE 0:53, LIGHT 4:02, DEEP 1:44, REM 1:48 — exact match with app; graph shape matches screenshot.
Time 0 = bedtime from 0x5a record.

## Block CRC
CRC-16/KERMIT (poly 0x1021 reflected = 0x8408, init 0, no xorout), over the on-wire (still inverted) block bytes, stored LE.

## Open questions
- Date stamps: 2026-11-21 night has 0x5a date 21 and 0x53 date 21, but the older nights from the first pairing
  are 0x5a 19/18 vs 0x53 18/17 (same nights, matched by span + stage totals). Library pairs by span; start time
  currently = 0x5a date (+1 day if UTC bedtime < 12:00). Needs one check against the app history.
- Unknown 0x5a fields: d0, d1-2, d26, d31 (float 1.9/3.5/3.7), d35, d36, d47-49.
- Files 0x50/0x51/0x52/0x56/0x72/0x65/0x00 not decoded (likely HR / activity / HRV logs). Library returns them raw.

## Implementation
GShockAPI branch `dw-h5600-sleep` (../GShockAPI): io/LifeLogTransferIO.kt, io/SleepIOFunctional.kt, io/SleepIO.kt,
model/SleepData.kt, `api.getSleepData()`. Tests replay the captured session (api/src/test/resources/dwh5600).
App builds against it with `-PlocalGShockApi=true`.

## Daytime activity (decoded 2026-11-22, verified against the watch: 1801 steps, ~1593 kcal, 0:50 active)

Sent in every 0x2E transfer, each record only once (like nights).

- **0x00 status (20 B):** `00 0f 00 00 00 | YY MM DD HH MM (binary, UTC) | u16 ? | u16 0 | u32 today's steps | u16 today's kcal (incl. resting)`.
  Offset 10 (u16) unknown, not monotonic (1290, 860, 373, 327, 470, 953, 460).
- **0x50:** one record per completed 5-min interval since the previous transfer: `<steps u16 LE> 04 08`.
  The last interval ends at the status time rounded down to 5 min. Contiguous across transfers; sums equal the growth of the daily total.
- **0x65 daily summary (99 B):** byte 39 = active hours, byte 40 = active minutes (29→50 over the afternoon). The rest is unknown.
- **0x51:** energy for the same 5-min intervals, `<u16 LE> 04 08`, unit 1/128 kcal (990 = 7.7 kcal at rest).
  Verified: the sum over 12:55-15:20 UTC was 284 kcal, against 280 of growth in the watch total (fit: 128.6 units/kcal).
- **0x56 (tentative):** one 3-byte record per minute, `<flag> 04 08`. 0x20 = rest, 0x60 = moderate, 0x80 = brisk walking, 0x10 = ? (seen once).
  The 0x80 minutes fall in the slots with the most steps, but sometimes about 5 min late, so the alignment isn't exact.
- **0x72 (not decoded):** one 32-byte record per 5-min interval, newest first:
  `<BCD YY MM DD HH MM = interval END, UTC> 04 08 <25 bytes>`.
  - Bytes 7-9 are signed values near ±64 (0x40 ≈ 1 g?), so they look like mean wrist orientation per axis.
  - Bytes 10-12 are small (spread?), bytes 13-15 look like per-axis maxima, then `00×4 fe`.
  - Then a u16 (30-460), one byte (0-30) and one byte (46-127, 127 when still), then `00 00 0x 00 ff 0c 0x`.
  - Probably the motion summary behind the sleep/wear detection. Needs a reference to verify.
- **0x52:** one 4-byte record per 2 minutes, always FEFE (empty) during the day so far. Likely heart rate, recorded only at night or during workouts.
- **0x00 offset 10 (u16):** grows with the time since the previous transfer (113 after 1 min, 953 after 61 min). Probably the amount of data pending.
