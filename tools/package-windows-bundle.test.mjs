import assert from 'node:assert/strict';
import test from 'node:test';
import { bootstrapSource } from './package-windows-bundle.mjs';

test('single-file Windows bundles extract per build outside the working directory', () => {
  const source = bootstrapSource('BTL16-Auction-Server', 'abcdef0123456789');
  assert.ok(source.includes('SpecialFolder.LocalApplicationData'));
  assert.ok(source.includes('AppName + "-" + Version'));
  assert.ok(source.includes('destination.StartsWith(root, StringComparison.OrdinalIgnoreCase)'));
  assert.ok(source.includes('GetManifestResourceStream("auction-payload.zip")'));
  assert.ok(source.includes('mutex.ReleaseMutex()'));
  assert.ok(source.includes('UseShellExecute = true'));
  assert.ok(!source.includes('Directory.Delete'));
});

test('invalid identities cannot be injected into bootstrap source', () => {
  assert.throws(() => bootstrapSource('../Server', 'abcdef0123456789'));
  assert.throws(() => bootstrapSource('BTL16-Auction-Client', 'not-a-build-hash'));
});
