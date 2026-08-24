import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const toolsDirectory = path.dirname(fileURLToPath(import.meta.url));

export const projectRoot = path.resolve(toolsDirectory, '..');
export const mysqlPort = 3306;
export const serverPort = 8888;

export function runtimeClasspath() {
  return [
    path.join(projectRoot, 'target', 'classes'),
    path.join(projectRoot, 'target', 'dependency', '*')
  ].join(path.delimiter);
}

export function runCommand(command, args, label = command) {
  return new Promise((resolve, reject) => {
    const windowsMaven = process.platform === 'win32' && command === 'mvn';
    const executable = windowsMaven ? (process.env.ComSpec || 'cmd.exe') : command;
    const commandArgs = windowsMaven
      ? ['/d', '/s', '/c', `mvn ${args.join(' ')}`]
      : args;
    const child = spawn(executable, commandArgs, {
      cwd: projectRoot,
      stdio: 'inherit',
      windowsHide: true
    });
    child.once('error', reject);
    child.once('exit', (code, signal) => {
      if (code === 0) {
        resolve();
        return;
      }
      reject(new Error(`${label} failed (${signal ?? `exit ${code}`})`));
    });
  });
}

export function startJava(mainClass, javaOptions = []) {
  return spawn('java', [
    ...javaOptions,
    '-cp',
    runtimeClasspath(),
    mainClass
  ], {
    cwd: projectRoot,
    stdio: 'inherit',
    windowsHide: false
  });
}

export async function buildProject() {
  console.log('[DEV] Building Java project...');
  await runCommand('mvn', [
    '-q',
    '-DskipTests',
    'package',
    'dependency:copy-dependencies',
    '-DincludeScope=runtime'
  ], 'Maven build');
}

export async function setupDatabase() {
  console.log('[DEV] Initializing/migrating MySQL and demo data...');
  await runCommand('java', [
    '-cp',
    runtimeClasspath(),
    'vn.ptit.btl16.server.db.DatabaseSetupMain'
  ], 'Database setup');
}

export function isPortOpen(host, port, timeoutMillis = 700) {
  return new Promise((resolve) => {
    const socket = net.createConnection({ host, port });
    const finish = (open) => {
      socket.removeAllListeners();
      socket.destroy();
      resolve(open);
    };
    socket.setTimeout(timeoutMillis);
    socket.once('connect', () => finish(true));
    socket.once('timeout', () => finish(false));
    socket.once('error', () => finish(false));
  });
}

export async function waitForPort(host, port, timeoutMillis) {
  const deadline = Date.now() + timeoutMillis;
  while (Date.now() < deadline) {
    if (await isPortOpen(host, port)) {
      return true;
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  return false;
}

export async function ensureMysql() {
  if (await isPortOpen('127.0.0.1', mysqlPort)) {
    console.log(`[DEV] MySQL is ready on 127.0.0.1:${mysqlPort}.`);
    return;
  }

  if (process.platform !== 'win32') {
    throw new Error(`MySQL is not listening on port ${mysqlPort}. Start MySQL first.`);
  }

  const laragonHome = process.env.LARAGON_HOME || 'C:\\laragon';
  const laragonExecutable = path.join(laragonHome, 'laragon.exe');
  if (!existsSync(laragonExecutable)) {
    throw new Error(
      `MySQL is stopped and Laragon was not found at ${laragonExecutable}. `
      + 'Start MySQL or set LARAGON_HOME.'
    );
  }

  console.log(`[DEV] Starting Laragon from ${laragonExecutable}...`);
  const laragon = spawn(laragonExecutable, [], {
    cwd: laragonHome,
    detached: true,
    stdio: 'ignore',
    windowsHide: true
  });
  laragon.unref();

  if (!await waitForPort('127.0.0.1', mysqlPort, 30_000)) {
    throw new Error(
      `Laragon opened but MySQL did not listen on port ${mysqlPort}. `
      + 'Open Laragon and press Start All, then run npm run dev again.'
    );
  }
  console.log(`[DEV] MySQL is ready on 127.0.0.1:${mysqlPort}.`);
}

export function lanIpv4Addresses() {
  const values = new Set();
  for (const addresses of Object.values(os.networkInterfaces())) {
    for (const address of addresses ?? []) {
      if (address.family === 'IPv4' && !address.internal) {
        values.add(address.address);
      }
    }
  }
  return [...values].sort();
}

export function optionValue(args, name, fallback = null) {
  const prefix = `${name}=`;
  const inline = args.find((value) => value.startsWith(prefix));
  if (inline) {
    return inline.slice(prefix.length);
  }
  const index = args.indexOf(name);
  if (index >= 0 && index + 1 < args.length) {
    return args[index + 1];
  }
  return fallback;
}

export async function stopProcessTree(child) {
  if (!child || child.exitCode !== null || !child.pid) {
    return;
  }

  if (process.platform === 'win32') {
    await new Promise((resolve) => {
      const killer = spawn('taskkill', ['/PID', String(child.pid), '/T', '/F'], {
        stdio: 'ignore',
        windowsHide: true
      });
      killer.once('error', resolve);
      killer.once('exit', resolve);
    });
    return;
  }

  child.kill('SIGTERM');
}
