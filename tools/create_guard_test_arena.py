"""Write an original, minimal Minecraft structure for isolated guard GameTests."""

import gzip
import struct
from pathlib import Path


def utf(value):
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def named(kind, name, payload):
    return bytes([kind]) + utf(name) + payload


def int_list(values):
    return bytes([3]) + struct.pack(">i", len(values)) + b"".join(struct.pack(">i", value) for value in values)


def compound_list(values):
    return bytes([10]) + struct.pack(">i", len(values)) + b"".join(values)


def main():
    palette = [named(8, "Name", utf("minecraft:stone")) + b"\0"]
    blocks = [
        named(9, "pos", int_list([x, 0, z])) + named(3, "state", struct.pack(">i", 0)) + b"\0"
        for x in range(16) for z in range(16)
    ]
    data = named(10, "", b"".join([
        named(3, "DataVersion", struct.pack(">i", 3465)),
        named(9, "size", int_list([16, 5, 16])),
        named(9, "palette", compound_list(palette)),
        named(9, "blocks", compound_list(blocks)),
        named(9, "entities", compound_list([])),
        b"\0",
    ]))
    target = Path(__file__).resolve().parents[1] / (
        "src/gametest/resources/data/villagediplomacy/structures/guard_arena.nbt"
    )
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(gzip.compress(data, mtime=0))
    print(target)


if __name__ == "__main__":
    main()
