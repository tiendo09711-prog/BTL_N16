import { existsSync } from 'node:fs';
import path from 'node:path';
import {
  buildProject,
  optionValue,
  projectRoot,
  serverPort,
  startJava,
  stopProcessTree
} from './dev-utils.mjs';

const args = process.argv.slice(2);
const host = optionValue(args, '--host', '127.0.0.1');
const portText = optionValue(args, '--port', String(serverPort));
const port = Number.parseInt(portText, 10);

if (!host.trim()) {
  throw new Error('--host must not be empty.');
}
if (!Number.isInteger(port) || port < 1 || port > 65535) {
  throw new Error('--port must be an integer from 1 to 65535.');
}

if (!existsSync(path.join(projectRoot, 'target', 'classes'))) {
  await buildProject();
}

console.log(`[CLIENT] Connecting to ${host}:${port}`);
const clientProcess = startJava(
  'vn.ptit.btl16.client.ClientMain',
  [`-Dbtl16.client.host=${host}`, `-Dbtl16.client.port=${port}`]
);

let stopping = false;
async function shutdown(exitCode) {
  if (stopping) {
    return;
  }
  stopping = true;
  await stopProcessTree(clientProcess);
  process.exit(exitCode);
}

process.once('SIGINT', () => void shutdown(0));
process.once('SIGTERM', () => void shutdown(0));
clientProcess.once('error', (error) => {
  console.error('[CLIENT] Cannot start:', error.message);
  void shutdown(1);
});
clientProcess.once('exit', (code) => {
  if (!stopping) {
    process.exit(code ?? 0);
  }
});
