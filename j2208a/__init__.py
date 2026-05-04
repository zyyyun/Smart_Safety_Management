"""J2208A health bracelet pipeline — S1 Decode -> S2 Validate -> S3 Aggregate
-> wear-state state machine -> S4 Derive -> Supabase writer.

Per CONTEXT.md D-04: pure-Python computation modules. NO BLE I/O at module level
(BLE wiring lives in scripts/j2208a_sensor_reader.py). NO Supabase HTTP I/O at
module-load time (lazy import inside supabase_writer).
"""
