import { spawn } from 'node:child_process';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const toolsDirectory = path.dirname(fileURLToPath(import.meta.url));

export const projectRoot = path.resolve(toolsDirectory, '..');
export const serverPort = 8888;
export const webSocketPort = 8890;

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
  console.log('[DEV] Initializing MySQL schema (no demo data)...');
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
  await runCommand('java', [
    '-cp',
    runtimeClasspath(),
    'vn.ptit.btl16.server.db.DatabaseCheckMain'
  ], 'XAMPP JDBC connection check');
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
