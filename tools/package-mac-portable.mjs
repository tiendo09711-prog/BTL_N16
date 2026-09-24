import { chmodSync, cpSync, existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { buildProject, projectRoot, runCommand } from './dev-utils.mjs';
import { removeBuildDirectory } from './package-apps.mjs';
import { applicationDetails, mainJar, portableLauncher, verifyJavafxLibraries } from './package-layout.mjs';

await buildProject();
for (const architecture of ['arm64', 'x64']) {
  const classifier = architecture === 'arm64' ? 'mac-aarch64' : 'mac';
  const dependencies = path.join(projectRoot, 'target', `macos-${architecture}-dependency`);
  removeBuildDirectory(dependencies);
  await runCommand('mvn', ['-q', 'dependency:copy-dependencies', '-DincludeScope=runtime',
    `-Djavafx.platform=${classifier}`, `-DoutputDirectory=target/macos-${architecture}-dependency`],
  `Resolve macOS ${architecture} dependencies`);
  verifyJavafxLibraries(readdirSync(dependencies), classifier);
  for (const role of ['client', 'server']) {
    const { appName } = applicationDetails(role);
    const folderName = `${appName}-macOS-${architecture}-portable`;
    const destination = path.join(projectRoot, 'dist', folderName);
    const configPath = path.join(destination, 'config', 'server.properties');
    const serverConfig = role === 'server'
      ? readFileSync(existsSync(configPath) ? configPath : path.join(projectRoot, 'config', 'server.properties'))
      : null;
    removeBuildDirectory(destination);
    mkdirSync(path.join(destination, 'app'), { recursive: true });
    cpSync(path.join(projectRoot, 'target', mainJar), path.join(destination, 'app', mainJar));
    cpSync(dependencies, path.join(destination, 'app'), { recursive: true });
    if (serverConfig) {
      mkdirSync(path.join(destination, 'config'), { recursive: true });
      writeFileSync(configPath, serverConfig);
    }
    const launcher = path.join(destination, `${appName}.command`);
    writeFileSync(launcher, portableLauncher(role, architecture), 'utf8');
    chmodSync(launcher, 0o755);
    writeFileSync(path.join(destination, 'START_HERE.txt'),
      `BTL16 macOS ${architecture} PORTABLE\n\n`
      + 'Requires an installed macOS JDK 17+ of the same architecture. Java is NOT bundled.\n'
      + 'arm64 = Apple Silicon (M-series); x64 = Intel. Do not mix Java architectures.\n'
      + `Extract the WHOLE archive, then open ${appName}.command.\n`
      + `If executable permission was lost, open Terminal in this folder and run:\nbash ./${appName}.command\n\n`
      + (role === 'server'
        ? 'Start a local MySQL service (XAMPP or another installation). Edit config/server.properties for its port and credentials.\nAllow incoming connections to Java in macOS Firewall. Keep server open; share its current ws://IP:8890/ws address.\n'
        : 'No MySQL, Maven or Node.js is required. Paste the address from the running server, then connect.\n')
      + '\nWindows and macOS clients/servers use the same network protocol. No fixed server IP.\n'
      + 'For a native .app with Java included, build the source ON macOS with npm run dist.\n', 'utf8');
    const archive = path.join(projectRoot, 'dist', `${folderName}.tar.gz`);
    await runCommand('tar', ['-czf', archive, '-C', path.join(projectRoot, 'dist'), folderName],
      `Archive ${folderName}`);
    console.log(`[DIST] Ready: ${archive}`);
  }
}
