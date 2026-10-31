import { spawn } from 'node:child_process';
import { createWriteStream, existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { projectRoot } from './dev-utils.mjs';

const flags = new Set(process.argv.slice(2));
const onlyFlag = [...flags].find(flag => flag.startsWith('--only='));
const selectedTests = new Set(onlyFlag ? onlyFlag.slice('--only='.length).split(',').filter(Boolean) : []);
if (onlyFlag && !selectedTests.size) throw new Error('--only requires at least one test group name.');
if (onlyFlag) flags.delete(onlyFlag);
const allowed = new Set(['--ui', '--ui-only', '--mysql', '--skip-build', '--packaged']);
for (const flag of flags) {
  if (!allowed.has(flag)) throw new Error(`Unknown test option: ${flag}`);
}
if (flags.has('--ui-only')) flags.add('--ui');
if (flags.has('--packaged') && process.platform !== 'win32') {
  throw new Error('--packaged uses the Windows server runtime; run native macOS tests on a Mac without this flag.');
}
const output = path.join(projectRoot, 'target', selectedTests.size ? 'qa-focused' : flags.has('--packaged') ? 'qa-packaged' : flags.has('--ui-only') ? 'qa-ui' : 'qa');
mkdirSync(output, { recursive: true });
if (selectedTests.size && existsSync(path.join(output, 'results.json'))) {
  const previous = readFileSync(path.join(output, 'results.json'), 'utf8');
  const history = path.join(output, 'history'); mkdirSync(history, { recursive: true });
  writeFileSync(path.join(history, `${Date.parse(JSON.parse(previous).startedAt)}.json`), previous);
}
const results = [];
const startedAt = new Date().toISOString();

async function check(name, command, args, timeout = 300_000) {
  if (selectedTests.size && name !== 'compile' && !selectedTests.has(name)) return true;
  if (name.endsWith('UiSelfTest')) args = [`-Dprism.order=${process.env.BTL16_TEST_PRISM || 'sw'}`, ...args];
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
  if (!await check('compile', 'mvn', ['-q', 'test-compile', 'dependency:copy-dependencies', '-DincludeScope=test'])) process.exit(1);
}
const packaged = flags.has('--packaged');
const app = path.join(projectRoot, 'dist', 'BTL16-Auction-Server');
const libraries = packaged ? path.join(app, 'app') : path.join(projectRoot, 'target', 'dependency');
const classpath = [packaged ? path.join(libraries, 'btl16-realtime-auction-1.0.0.jar') : path.join(projectRoot, 'target', 'classes'),
  path.join(projectRoot, 'target', 'test-classes'), path.join(libraries, '*'),
  ...readdirSync(path.join(projectRoot, 'target', 'dependency')).filter(file => file.startsWith('h2-'))
    .map(file => path.join(projectRoot, 'target', 'dependency', file))].join(path.delimiter);
const java = packaged ? path.join(app, 'runtime', 'bin', 'java.exe') : 'java';
const javafxModules = readdirSync(libraries).filter(file => file.startsWith('javafx-')
  && /-(?:win|mac(?:-aarch64)?|linux(?:-aarch64)?)\.jar$/.test(file)).map(file => path.join(libraries, file)).join(path.delimiter);
if (packaged && !existsSync(java)) throw new Error('Build the Windows distribution first: npm run dist');
if (!flags.has('--ui-only')) {
  await check('packaging-unit', 'node', ['--test', 'tools/package-layout.test.mjs', 'tools/package-windows-bundle.test.mjs']);
  await check('AllSelfTests', java, ['-Dbtl16.test.allowEmbedded=true', '-Dbtl16.client.sound.disabled=true', '-cp', classpath, 'vn.ptit.btl16.selftest.AllSelfTests'], 600_000);
}
if (flags.has('--ui')) {
  await check('AuctionSessionsUiSelfTest', java, ['-Dbtl16.test.allowEmbedded=true', '-Dbtl16.client.sound.disabled=true', '--module-path', javafxModules, '--add-modules', 'javafx.controls,javafx.media',
    '-cp', classpath, 'vn.ptit.btl16.selftest.AuctionSessionsUiSelfTest']);
  await check('DialogVisualUiSelfTest', java, ['-Dbtl16.client.sound.disabled=true', '--module-path', javafxModules, '--add-modules', 'javafx.controls,javafx.media',
    '-cp', classpath, 'vn.ptit.btl16.selftest.DialogVisualUiSelfTest']);
  await check('RoomCodeUiSelfTest', java, ['-Dbtl16.client.sound.disabled=true', '--module-path', javafxModules, '--add-modules', 'javafx.controls,javafx.media',
    '-cp', classpath, 'vn.ptit.btl16.selftest.RoomCodeUiSelfTest']);
  await check('RoomManagementUiSelfTest', java, ['-Dbtl16.test.allowEmbedded=true', '-Dbtl16.client.sound.disabled=true', '--module-path', javafxModules, '--add-modules', 'javafx.controls,javafx.media',
    '-cp', classpath, 'vn.ptit.btl16.selftest.RoomManagementUiSelfTest']);
  await check('LoginFeedbackUiSelfTest', java, ['-Dbtl16.client.sound.disabled=true', '--module-path', javafxModules, '--add-modules', 'javafx.controls,javafx.media',
    '-cp', classpath, 'vn.ptit.btl16.selftest.LoginFeedbackUiSelfTest']);
  await check('RoomCenterTabsUiSelfTest', java, ['-Dbtl16.test.allowEmbedded=true', '-Dbtl16.client.sound.disabled=true', '--module-path', javafxModules, '--add-modules', 'javafx.controls,javafx.media',
    '-cp', classpath, 'vn.ptit.btl16.selftest.RoomCenterTabsUiSelfTest']);
  await check('ServerAdminTabsUiSelfTest', java, ['-Dbtl16.test.allowEmbedded=true', '-Dbtl16.client.sound.disabled=true', '--module-path', javafxModules, '--add-modules', 'javafx.controls,javafx.media',
    '-cp', classpath, 'vn.ptit.btl16.selftest.ServerAdminTabsUiSelfTest']);
  for (const test of ['ClientViewportUiSelfTest', 'BidLeadingBannerUiSelfTest', 'AuctionEndingUiSelfTest', 'RevenueRankingUiSelfTest', 'CashFlowAndAnonymousUiSelfTest', 'MarketplaceUiSelfTest', 'ClientSoundsUiSelfTest', 'CompletionUiSelfTest', 'AuctionReceiptUiSelfTest', 'FriendsUiSelfTest', 'FriendsSessionUiSelfTest', 'PersonalNotificationsUiSelfTest', 'AccountScreensUiSelfTest', 'RoomInteractionUiSelfTest', 'RoomProgressUiSelfTest', 'BidExperienceUiSelfTest', 'RoomSocialUiSelfTest', 'LiveAuctionRoomUiSelfTest', 'AuctionButtonsUiSelfTest', 'ServerButtonsUiSelfTest', 'DesktopUiSelfTest', 'ProductCodesUiSelfTest', 'MediaUiSelfTest', 'ProductDeletionUiSelfTest', 'WalletUiSelfTest']) {
    await check(test, java, ['-Dbtl16.test.allowEmbedded=true', '-Dbtl16.client.sound.disabled=true', '--module-path', javafxModules, '--add-modules', 'javafx.controls,javafx.media',
      '-cp', classpath, `vn.ptit.btl16.selftest.${test}`]);
  }
}
if (flags.has('--mysql')) {
  await check('CashFlowRulesMySqlSelfTest', java, ['-cp', classpath, 'vn.ptit.btl16.selftest.CashFlowRulesSelfTest', '--mysql']);
  await check('WalletStorageMigrationMySqlSelfTest', java, ['-cp', classpath, 'vn.ptit.btl16.selftest.WalletStorageMigrationSelfTest', '--mysql']);
  await check('MysqlStorageSelfTest', java, ['-cp', classpath, 'vn.ptit.btl16.selftest.MysqlStorageSelfTest']);
  await check('XamppDatabaseSelfTest', java, ['-cp', classpath, 'vn.ptit.btl16.selftest.XamppDatabaseSelfTest']);
  await check('JdbcProductRestorationMySqlSelfTest', java, ['-cp', classpath, 'vn.ptit.btl16.selftest.JdbcProductRestorationSelfTest', '--mysql']);
}
const failed = results.filter(result => !result.passed);
const missing = [...selectedTests].filter(name => !results.some(result => result.name === name));
if (missing.length) throw new Error(`Unknown or disabled test groups: ${missing.join(', ')}`);
console.log(`[QA] ${results.length - failed.length}/${results.length} groups passed. Logs: ${path.relative(projectRoot, output)}`);
process.exitCode = failed.length ? 1 : 0;
