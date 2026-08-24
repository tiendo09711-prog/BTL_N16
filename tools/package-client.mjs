import { spawn } from 'node:child_process';
import {
  cpSync,
  existsSync,
  mkdirSync,
  readdirSync,
  rmSync
} from 'node:fs';
import path from 'node:path';
import {
  buildProject,
  projectRoot
} from './dev-utils.mjs';

await buildProject();

const target = path.join(projectRoot, 'target');
const staging = path.join(target, 'jpackage-input');
const dist = path.join(projectRoot, 'dist');
const appName = 'BTL16-Auction-Client';
const appImage = path.join(dist, appName);
const mainJar = 'btl16-realtime-auction-1.0.0.jar';

rmSync(staging, { recursive: true, force: true });
rmSync(appImage, { recursive: true, force: true });
mkdirSync(staging, { recursive: true });
mkdirSync(dist, { recursive: true });
cpSync(path.join(target, mainJar), path.join(staging, mainJar));

const dependencyDirectory = path.join(target, 'dependency');
for (const file of readdirSync(dependencyDirectory)) {
  if (file.endsWith('.jar')) {
    cpSync(path.join(dependencyDirectory, file), path.join(staging, file));
  }
}

const javaHomeJpackage = process.env.JAVA_HOME
  ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'jpackage.exe' : 'jpackage')
  : null;
const executable = javaHomeJpackage && existsSync(javaHomeJpackage)
  ? javaHomeJpackage
  : 'jpackage';

const args = [
  '--type', 'app-image',
  '--name', appName,
  '--dest', dist,
  '--input', staging,
  '--main-jar', mainJar,
  '--main-class', 'vn.ptit.btl16.client.ClientMain',
  '--app-version', '1.0.0',
  '--description', 'BTL16 JavaFX Realtime Auction Client',
  '--vendor', 'PTIT BTL16',
  '--java-options', '-Dfile.encoding=UTF-8',
  '--java-options', '-Dbtl16.client.transport=websocket',
  '--java-options', '-Dbtl16.client.url=ws://127.0.0.1:8890/ws'
];

console.log(`[DIST] Running ${executable} ${args.join(' ')}`);
await new Promise((resolve, reject) => {
  const child = spawn(executable, args, {
    cwd: projectRoot,
    stdio: 'inherit',
    windowsHide: true
  });
  child.once('error', reject);
  child.once('exit', (code, signal) => {
    if (code === 0) {
      resolve();
    } else {
      reject(new Error(`jpackage failed (${signal ?? `exit ${code}`})`));
    }
  });
});

console.log(`[DIST] App image created: ${appImage}`);
console.log('[DIST] Copy this directory to another Windows computer; source code and IDE are not required.');
