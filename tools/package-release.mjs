import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { projectRoot, runCommand } from './dev-utils.mjs';
import { sha256, sourceFingerprint } from './distribution-metadata.mjs';
import { mainJar } from './package-layout.mjs';

if (process.platform !== 'win32') throw new Error('dist:all builds Windows EXEs and both Mac portable architectures on Windows. On macOS use dist and dist:mac:portable.');
if (process.argv.length > 2) throw new Error('Usage: npm run dist:all');
const fingerprint = sourceFingerprint();
await runCommand(process.execPath, ['tools/package-desktop.mjs'], 'Windows Client and Server');
await runCommand(process.execPath, ['tools/package-mac-portable.mjs'], 'macOS Client and Server, Apple Silicon and Intel');
if (sourceFingerprint() !== fingerprint) throw new Error('Source changed during packaging; rebuild instead of mixing versions.');
await runCommand(process.execPath, ['tools/verify-distributions.mjs'], 'Verify all release payloads');
const artifacts = readdirSync(path.join(projectRoot, 'dist')).filter(file => /^BTL16-Auction-(Client|Server)(?:\.exe|-macOS-(arm64|x64)-portable\.tar\.gz)$/.test(file)).sort();
writeFileSync(path.join(projectRoot, 'dist', 'BUILD_INFO.json'), JSON.stringify({
  schemaVersion: 1, builtAt: new Date().toISOString(), sourceSha256: fingerprint,
  jar: mainJar, jarSha256: sha256(readFileSync(path.join(projectRoot, 'target', mainJar))),
  artifacts: artifacts.map(file => ({ file, sha256: sha256(readFileSync(path.join(projectRoot, 'dist', file))) }))
}, null, 2) + '\n');
console.log(`[DIST] All ${artifacts.length} release files verified. Build identity: dist/BUILD_INFO.json`);
