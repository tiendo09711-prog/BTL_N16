import { spawn, spawnSync } from 'node:child_process';
import { cpSync, existsSync, lstatSync, mkdirSync, readdirSync, realpathSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { buildProject, projectRoot } from './dev-utils.mjs';
import { desktopLayout, mainJar, verifyJavafxLibraries } from './package-layout.mjs';
import { packageWindowsBundle } from './package-windows-bundle.mjs';

export function removeBuildDirectory(directory) {
  const relative = path.relative(projectRoot, directory);
  if (!relative || relative.startsWith('..') || path.isAbsolute(relative)
      || !['target', 'dist'].includes(relative.split(path.sep)[0])) {
    throw new Error(`Refusing to remove directory outside build outputs: ${directory}`);
  }
  if (existsSync(directory)) {
    const resolved = path.relative(realpathSync(projectRoot), realpathSync(directory));
    if (lstatSync(directory).isSymbolicLink() || resolved.toLowerCase() !== relative.toLowerCase()) {
      throw new Error(`Refusing to remove redirected build directory: ${directory}`);
    }
    rmSync(directory, { recursive: true, force: true });
  }
}

export async function packageApplications(roles) {
  for (const role of roles) {
    desktopLayout(role, process.platform, process.arch);
  }
  if (process.platform === 'darwin') {
    if (!process.env.JAVA_HOME) {
      throw new Error('Set JAVA_HOME to a macOS JDK 17+ containing jpackage before packaging.');
    }
    const java = spawnSync(path.join(process.env.JAVA_HOME, 'bin', 'java'),
      ['-XshowSettings:properties', '-version'], { encoding: 'utf8' });
    const javaArch = /os\.arch\s*=\s*(\S+)/.exec(java.stderr ?? '')?.[1];
    const expected = process.arch === 'arm64' ? ['aarch64', 'arm64'] : ['x86_64', 'amd64'];
    if (java.status !== 0 || !expected.includes(javaArch)) {
      throw new Error('Use Node.js and JAVA_HOME with the same macOS architecture (arm64 or x64).');
    }
    if (!existsSync(path.join(process.env.JAVA_HOME, 'bin', 'jpackage'))) {
      throw new Error('JAVA_HOME must contain bin/jpackage.');
    }
  }
  removeBuildDirectory(path.join(projectRoot, 'target', 'dependency'));
  await buildProject();
  for (const role of roles) {
    await packageApplication(role);
  }
  if (process.platform === 'win32') {
    cpSync(path.join(projectRoot, 'docs', '22_HAI_UNG_DUNG_JAVAFX.md'),
      path.join(projectRoot, 'dist', 'HUONG_DAN.md'));
    for (const guide of ['23_ANH_VIDEO_SAN_PHAM.md', '24_KIEM_THU_TOAN_BO.md', 'LUAT_DAU_GIA_BTL16.docx']) {
      cpSync(path.join(projectRoot, 'docs', guide), path.join(projectRoot, 'dist', guide));
    }
    const bundles = readdirSync(path.join(projectRoot, 'dist')).filter(file => /^BTL16-Auction-(Server|Client)\.exe$/.test(file));
    const { createHash } = await import('node:crypto');
    const { readFileSync } = await import('node:fs');
    writeFileSync(path.join(projectRoot, 'dist', 'SHA256SUMS.txt'), bundles.map(file =>
      `${createHash('sha256').update(readFileSync(path.join(projectRoot, 'dist', file))).digest('hex')}  ${file}`).join('\n') + '\n');
  }
}

async function packageApplication(role) {
  const server = role === 'server';
  const layout = desktopLayout(role, process.platform, process.arch);
  const { appName } = layout;
  const target = path.join(projectRoot, 'target');
  const staging = path.join(target, `jpackage-${role}-input`);
  const dist = path.join(projectRoot, layout.destination);
  const appImage = path.join(dist, layout.imageName);

  removeBuildDirectory(staging);
  mkdirSync(staging, { recursive: true });
  mkdirSync(dist, { recursive: true });
  cpSync(path.join(target, mainJar), path.join(staging, mainJar));
  const dependencies = path.join(target, 'dependency');
  if (process.platform === 'darwin') {
    verifyJavafxLibraries(readdirSync(dependencies), process.arch === 'arm64' ? 'mac-aarch64' : 'mac');
  }
  for (const file of readdirSync(dependencies)) {
    if (file.endsWith('.jar')) {
      cpSync(path.join(dependencies, file), path.join(staging, file));
    }
  }
  if (server) {
    const mediaTools = path.join(projectRoot, 'tools', 'media');
    for (const binary of ['ffmpeg.exe', 'ffprobe.exe']) {
      if (process.platform === 'win32' && !existsSync(path.join(mediaTools, binary))) {
        throw new Error(`Missing ${binary}; run tools/prepare-media.ps1 before packaging.`);
      }
    }
    if (process.platform === 'win32' && existsSync(mediaTools)) {
      cpSync(mediaTools, path.join(staging, 'tools', 'media'), { recursive: true });
    }
    const previousConfig = path.join(appImage, layout.contentDirectory, 'config', 'server.properties');
    mkdirSync(path.join(staging, 'config'), { recursive: true });
    cpSync(existsSync(previousConfig) ? previousConfig : path.join(projectRoot, 'config', 'server-desktop.properties'),
      path.join(staging, 'config', 'server.properties'));
  }
  removeBuildDirectory(appImage);

  const javaHomeJpackage = process.env.JAVA_HOME
    ? path.join(process.env.JAVA_HOME, 'bin', layout.jpackageBinary) : null;
  const executable = javaHomeJpackage && existsSync(javaHomeJpackage) ? javaHomeJpackage : 'jpackage';
  const args = [
    '--type', 'app-image', '--name', appName, '--dest', dist, '--input', staging,
    '--main-jar', mainJar,
    '--main-class', layout.mainClass,
    ...(process.platform === 'win32' ? ['--icon', path.join(projectRoot, 'config', 'auction.ico')] : []),
    '--add-modules', 'java.se,jdk.httpserver,jdk.unsupported,jdk.crypto.ec,jdk.charsets,jdk.localedata',
    '--jlink-options', '--strip-debug --no-man-pages --no-header-files',
    '--app-version', '1.0.0', '--description', `BTL16 Auction ${role}`, '--vendor', 'PTIT BTL16',
    '--java-options', '-Dfile.encoding=UTF-8',
    ...(server
      ? ['--java-options', '-Dbtl16.server.config=$APPDIR/config/server.properties']
      : ['--java-options', '-Dbtl16.client.transport=websocket',
          '--java-options', '-Dbtl16.client.manualConnect=true'])
  ];
  console.log(`[DIST] Packaging ${appName}...`);
  await new Promise((resolve, reject) => {
    const child = spawn(executable, args, { cwd: projectRoot, stdio: 'inherit', windowsHide: true });
    child.once('error', reject);
    child.once('exit', (code, signal) => code === 0 ? resolve()
      : reject(new Error(`jpackage failed (${signal ?? `exit ${code}`})`)));
  });
  if (process.platform === 'darwin') {
    writeFileSync(path.join(dist, `${appName}-START_HERE.txt`),
      `BTL16 macOS ${process.arch}\nOpen ${layout.imageName}. Java is included.\n`
      + (server
        ? `Local database is included. Optional MySQL settings: ${layout.imageName}/Contents/app/config/server.properties.\nImage/video uploads require FFmpeg 8+ and ffprobe (brew install ffmpeg); media tools are not bundled in the Mac app.\nAllow incoming connections on ports 8890 and 8891 in macOS Firewall.\nCopy the current LAN address from the dashboard to clients.\n`
        : 'Paste the current WebSocket address copied from the server. No separate Java or database installation is required.\n')
      + 'This is an unsigned local build. Only open builds whose source you trust.\n', 'utf8');
    console.log(`[DIST] Ready: ${appImage}`);
    return;
  }
  writeFileSync(path.join(appImage, 'START_HERE.txt'), server
    ? 'AUCTION SERVER\r\nOpen BTL16-Auction-Server.exe. Java, media processing and a persistent local database are included.\r\nShare the connection code or LAN address with clients on the same trusted network.\r\nAllow inbound TCP 8890 and 8891 on Private networks in Windows Firewall.\r\nData: %LOCALAPPDATA%/BTL16/AuctionServer. Back up the database and the media folder together while the server is stopped.\r\nKeep this entire app-image folder together. The single-file EXE in the parent dist folder can be copied alone.\r\n'
    : 'BTL16 CLIENT\r\n1. Open BTL16-Auction-Client.exe.\r\n2. Paste the connection code or address from the server, then click Ket noi.\r\n3. Register or log in to join auctions.\r\nKeep this entire app-image folder together. The single-file EXE in the parent dist folder can be copied alone. No separate Java or MySQL installation is needed.\r\n', 'utf8');
  console.log(`[DIST] Ready: ${path.join(appImage, `${appName}.exe`)}`);
  console.log('[DIST] Copy the WHOLE application directory, not the EXE alone.');
  await packageWindowsBundle(appName, appImage);
}
