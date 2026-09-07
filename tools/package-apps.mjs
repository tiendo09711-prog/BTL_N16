import { spawn } from 'node:child_process';
import { cpSync, existsSync, lstatSync, mkdirSync, readdirSync, realpathSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { buildProject, projectRoot } from './dev-utils.mjs';

function removeBuildDirectory(directory) {
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
  if (process.platform !== 'win32') {
    throw new Error('Build these Windows EXE applications on Windows.');
  }
  if (roles.some(role => !['server', 'client'].includes(role))) {
    throw new Error('Application role must be server or client.');
  }
  await buildProject();
  for (const role of roles) {
    await packageApplication(role);
  }
}

async function packageApplication(role) {
  const server = role === 'server';
  const appName = `BTL16-Auction-${server ? 'Server' : 'Client'}`;
  const target = path.join(projectRoot, 'target');
  const staging = path.join(target, `jpackage-${role}-input`);
  const dist = path.join(projectRoot, 'dist');
  const appImage = path.join(dist, appName);
  const mainJar = 'btl16-realtime-auction-1.0.0.jar';

  removeBuildDirectory(staging);
  mkdirSync(staging, { recursive: true });
  mkdirSync(dist, { recursive: true });
  cpSync(path.join(target, mainJar), path.join(staging, mainJar));
  const dependencies = path.join(target, 'dependency');
  for (const file of readdirSync(dependencies)) {
    if (file.endsWith('.jar')) {
      cpSync(path.join(dependencies, file), path.join(staging, file));
    }
  }
  if (server) {
    const previousConfig = path.join(appImage, 'app', 'config', 'server.properties');
    mkdirSync(path.join(staging, 'config'), { recursive: true });
    cpSync(existsSync(previousConfig) ? previousConfig : path.join(projectRoot, 'config', 'server.properties'),
      path.join(staging, 'config', 'server.properties'));
  }
  removeBuildDirectory(appImage);

  const javaHomeJpackage = process.env.JAVA_HOME
    ? path.join(process.env.JAVA_HOME, 'bin', 'jpackage.exe') : null;
  const executable = javaHomeJpackage && existsSync(javaHomeJpackage) ? javaHomeJpackage : 'jpackage';
  const args = [
    '--type', 'app-image', '--name', appName, '--dest', dist, '--input', staging,
    '--main-jar', mainJar,
    '--main-class', server ? 'vn.ptit.btl16.server.dashboard.ServerDashboardMain' : 'vn.ptit.btl16.client.ClientMain',
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
  writeFileSync(path.join(appImage, 'START_HERE.txt'), server
    ? 'BTL16 SERVER\r\n1. Start MySQL in XAMPP on this computer.\r\n2. Match db.port in app/config/server.properties to your XAMPP MySQL port; check DB credentials.\r\n3. Open BTL16-Auction-Server.exe. A new database starts empty; no demo data is created.\r\n4. Copy the LAN address shown in the dashboard to clients.\r\n5. Allow inbound TCP 8890 on Private networks in Windows Firewall (or your configured WebSocket port).\r\nKeep this whole folder, not only the EXE. Java is included; MySQL is not.\r\n'
    : 'BTL16 CLIENT\r\n1. Open BTL16-Auction-Client.exe.\r\n2. Paste the address copied from the server, then click Ket noi.\r\n3. Register or log in to join auctions.\r\nKeep this whole folder, not only the EXE. No separate Java or MySQL installation is needed.\r\n', 'utf8');
  console.log(`[DIST] Ready: ${path.join(appImage, `${appName}.exe`)}`);
  console.log('[DIST] Copy the WHOLE application directory, not the EXE alone.');
}
