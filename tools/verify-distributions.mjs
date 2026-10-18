import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync, readdirSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { projectRoot } from './dev-utils.mjs';
import { mainJar, verifyJavafxLibraries } from './package-layout.mjs';

const hash = bytes => createHash('sha256').update(bytes).digest('hex');
const expected = hash(readFileSync(path.join(projectRoot, 'target', mainJar)));
function tar(args) {
  const result = spawnSync('tar', args, { cwd: projectRoot, windowsHide: true, maxBuffer: 64 << 20 });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, result.stderr?.toString());
  return result.stdout;
}
function unpackedFiles(folder) {
  return readdirSync(path.join(projectRoot, 'dist', folder), { withFileTypes: true }).flatMap(entry => {
    const file = `${folder}/${entry.name}`;
    if (entry.isDirectory()) return unpackedFiles(file);
    assert.ok(entry.isFile(), `unexpected link or special file: ${file}`);
    return [file];
  });
}
for (const architecture of ['arm64', 'x64']) {
  const checksumFile = readFileSync(path.join(projectRoot, 'dist', `SHA256SUMS-macOS-${architecture}.txt`), 'utf8');
  for (const role of ['Client', 'Server']) {
    const folder = `BTL16-Auction-${role}-macOS-${architecture}-portable`;
    const archive = path.join('dist', `${folder}.tar.gz`);
    const files = tar(['-tzf', archive]).toString('utf8').split(/\r?\n/).filter(Boolean);
    assert.ok(files.every(file => file.startsWith(`${folder}/`) && !file.split('/').includes('..')), 'archive paths stay within package');
    assert.ok(!files.some(file => /\.(exe|dll|mv\.db|trace\.db|lock\.db)$|\/media\/(products|staging)\//i.test(file)), 'no Windows executables or user data on Mac');
    const archivedFiles = files.filter(file => !file.endsWith('/'));
    assert.deepEqual(unpackedFiles(folder).sort(), [...archivedFiles].sort(), `unpacked file list differs: ${folder}`);
    for (const file of archivedFiles) {
      assert.equal(hash(readFileSync(path.join(projectRoot, 'dist', file))), hash(tar(['-xOzf', archive, file])),
        `unpacked file differs: ${file}`);
    }
    verifyJavafxLibraries(files.filter(file => file.startsWith(`${folder}/app/`)).map(file => path.posix.basename(file)), architecture === 'arm64' ? 'mac-aarch64' : 'mac');
    const jar = `${folder}/app/${mainJar}`;
    assert.equal(hash(tar(['-xOzf', archive, jar])), expected, `stale Mac ${role} ${architecture} JAR`);
    const launcher = tar(['-xOzf', archive, `${folder}/BTL16-Auction-${role}.command`]).toString('utf8');
    assert.ok(!launcher.includes('\r') && launcher.includes(architecture === 'arm64' ? 'aarch64|arm64' : 'x86_64|amd64'), 'launcher architecture and LF');
    assert.ok(files.includes(`${folder}/23_ANH_VIDEO_SAN_PHAM.md`), 'media guide travels with every Mac package');
    if (role === 'Server') {
      const config = tar(['-xOzf', archive, `${folder}/config/server.properties`]).toString('utf8');
      assert.doesNotMatch(config, /^media\.(ffmpeg|ffprobe)=.*(?:[A-Za-z]:|\.exe)/m, 'Mac must not use Windows tool paths');
      assert.match(config, /^media\.port=8891\s*$/m, 'media port is distributable, not a random test port');
      assert.match(config, /^media\.imageMaxPixels=64000000\s*$/m, 'Mac image quota includes 48MP phone photos');
    }
    assert.ok(checksumFile.includes(`${hash(readFileSync(path.join(projectRoot, archive)))}  ${folder}.tar.gz`), 'archive checksum');
    console.log(`[PASS] ${folder}: archive AND unpacked files match; current JAR, correct JavaFX natives, launcher, media config, clean payload and SHA-256`);
  }
}
if (process.platform === 'win32') {
  const result = spawnSync('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'tools/verify-media-bundles.ps1'],
    { cwd: projectRoot, windowsHide: true, stdio: 'inherit' });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, 'Windows embedded bundle verification');
  const checksums = readFileSync(path.join(projectRoot, 'dist', 'SHA256SUMS.txt'), 'utf8');
  for (const role of ['Server', 'Client']) {
    const file = `BTL16-Auction-${role}.exe`;
    assert.ok(checksums.includes(`${hash(readFileSync(path.join(projectRoot, 'dist', file)))}  ${file}`), 'Windows EXE checksum');
  }
}
