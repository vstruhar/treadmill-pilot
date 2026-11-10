# Control the belt through the FTMS control point

The app changes belt speed through the Bluetooth Fitness Machine control point (`0x2AD9`), and only with Request Control (`0x00`), Set Target Speed (`0x02`), Start or Resume (`0x07`), and Stop or Pause (`0x08`). Live speed and distance still come from Treadmill Data (`0x2ACD`).

FitShow's private framed protocol was the other way to drive this treadmill, but those opcodes were never recovered. The Ring treadmill exposes the standard control point, which is a published specification, so that is the command path.

The control point has no watchdog. If the phone dies mid-run, the belt does not stop by itself. The treadmill's own stop key and safety clip stay the physical fail-safe.
