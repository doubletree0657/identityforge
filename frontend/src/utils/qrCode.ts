// Small, dependency-free QR encoder for TOTP provisioning URIs.
// It emits a fixed Version 8 / error-correction L symbol (up to 192 UTF-8 bytes).
const VERSION = 8;
const SIZE = VERSION * 4 + 17;
const DATA_CODEWORDS = 194;
const BLOCK_DATA_CODEWORDS = 97;
const BLOCK_TOTAL_CODEWORDS = 121;

export function drawQrCode(canvas: HTMLCanvasElement, value: string, scale = 4) {
  const modules = encodeQr(value);
  const quietZone = 4;
  canvas.width = (SIZE + quietZone * 2) * scale;
  canvas.height = canvas.width;
  const context = canvas.getContext('2d');
  if (!context) return;
  context.imageSmoothingEnabled = false;
  context.fillStyle = '#ffffff';
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.fillStyle = '#0f172a';
  modules.forEach((row, y) => row.forEach((dark, x) => {
    if (dark) context.fillRect((x + quietZone) * scale, (y + quietZone) * scale, scale, scale);
  }));
}

export function encodeQr(value: string): boolean[][] {
  const bytes = Array.from(new TextEncoder().encode(value));
  if (bytes.length > 192) throw new Error('TOTP provisioning URI is too long for the QR encoder');
  const data = makeDataCodewords(bytes);
  const codewords = addErrorCorrection(data);
  let best: boolean[][] | undefined;
  let bestPenalty = Number.POSITIVE_INFINITY;
  for (let mask = 0; mask < 8; mask += 1) {
    const modules = makeBaseMatrix();
    drawFormatBits(modules, mask);
    mapData(modules, codewords, mask);
    const penalty = penaltyScore(modules as boolean[][]);
    if (penalty < bestPenalty) {
      bestPenalty = penalty;
      best = modules as boolean[][];
    }
  }
  return best!;
}

function makeDataCodewords(bytes: number[]) {
  const bits: number[] = [];
  appendBits(bits, 0b0100, 4);
  appendBits(bits, bytes.length, 8);
  bytes.forEach((byte) => appendBits(bits, byte, 8));
  const capacity = DATA_CODEWORDS * 8;
  appendBits(bits, 0, Math.min(4, capacity - bits.length));
  while (bits.length % 8 !== 0) bits.push(0);
  const result: number[] = [];
  for (let index = 0; index < bits.length; index += 8) {
    result.push(bits.slice(index, index + 8).reduce((byte, bit) => (byte << 1) | bit, 0));
  }
  for (let pad = 0; result.length < DATA_CODEWORDS; pad += 1) result.push(pad % 2 === 0 ? 0xec : 0x11);
  return result;
}

function addErrorCorrection(data: number[]) {
  const blocks = [data.slice(0, BLOCK_DATA_CODEWORDS), data.slice(BLOCK_DATA_CODEWORDS)];
  const divisor = reedSolomonDivisor(BLOCK_TOTAL_CODEWORDS - BLOCK_DATA_CODEWORDS);
  const errorBlocks = blocks.map((block) => reedSolomonRemainder(block, divisor));
  const result: number[] = [];
  for (let index = 0; index < BLOCK_DATA_CODEWORDS; index += 1) blocks.forEach((block) => result.push(block[index]));
  for (let index = 0; index < divisor.length; index += 1) errorBlocks.forEach((block) => result.push(block[index]));
  return result;
}

function reedSolomonDivisor(degree: number) {
  const result = new Array<number>(degree).fill(0);
  result[degree - 1] = 1;
  let root = 1;
  for (let index = 0; index < degree; index += 1) {
    for (let coefficient = 0; coefficient < result.length; coefficient += 1) {
      result[coefficient] = gfMultiply(result[coefficient], root);
      if (coefficient + 1 < result.length) result[coefficient] ^= result[coefficient + 1];
    }
    root = gfMultiply(root, 0x02);
  }
  return result;
}

function reedSolomonRemainder(data: number[], divisor: number[]) {
  const result = new Array<number>(divisor.length).fill(0);
  data.forEach((byte) => {
    const factor = byte ^ result[0];
    result.shift();
    result.push(0);
    divisor.forEach((coefficient, index) => { result[index] ^= gfMultiply(coefficient, factor); });
  });
  return result;
}

function gfMultiply(left: number, right: number) {
  let product = 0;
  for (let bit = 7; bit >= 0; bit -= 1) {
    product = (product << 1) ^ ((product >>> 7) * 0x11d);
    product ^= ((right >>> bit) & 1) * left;
  }
  return product;
}

function makeBaseMatrix(): Array<Array<boolean | null>> {
  const modules = Array.from({ length: SIZE }, () => new Array<boolean | null>(SIZE).fill(null));
  drawFinder(modules, 0, 0);
  drawFinder(modules, SIZE - 7, 0);
  drawFinder(modules, 0, SIZE - 7);
  [6, 24, 42].forEach((row) => [6, 24, 42].forEach((column) => {
    if (modules[row][column] === null) drawAlignment(modules, row, column);
  }));
  for (let index = 8; index < SIZE - 8; index += 1) {
    if (modules[6][index] === null) modules[6][index] = index % 2 === 0;
    if (modules[index][6] === null) modules[index][6] = index % 2 === 0;
  }
  drawVersionBits(modules);
  return modules;
}

function drawFinder(modules: Array<Array<boolean | null>>, row: number, column: number) {
  for (let dy = -1; dy <= 7; dy += 1) for (let dx = -1; dx <= 7; dx += 1) {
    const y = row + dy;
    const x = column + dx;
    if (y < 0 || y >= SIZE || x < 0 || x >= SIZE) continue;
    modules[y][x] = dy >= 0 && dy <= 6 && dx >= 0 && dx <= 6
      && (dy === 0 || dy === 6 || dx === 0 || dx === 6 || (dy >= 2 && dy <= 4 && dx >= 2 && dx <= 4));
  }
}

function drawAlignment(modules: Array<Array<boolean | null>>, row: number, column: number) {
  for (let dy = -2; dy <= 2; dy += 1) for (let dx = -2; dx <= 2; dx += 1) {
    modules[row + dy][column + dx] = Math.max(Math.abs(dx), Math.abs(dy)) !== 1;
  }
}

function drawVersionBits(modules: Array<Array<boolean | null>>) {
  const bits = bchRemainder(VERSION << 12, 0x1f25) | (VERSION << 12);
  for (let index = 0; index < 18; index += 1) {
    const dark = ((bits >>> index) & 1) !== 0;
    modules[Math.floor(index / 3)][index % 3 + SIZE - 11] = dark;
    modules[index % 3 + SIZE - 11][Math.floor(index / 3)] = dark;
  }
}

function drawFormatBits(modules: Array<Array<boolean | null>>, mask: number) {
  const data = (1 << 3) | mask; // Error correction level L.
  const bits = ((data << 10) | bchRemainder(data << 10, 0x537)) ^ 0x5412;
  for (let index = 0; index < 15; index += 1) {
    const dark = ((bits >>> index) & 1) !== 0;
    const verticalRow = index < 6 ? index : index < 8 ? index + 1 : SIZE - 15 + index;
    modules[verticalRow][8] = dark;
    const horizontalColumn = index < 8 ? SIZE - index - 1 : index === 8 ? 7 : 14 - index;
    modules[8][horizontalColumn] = dark;
  }
  modules[SIZE - 8][8] = true;
}

function mapData(modules: Array<Array<boolean | null>>, codewords: number[], mask: number) {
  let bitIndex = 0;
  let upward = true;
  for (let right = SIZE - 1; right > 0; right -= 2) {
    if (right === 6) right -= 1;
    for (let vertical = 0; vertical < SIZE; vertical += 1) {
      const row = upward ? SIZE - 1 - vertical : vertical;
      for (let offset = 0; offset < 2; offset += 1) {
        const column = right - offset;
        if (modules[row][column] !== null) continue;
        const dataBit = bitIndex < codewords.length * 8
          && ((codewords[bitIndex >>> 3] >>> (7 - (bitIndex & 7))) & 1) !== 0;
        modules[row][column] = dataBit !== maskBit(mask, row, column);
        bitIndex += 1;
      }
    }
    upward = !upward;
  }
}

function maskBit(mask: number, row: number, column: number) {
  switch (mask) {
    case 0: return (row + column) % 2 === 0;
    case 1: return row % 2 === 0;
    case 2: return column % 3 === 0;
    case 3: return (row + column) % 3 === 0;
    case 4: return (Math.floor(row / 2) + Math.floor(column / 3)) % 2 === 0;
    case 5: return (row * column) % 2 + (row * column) % 3 === 0;
    case 6: return ((row * column) % 2 + (row * column) % 3) % 2 === 0;
    default: return ((row * column) % 3 + (row + column) % 2) % 2 === 0;
  }
}

function penaltyScore(modules: boolean[][]) {
  let score = 0;
  for (let row = 0; row < SIZE; row += 1) {
    for (let column = 0; column < SIZE; column += 1) {
      const dark = modules[row][column];
      let same = 0;
      for (let dy = -1; dy <= 1; dy += 1) for (let dx = -1; dx <= 1; dx += 1) {
        if ((dx === 0 && dy === 0) || row + dy < 0 || row + dy >= SIZE || column + dx < 0 || column + dx >= SIZE) continue;
        if (modules[row + dy][column + dx] === dark) same += 1;
      }
      if (same > 5) score += 3 + same - 5;
    }
  }
  for (let row = 0; row < SIZE - 1; row += 1) for (let column = 0; column < SIZE - 1; column += 1) {
    const value = modules[row][column];
    if (value === modules[row + 1][column] && value === modules[row][column + 1] && value === modules[row + 1][column + 1]) score += 3;
  }
  const pattern = [true, false, true, true, true, false, true];
  for (let index = 0; index < SIZE; index += 1) {
    for (let start = 0; start <= SIZE - 7; start += 1) {
      if (pattern.every((value, offset) => modules[index][start + offset] === value)) score += 40;
      if (pattern.every((value, offset) => modules[start + offset][index] === value)) score += 40;
    }
  }
  const darkCount = modules.flat().filter(Boolean).length;
  score += Math.floor(Math.abs(100 * darkCount / (SIZE * SIZE) - 50) / 5) * 10;
  return score;
}

function appendBits(target: number[], value: number, length: number) {
  for (let bit = length - 1; bit >= 0; bit -= 1) target.push((value >>> bit) & 1);
}

function bchRemainder(value: number, polynomial: number) {
  let result = value;
  while (bitLength(result) >= bitLength(polynomial)) result ^= polynomial << (bitLength(result) - bitLength(polynomial));
  return result;
}

function bitLength(value: number) {
  let length = 0;
  for (let current = value; current !== 0; current >>>= 1) length += 1;
  return length;
}
