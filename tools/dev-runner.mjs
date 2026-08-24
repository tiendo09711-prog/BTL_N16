import {
  buildProject,
  ensureMysql,
  isPortOpen,
  lanIpv4Addresses,
  serverPort,
  setupDatabase,
  startJava,
  stopProcessTree,
  waitForPort
} from './dev-utils.mjs';

const args = process.argv.slice(2);
const serverOnly = args.includes('--server-only') || args.includes('--no-client');
const skipSetup = args.includes('--skip-setup');

let serverProcess = null;
let clientProcess = null;
let shuttingDown = false;

async function shutdown(exitCode) {
  if (shuttingDown) {
    return;
  }
  shuttingDown = true;
  console.log('\n[DEV] Stopping BTL16 client/server...');
  await stopProcessTree(clientProcess);
  await stopProcessTree(serverProcess);
  console.log('[DEV] BTL16 stopped. MySQL remains running intentionally.');
  process.exit(exitCode);
}

process.once('SIGINT', () => void shutdown(0));
process.once('SIGTERM', () => void shutdown(0));

try {
  console.log('====================================================');
  console.log(' BTL16 DEVELOPMENT RUNNER');
  console.log('====================================================');

  await ensureMysql();
  await buildProject();
  if (!skipSetup) {
    await setupDatabase();
  }

  if (await isPortOpen('127.0.0.1', serverPort)) {
    throw new Error(`Port ${serverPort} is already occupied. Stop the old server first.`);
  }

  console.log('[DEV] Starting MySQL/JDBC auction server...');
  serverProcess = startJava('vn.ptit.btl16.server.ServerMain');
  serverProcess.once('error', (error) => {
    console.error('[DEV] Cannot start server:', error.message);
    void shutdown(1);
  });
  serverProcess.once('exit', (code, signal) => {
    if (!shuttingDown) {
      console.error(`[DEV] Server stopped unexpectedly (${signal ?? `exit ${code}`}).`);
      void shutdown(code ?? 1);
    }
  });

  if (!await waitForPort('127.0.0.1', serverPort, 20_000)) {
    throw new Error(`Server did not listen on port ${serverPort}.`);
  }

  const addresses = lanIpv4Addresses();
  console.log('');
  console.log('[DEV] Server is ready.');
  console.log(`[DEV] Local TCP address: 127.0.0.1:${serverPort}`);
  for (const address of addresses) {
    console.log(`[DEV] LAN TCP address:   ${address}:${serverPort}`);
    console.log(`[DEV] Remote command:    npm run client -- --host=${address}`);
  }
  console.log('[DEV] These are TCP addresses, not browser links.');
  console.log('[DEV] Remote computers need the Java client and inbound firewall TCP 8888.');

  if (!serverOnly) {
    console.log('[DEV] Starting one local Swing client...');
    clientProcess = startJava(
      'vn.ptit.btl16.client.ClientMain',
      ['-Dbtl16.client.host=127.0.0.1', `-Dbtl16.client.port=${serverPort}`]
    );
    clientProcess.once('error', (error) => {
      console.error('[DEV] Cannot start local client:', error.message);
    });
  }

  console.log('[DEV] Press Ctrl+C to stop BTL16 server/client.');
} catch (error) {
  console.error('[DEV] Startup failed:', error.message);
  await shutdown(1);
}
