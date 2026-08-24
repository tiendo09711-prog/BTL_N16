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
const transport = optionValue(args, '--transport', 'websocket').toLowerCase();
const url = optionValue(args, '--url', 'ws://127.0.0.1:8890/ws');
const host = optionValue(args, '--host', '127.0.0.1');
const portText = optionValue(args, '--port', String(serverPort));
const port = Number.parseInt(portText, 10);

if (!['websocket', 'tcp'].includes(transport)) {
  throw new Error('--transport must be websocket or tcp.');
}
if (transport === 'websocket' && !/^wss?:\/\//i.test(url)) {
  throw new Error('--url must start with ws:// or wss://.');
}
if (!host.trim()) {
  throw new Error('--host must not be empty.');
}
if (!Number.isInteger(port) || port < 1 || port > 65535) {
  throw new Error('--port must be an integer from 1 to 65535.');
}

if (!existsSync(path.join(projectRoot, 'target', 'classes'))) {
  await buildProject();
}

console.log(transport === 'tcp'
  ? `[CLIENT] JavaFX legacy TCP: ${host}:${port}`
  : `[CLIENT] JavaFX WebSocket: ${url}`);
const clientProcess = startJava(
  'vn.ptit.btl16.client.ClientMain',
  transport === 'tcp'
    ? [
        '-Dbtl16.client.transport=tcp',
        `-Dbtl16.client.host=${host}`,
        `-Dbtl16.client.port=${port}`
      ]
    : [
        '-Dbtl16.client.transport=websocket',
        `-Dbtl16.client.url=${url}`
      ]
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
