const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const CRC_TABLE = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) {
      c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
    }
    table[n] = c;
  }
  return table;
})();

function crc32(buffer) {
  let c = 0xFFFFFFFF;
  for (let i = 0; i < buffer.length; i++) {
    c = CRC_TABLE[(c ^ buffer[i]) & 0xFF] ^ (c >>> 8);
  }
  return (c ^ 0xFFFFFFFF) >>> 0;
}

function chunk(type, data) {
  const typeBuffer = Buffer.from(type, 'ascii');
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length, 0);
  const crcInput = Buffer.concat([typeBuffer, data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(crcInput), 0);
  return Buffer.concat([length, typeBuffer, data, crc]);
}

function encodePng(width, height, pixels) {
  const signature = Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;
  const stride = width * 4;
  const raw = Buffer.alloc((stride + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (stride + 1)] = 0;
    pixels.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride);
  }
  const idat = zlib.deflateSync(raw, { level: 9 });
  return Buffer.concat([signature, chunk('IHDR', ihdr), chunk('IDAT', idat), chunk('IEND', Buffer.alloc(0))]);
}

function canvas(width, height, background = [0, 0, 0, 0]) {
  const pixels = Buffer.alloc(width * height * 4);
  for (let i = 0; i < width * height; i++) {
    pixels[i * 4] = background[0];
    pixels[i * 4 + 1] = background[1];
    pixels[i * 4 + 2] = background[2];
    pixels[i * 4 + 3] = background[3];
  }
  return {
    width,
    height,
    pixels,
    set(x, y, r, g, b, a = 255) {
      if (x < 0 || y < 0 || x >= width || y >= height) return;
      const i = (x + y * width) * 4;
      pixels[i] = r;
      pixels[i + 1] = g;
      pixels[i + 2] = b;
      pixels[i + 3] = a;
    },
    rect(x, y, w, h, r, g, b, a = 255) {
      for (let yy = y; yy < y + h; yy++) {
        for (let xx = x; xx < x + w; xx++) this.set(xx, yy, r, g, b, a);
      }
    }
  };
}

function makeWand() {
  const c = canvas(16, 16);
  for (let i = 0; i < 9; i++) {
    c.set(3 + i, 13 - i, 112, 74, 38);
    c.set(4 + i, 13 - i, 86, 55, 28);
  }
  for (let i = 2; i < 9; i += 3) {
    c.set(3 + i, 13 - i, 214, 174, 74);
    c.set(4 + i, 13 - i, 168, 128, 44);
  }
  c.rect(11, 3, 3, 3, 90, 232, 255);
  c.rect(12, 2, 1, 1, 210, 255, 255);
  c.set(11, 5, 40, 170, 210);
  c.set(13, 5, 40, 170, 210);
  c.set(12, 6, 40, 170, 210);
  return c;
}

function makePlanner() {
  const c = canvas(16, 16);
  c.rect(2, 1, 12, 14, 30, 54, 92);
  c.rect(3, 2, 10, 12, 44, 82, 134);
  for (let x = 4; x < 13; x += 2) c.rect(x, 3, 1, 10, 30, 58, 100);
  for (let y = 4; y < 13; y += 2) c.rect(4, y, 8, 1, 30, 58, 100);
  c.rect(5, 8, 6, 4, 226, 240, 250);
  c.rect(6, 6, 4, 2, 226, 240, 250);
  c.rect(7, 9, 2, 3, 44, 82, 134);
  c.rect(2, 1, 12, 1, 120, 190, 235);
  c.rect(2, 14, 12, 1, 18, 34, 62);
  return c;
}

function makeLogo() {
  const c = canvas(128, 128, [10, 18, 32, 255]);
  c.rect(2, 2, 124, 124, 18, 32, 54);
  c.rect(3, 3, 122, 1, 64, 200, 230);
  c.rect(3, 124, 122, 1, 64, 200, 230);
  c.rect(3, 3, 1, 122, 64, 200, 230);
  c.rect(124, 3, 1, 122, 64, 200, 230);
  const buildings = [
    [14, 72, 18, 40, [58, 74, 96]],
    [34, 56, 20, 56, [72, 90, 116]],
    [56, 38, 22, 74, [88, 108, 138]],
    [80, 64, 16, 48, [64, 82, 108]],
    [98, 48, 18, 64, [76, 96, 124]]
  ];
  for (const [x, y, w, h, color] of buildings) {
    c.rect(x, y, w, h, color[0], color[1], color[2]);
    c.rect(x, y, w, 2, 110, 140, 170);
    for (let wy = y + 6; wy < y + h - 4; wy += 8) {
      for (let wx = x + 3; wx < x + w - 3; wx += 6) {
        c.rect(wx, wy, 2, 3, 245, 210, 110);
      }
    }
  }
  c.rect(0, 112, 128, 16, 26, 42, 62);
  c.rect(12, 112, 104, 2, 64, 200, 230);
  return c;
}

const root = path.resolve(__dirname, '..');
const itemDir = path.join(root, 'src/main/resources/assets/freebuilding/textures/item');
fs.mkdirSync(itemDir, { recursive: true });
fs.writeFileSync(path.join(itemDir, 'city_wand.png'), encodePng(16, 16, makeWand().pixels));
fs.writeFileSync(path.join(itemDir, 'city_planner.png'), encodePng(16, 16, makePlanner().pixels));
fs.writeFileSync(path.join(root, 'src/main/resources/freebuilding.png'), encodePng(128, 128, makeLogo().pixels));
console.log('Generated textures and logo.');
