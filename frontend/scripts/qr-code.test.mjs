import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { transformWithEsbuild } from 'vite';

const source = await readFile(new URL('../src/utils/qrCode.ts', import.meta.url), 'utf8');
const compiled = await transformWithEsbuild(source, 'qrCode.ts', { loader: 'ts', format: 'esm', target: 'es2022' });
const qr = await import(`data:text/javascript;base64,${Buffer.from(compiled.code).toString('base64')}`);

test('encodes a TOTP provisioning URI as a Version 8 QR matrix', () => {
  const uri = 'otpauth://totp/IdentityForge:development/admin?secret=ABCDEFGHIJKLMNOPQRSTUVWXYZ234567&issuer=IdentityForge&algorithm=SHA1&digits=6&period=30';
  const matrix = qr.encodeQr(uri);

  assert.equal(matrix.length, 49);
  matrix.forEach((row) => {
    assert.equal(row.length, 49);
    row.forEach((module) => assert.equal(typeof module, 'boolean'));
  });
  assertFinder(matrix, 0, 0);
  assertFinder(matrix, 42, 0);
  assertFinder(matrix, 0, 42);
  assert.equal(matrix[8][6], true, 'timing module must not be overwritten by format data');
  assert.equal(matrix[41][8], true, 'required fixed dark module');
});

test('payload changes data modules and the encoder enforces its byte capacity', () => {
  const first = qr.encodeQr('otpauth://totp/IdentityForge:a?secret=ABCDEFGHIJKLMNOPQRSTUVWXYZ234567&issuer=IdentityForge');
  const second = qr.encodeQr('otpauth://totp/IdentityForge:b?secret=ABCDEFGHIJKLMNOPQRSTUVWXYZ234567&issuer=IdentityForge');
  const differences = first.flat().filter((module, index) => module !== second.flat()[index]).length;

  assert.ok(differences > 100);
  assert.doesNotThrow(() => qr.encodeQr('x'.repeat(192)));
  assert.throws(() => qr.encodeQr('x'.repeat(193)), /too long/);
});

function assertFinder(matrix, row, column) {
  for (let y = 0; y < 7; y += 1) for (let x = 0; x < 7; x += 1) {
    const expected = y === 0 || y === 6 || x === 0 || x === 6 || (y >= 2 && y <= 4 && x >= 2 && x <= 4);
    assert.equal(matrix[row + y][column + x], expected);
  }
}
