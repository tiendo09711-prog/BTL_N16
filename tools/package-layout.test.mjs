import assert from 'node:assert/strict';
import path from 'node:path';
import test from 'node:test';
import { mkdirSync, mkdtempSync, realpathSync, rmSync, writeFileSync } from 'node:fs';
import { applicationDetails, desktopLayout, portableArchitectures, portableLauncher, verifyJavafxLibraries } from './package-layout.mjs';
import { distributionGuides, distributionMigrations, sourceFingerprint, verifyBuildInfo } from './distribution-metadata.mjs';

test('Windows packaging keeps the existing EXE directory and config layout', () => {
  const layout = desktopLayout('server', 'win32', 'x64');
  assert.equal(layout.destination, 'dist');
  assert.equal(layout.imageName, 'BTL16-Auction-Server');
  assert.equal(layout.contentDirectory, 'app');
  assert.equal(layout.jpackageBinary, 'jpackage.exe');
});

test('all distributions carry chat upgrade guides and non-destructive migration scripts', () => {
  for (const guide of ['29_TUONG_TAC_CHAT.md', '30_KET_QUA_PHIEN.md', '31_HOAN_THIEN_CHUNG.md', '34_CAP_NHAT_BAN_PHAN_PHOI.md']) assert.ok(distributionGuides.includes(guide));
  assert.ok(distributionMigrations.includes('03_chat_interactions.sql'));
  assert.ok(!distributionMigrations.some(file => /reset/i.test(file)));
});

test('source identity includes Java, resources and Maven configuration, not docs or timestamps', context => {
  const target = path.resolve('target'); mkdirSync(target, { recursive: true });
  const root = mkdtempSync(path.join(target, 'distribution-metadata-test-'));
  context.after(() => {
    assert.equal(path.dirname(realpathSync(root)), realpathSync(target));
    rmSync(root, { recursive: true, force: true });
  });
  mkdirSync(path.join(root, 'src', 'main', 'java'), { recursive: true });
  mkdirSync(path.join(root, 'src', 'main', 'resources'), { recursive: true });
  writeFileSync(path.join(root, 'pom.xml'), '<project/>');
  const source = path.join(root, 'src', 'main', 'java', 'Chat.java');
  writeFileSync(source, 'chat-v1');
  const initial = sourceFingerprint(root);
  writeFileSync(source, 'chat-v1');
  writeFileSync(path.join(root, 'README.md'), 'documentation');
  assert.equal(sourceFingerprint(root), initial);
  writeFileSync(source, 'chat-v2'); assert.notEqual(sourceFingerprint(root), initial);
  const withChat = sourceFingerprint(root);
  writeFileSync(path.join(root, 'src', 'main', 'resources', 'chat.css'), 'new-style');
  assert.notEqual(sourceFingerprint(root), withChat);
  const withCss = sourceFingerprint(root);
  writeFileSync(path.join(root, 'pom.xml'), '<project>updated</project>');
  assert.notEqual(sourceFingerprint(root), withCss);
});

test('build identity rejects stale source, mismatched JAR or architecture', () => {
  const info = { schemaVersion: 1, builtAt: '2026-10-26T00:00:00.000Z', jar: 'btl16-realtime-auction-1.0.0.jar', jarSha256: 'current-jar', sourceSha256: 'current-source', role: 'client', platform: 'darwin', architecture: 'arm64' };
  const expected = { role: 'client', platform: 'darwin', architecture: 'arm64' };
  assert.doesNotThrow(() => verifyBuildInfo(info, 'current-jar', 'current-source', expected));
  assert.throws(() => verifyBuildInfo(info, 'new-jar', 'current-source', expected), /different JAR/);
  assert.throws(() => verifyBuildInfo(info, 'current-jar', 'new-source', expected), /rebuild all/);
  assert.throws(() => verifyBuildInfo(info, 'current-jar', 'current-source', { architecture: 'x64' }), /architecture/);
});

test('macOS packages both roles into architecture-specific app bundles', () => {
  for (const architecture of ['arm64', 'x64']) {
    for (const role of ['client', 'server']) {
      const layout = desktopLayout(role, 'darwin', architecture);
      assert.equal(layout.destination, path.join('dist', `macos-${architecture}`));
      assert.equal(layout.imageName, `${applicationDetails(role).appName}.app`);
      assert.equal(layout.contentDirectory, path.join('Contents', 'app'));
      assert.equal(layout.jpackageBinary, 'jpackage');
      assert.equal(layout.mainClass, applicationDetails(role).mainClass);
    }
  }
});

test('unsupported roles, operating systems and architectures fail before packaging', () => {
  assert.throws(() => applicationDetails('unknown'));
  assert.throws(() => desktopLayout('client', 'linux', 'x64'));
  assert.throws(() => desktopLayout('server', 'darwin', 'ia32'));
  assert.throws(() => portableLauncher('client', 'ia32'));
});

test('JavaFX validation rejects Windows libraries and mixed Mac architectures', () => {
  for (const classifier of ['mac', 'mac-aarch64']) {
    const libraries = ['base', 'graphics', 'controls', 'media'].map(module => `javafx-${module}-21.0.4-${classifier}.jar`);
    assert.doesNotThrow(() => verifyJavafxLibraries([...libraries, 'javafx-controls-21.0.4.jar'], classifier));
    assert.throws(() => verifyJavafxLibraries(libraries.slice(1), classifier), /Missing JavaFX/);
    assert.throws(() => verifyJavafxLibraries(libraries.slice(0, -1), classifier), /Missing JavaFX media/);
    assert.throws(() => verifyJavafxLibraries([...libraries, 'javafx-graphics-21.0.4-win.jar'], classifier), /Mixed/);
    const other = classifier === 'mac' ? 'mac-aarch64' : 'mac';
    assert.throws(() => verifyJavafxLibraries([...libraries, `javafx-graphics-21.0.4-${other}.jar`], classifier), /Mixed/);
  }
});

test('portable packaging can select only Intel without changing the default architectures', () => {
  assert.deepEqual(portableArchitectures([]), ['arm64', 'x64']);
  assert.deepEqual(portableArchitectures(['--arch=x64']), ['x64']);
  assert.deepEqual(portableArchitectures(['--arch=arm64']), ['arm64']);
  for (const args of [['--arch=ia32'], ['--arch'], ['--unknown'], ['--arch=x64', '--arch=arm64']]) {
    assert.throws(() => portableArchitectures(args), /Usage:/);
  }
});

test('portable launchers handle spaces, validate Java and do not hard-code server IPs', () => {
  for (const architecture of ['arm64', 'x64']) {
    const client = portableLauncher('client', architecture);
    const server = portableLauncher('server', architecture);
    for (const launcher of [client, server]) {
      assert.ok(launcher.startsWith('#!/bin/bash\n'));
      assert.ok(!launcher.includes('\r'));
      assert.ok(launcher.includes('cd "$(dirname "$0")"'));
      assert.ok(launcher.includes('"$JAVA_HOME/bin/java"'));
      assert.ok(launcher.includes('[ "$java_version" -ge 17 ]'));
      assert.ok(launcher.includes("-cp 'app/*'"));
      assert.ok(launcher.includes(architecture === 'arm64' ? 'aarch64|arm64' : 'x86_64|amd64'));
      assert.doesNotMatch(launcher, /172\.11\.|192\.168\.|127\.0\.0\.1/);
    }
    assert.ok(client.includes('-Dbtl16.client.manualConnect=true'));
    assert.ok(client.includes(applicationDetails('client').mainClass));
    assert.ok(!client.includes('server.properties'));
    assert.ok(server.includes('"-Dbtl16.server.config=$PWD/config/server.properties"'));
    assert.ok(server.includes(applicationDetails('server').mainClass));
  }
});
