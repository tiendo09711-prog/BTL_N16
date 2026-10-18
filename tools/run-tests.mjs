import { spawn } from 'node:child_process';
import { createWriteStream, existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { projectRoot } from './dev-utils.mjs';

const flags = new Set(process.argv.slice(2));
const allowed = new Set(['--ui', '--mysql', '--skip-build', '--packaged']);
for (const flag of flags) {
  if (!allowed.has(flag)) throw new Error(`Unknown test option: ${flag}`);
}
if (flags.has('--packaged') && process.platform !== 'win32') {
  throw new Error('--packaged uses the Windows server runtime; run native macOS tests on a Mac without this flag.');
}
const output = path.join(projectRoot, 'target', flags.has('--packaged') ? 'qa-packaged' : 'qa');
mkdirSync(output, { recursive: true });
const results = [];
const startedAt = new Date().toISOString();

async function check(name, command, args, timeout = 300_000) {
  const log = path.join(output, `${name}.log`);
  console.log(`[TEST] ${name}`);
  const started = Date.now();
  const result = await new Promise(resolve => {
    const stream = createWriteStream(log);
    const windowsMaven = process.platform === 'win32' && command === 'mvn';
    const child = spawn(windowsMaven ? (process.env.ComSpec || 'cmd.exe') : command,
      windowsMaven ? ['/d', '/s', '/c', `mvn ${args.join(' ')}`] : args,
      { cwd: projectRoot, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
    let expired = false;
    const timer = setTimeout(() => { expired = true; child.kill(); }, timeout);
    child.stdout.pipe(stream, { end: false }); child.stderr.pipe(stream, { end: false });
    child.once('error', error => stream.write(`${error.stack}\n`));
    child.once('close', (code, signal) => {
      clearTimeout(timer);
      stream.end(() => resolve({ name, passed: code === 0 && !expired, code, signal, expired,
        milliseconds: Date.now() - started, log: path.relative(projectRoot, log) }));
    });
  });
  results.push(result);
  if (result.passed) {
    const passes = readFileSync(log, 'utf8').split(/\r?\n/).filter(line => /^\[PASS\]/.test(line));
    console.log(passes.length ? passes.join('\n') : `[PASS] ${name}`);
  } else {
    console.error(`[FAIL] ${name}: ${result.expired ? 'timeout' : `exit ${result.code}`}`);
    console.error(readFileSync(log, 'utf8').split(/\r?\n/).slice(-24).join('\n'));
  }
  writeFileSync(path.join(output, 'results.json'), JSON.stringify({ platform: process.platform, architecture: process.arch,
    packaged: flags.has('--packaged'), startedAt, results }, null, 2) + '\n');
  return result.passed;
}

if (!flags.has('--skip-build')) {
  if (!await check('compile', 'mvn', ['-q', 'test-compile', 'dependency:copy-dependencies', '-DincludeScope=runtime'])) process.exit(1);
}
const packaged = flags.has('--packaged');
const app = path.join(projectRoot, 'dist', 'BTL16-Auction-Server');
const libraries = packaged ? path.join(app, 'app') : path.join(projectRoot, 'target', 'dependency');
const classpath = [packaged ? path.join(libraries, 'btl16-realtime-auction-1.0.0.jar') : path.join(projectRoot, 'target', 'classes'),
  path.join(projectRoot, 'target', 'test-classes'), path.join(libraries, '*')].join(path.delimiter);
const java = packaged ? path.join(app, 'runtime', 'bin', 'java.exe') : 'java';
const javafxModules = readdirSync(libraries).filter(file => file.startsWith('javafx-')
  && /-(?:win|mac(?:-aarch64)?|linux(?:-aarch64)?)\.jar$/.test(file)).map(file => path.join(libraries, file)).join(path.delimiter);
if (packaged && !existsSync(java)) throw new Error('Build the Windows distribution first: npm run dist');
await check('packaging-unit', 'node', ['--test', 'tools/package-layout.test.mjs', 'tools/package-windows-bundle.test.mjs']);
await check('AllSelfTests', java, ['-cp', classpath, 'vn.ptit.btl16.selftest.AllSelfTests'], 600_000);
if (flags.has('--ui')) {
  for (const test of ['AuctionButtonsUiSelfTest', 'ServerButtonsUiSelfTest', 'DesktopUiSelfTest', 'MediaUiSelfTest', 'ProductRestorationUiSelfTest']) {
    await check(test, java, ['--module-path', javafxModules, '--add-modules', 'javafx.controls,javafx.media',
      '-cp', classpath, `vn.ptit.btl16.selftest.${test}`]);
  }
}
if (flags.has('--mysql')) {
  await check('XamppDatabaseSelfTest', java, ['-cp', classpath, 'vn.ptit.btl16.selftest.XamppDatabaseSelfTest']);
  await check('JdbcProductRestorationMySqlSelfTest', java, ['-cp', classpath, 'vn.ptit.btl16.selftest.JdbcProductRestorationSelfTest', '--mysql']);
}
const failed = results.filter(result => !result.passed);
console.log(`[QA] ${results.length - failed.length}/${results.length} groups passed. Logs: ${path.relative(projectRoot, output)}`);
process.exitCode = failed.length ? 1 : 0;
