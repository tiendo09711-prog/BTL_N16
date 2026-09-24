import assert from 'node:assert/strict';
import path from 'node:path';
import test from 'node:test';
import { applicationDetails, desktopLayout, portableLauncher, verifyJavafxLibraries } from './package-layout.mjs';

test('Windows packaging keeps the existing EXE directory and config layout', () => {
  const layout = desktopLayout('server', 'win32', 'x64');
  assert.equal(layout.destination, 'dist');
  assert.equal(layout.imageName, 'BTL16-Auction-Server');
  assert.equal(layout.contentDirectory, 'app');
  assert.equal(layout.jpackageBinary, 'jpackage.exe');
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
    const libraries = ['base', 'graphics', 'controls'].map(module => `javafx-${module}-21.0.4-${classifier}.jar`);
    assert.doesNotThrow(() => verifyJavafxLibraries([...libraries, 'javafx-controls-21.0.4.jar'], classifier));
    assert.throws(() => verifyJavafxLibraries(libraries.slice(1), classifier), /Missing JavaFX/);
    assert.throws(() => verifyJavafxLibraries([...libraries, 'javafx-graphics-21.0.4-win.jar'], classifier), /Mixed/);
    const other = classifier === 'mac' ? 'mac-aarch64' : 'mac';
    assert.throws(() => verifyJavafxLibraries([...libraries, `javafx-graphics-21.0.4-${other}.jar`], classifier), /Mixed/);
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
