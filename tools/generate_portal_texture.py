"""Generate a neutral animated portal sprite so dye tints retain their actual hues."""
from pathlib import Path
import math
import struct
import zlib

size, frames = 16, 32
pixels = bytearray()
for frame in range(frames):
    t = frame * math.tau / frames
    for y in range(size):
        pixels.append(0)  # PNG filter: none
        for x in range(size):
            u, v = x * math.tau / size, y * math.tau / size
            wave = math.sin(u + math.sin(v + t)) + math.cos(v - t + math.sin(u - t))
            value = round(190 + 28 * wave)
            pixels.extend((value, value, value, 175))

def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))

path = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/indev2/textures/block/dyed_portal.png'
path.parent.mkdir(parents=True, exist_ok=True)
path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size * frames, 8, 6, 0, 0, 0))
                + chunk(b'IDAT', zlib.compress(pixels)) + chunk(b'IEND', b''))
path.with_suffix('.png.mcmeta').write_text('{"animation":{"frametime":2,"interpolate":true}}\n')
