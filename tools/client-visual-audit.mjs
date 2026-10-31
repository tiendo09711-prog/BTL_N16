import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const output = path.join(root, 'target', 'client-visual-audit');
mkdirSync(output, { recursive: true });
const require = createRequire(import.meta.url);
const escape = value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
const relative = file => path.relative(root, file).replaceAll('\\', '/');
const asset = file => path.relative(output, file).replaceAll('\\', '/').split('/').map(encodeURIComponent).join('/');
const files = directory => readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
  const file = path.join(directory, entry.name);
  return entry.isDirectory() ? files(file) : [file];
});
const qaFile = path.join(root, 'target', 'qa', 'results.json');
let qa = existsSync(qaFile) ? JSON.parse(readFileSync(qaFile, 'utf8')) : null;
const historyDirectory = path.join(root, 'target', 'qa-focused', 'history');
const historyFiles = existsSync(historyDirectory) ? readdirSync(historyDirectory).filter(file => file.endsWith('.json')).map(file => path.join(historyDirectory, file)) : [];
const rechecks = [...historyFiles, ...['qa-ui', 'qa-focused'].map(directory => path.join(root, 'target', directory, 'results.json'))]
  .filter(existsSync).map(file => JSON.parse(readFileSync(file, 'utf8')))
  .sort((first, second) => Date.parse(first.startedAt) - Date.parse(second.startedAt));
for (const recheck of rechecks) {
  if (!qa) { qa = recheck; }
  else if (Date.parse(recheck.startedAt) >= Date.parse(qa.startedAt)) {
    const latest = new Map(qa.results.map(test => [test.name, test]));
    for (const test of recheck.results) { latest.set(test.name, test); }
    qa.results = [...latest.values()]; qa.uiRecheckStartedAt = recheck.startedAt;
  }
}
const started = qa ? Date.parse(qa.startedAt) : 0;
const testFiles = files(path.join(root, 'src', 'test', 'java')).filter(file => file.endsWith('UiSelfTest.java'));
const tests = testFiles.map(file => ({ file: relative(file), source: readFileSync(file, 'utf8') }));
const sources = files(path.join(root, 'src', 'main', 'java', 'vn', 'ptit', 'btl16', 'client', 'fx')).filter(file => file.endsWith('.java'));
const inventory = sources.map(file => {
  const source = readFileSync(file, 'utf8');
  const name = path.basename(file, '.java');
  const windows = source.split(/\r?\n/).flatMap((line, index) => {
    const match = line.match(/new (Alert|Dialog|TextInputDialog|ChoiceDialog|Stage)\b/);
    return match ? [{ line: index + 1, type: match[1], source: line.trim() }] : [];
  });
  const references = tests.filter(test => new RegExp(`\\b${name}\\b`).test(test.source)).map(test => test.file);
  return { name, file: relative(file), windows, testReferences: references,
    buttons: [...source.matchAll(/new (?:Button|ToggleButton|MenuButton)\s*\(/g)].length,
    labels: [...source.matchAll(/new Label\s*\(|DesktopTheme\.label\s*\(/g)].length };
});
const groups = ['desktop-preview', 'client-viewport-audit', 'live-room-preview', 'bid-experience-preview', 'social-room-preview',
  'wallet-ui-preview', 'media-ui-preview', 'room-center-preview', 'room-code-preview', 'room-management-preview', 'login-feedback-ui'];
const candidates = readdirSync(path.join(root, 'target')).filter(name => name.endsWith('.png'))
  .map(name => path.join(root, 'target', name));
for (const group of groups) {
  const directory = path.join(root, 'target', group);
  if (existsSync(directory)) { candidates.push(...files(directory).filter(file => file.endsWith('.png'))); }
}
for (const group of ['before', 'after']) {
  const directory = path.join(output, group);
  if (existsSync(directory)) { candidates.push(...files(directory).filter(file => file.endsWith('.png'))); }
}
const images = [...new Set(candidates)].sort().map(file => {
  const buffer = readFileSync(file);
  if (buffer.length < 24 || buffer.toString('hex', 0, 8) !== '89504e470d0a1a0a') { throw new Error(`Invalid PNG: ${file}`); }
  const stat = statSync(file);
  const group = file.includes(`${path.sep}before${path.sep}`) ? 'before' : file.includes(`${path.sep}after${path.sep}`) ? 'after' : 'client';
  return { file: relative(file), asset: asset(file), name: path.basename(file, '.png'), group,
    component: path.basename(file).includes('--'), width: buffer.readUInt32BE(16), height: buffer.readUInt32BE(20),
    capturedAt: stat.mtime.toISOString(), current: group === 'before' || stat.mtimeMs >= started };
});
const current = images.filter(image => image.current);
const screens = current.filter(image => !image.component);
const report = { generatedAt: new Date().toISOString(), qa, inventory, images,
  nativeScreens: screens.length, componentCrops: current.filter(image => image.component).length,
  staleImagesExcluded: images.filter(image => !image.current).length };
writeFileSync(path.join(output, 'inventory.json'), JSON.stringify(report, null, 2) + '\n');
const cards = screens.map(image => {
  const crops = current.filter(candidate => candidate.component && candidate.group === image.group && candidate.name.startsWith(`${image.name}--`));
  return `<article class="screen" data-name="${escape(image.name)}" data-group="${image.group}">
    <div class="screen-heading"><h3>${escape(image.name)}</h3><span>${image.group} / ${image.width} × ${image.height}</span></div>
    <button class="preview" data-image="${escape(image.asset)}" aria-label="Inspect ${escape(image.name)}"><img loading="lazy" src="${escape(image.asset)}" alt="${escape(image.name)} — native JavaFX screenshot"></button>
    ${crops.length ? `<details><summary>Header / content / actions (${crops.length})</summary><div class="crops">${crops.map(crop => `<button class="preview" data-image="${escape(crop.asset)}"><img loading="lazy" src="${escape(crop.asset)}" alt="${escape(crop.name)}"></button>`).join('')}</div></details>` : ''}
    <p class="path">${escape(image.file)}</p></article>`;
}).join('\n');
writeFileSync(path.join(output, 'index.html'), `<!doctype html>
<html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>AUCTION / Native Client Visual Audit</title>
<style>
@font-face{font-family:BeVietnam;src:url('../classes/ui/fonts/BeVietnamPro-Regular.ttf')}*{box-sizing:border-box}
body{margin:0;background:radial-gradient(ellipse at 90% 0,#ead7b4,transparent 52%),#f6f1e9;color:#35291d;font-family:BeVietnam,Georgia,serif}
header,main{max-width:1500px;margin:auto;padding:32px}header{padding-top:54px}.eyebrow{font-size:11px;letter-spacing:.22em;color:#936b35}
h1{font:48px Georgia,serif;margin:14px 0;letter-spacing:-1px}header p{color:#7e6b55;max-width:800px;line-height:1.7}.stats{display:flex;gap:12px;margin:28px 0}
.stat{padding:16px 24px;border:1px solid #ddc9a9;border-radius:12px;background:#fffaf1bb}.stat strong{font-size:26px;display:block}.stat span{font-size:11px;color:#806b53}
.toolbar{position:sticky;top:0;z-index:2;display:flex;gap:12px;padding:16px 32px;background:#f7f1e8ee;border-block:1px solid #e1d1b9;backdrop-filter:blur(12px)}
input,select{font:inherit;padding:12px 16px;border:1px solid #d8c2a1;border-radius:8px;background:#fffdf9;color:#513e27}input{flex:1;min-width:0}button{font:inherit;cursor:pointer}
.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:24px}.screen{padding:18px;border:1px solid #e2d4bf;background:#fffdf8;border-radius:16px;box-shadow:0 8px 28px #63421c12;min-width:0;align-self:start}
.screen-heading{display:flex;justify-content:space-between;gap:14px;align-items:baseline;margin:0 0 16px}.screen-heading h3{font-size:13px;margin:0;overflow-wrap:anywhere}.screen-heading span{font-size:10px;color:#957753;white-space:nowrap}
.preview{display:block;padding:0;border:0;background:repeating-conic-gradient(#f7f2ea 0 25%,#eee6d8 0 50%) 50%/16px 16px;width:100%;border-radius:9px;overflow:hidden}
.preview img{display:block;width:100%;height:auto}.path{font:10px ui-monospace,monospace;color:#9d8970;overflow-wrap:anywhere;margin-bottom:0}
details{margin-top:14px;font-size:12px;color:#8b683a}summary{cursor:pointer}.crops{display:grid;gap:12px;margin-top:12px}
dialog{border:1px solid #d6bc93;border-radius:14px;max-width:96vw;max-height:96vh;padding:18px;background:#fffaf1;box-shadow:0 18px 80px #1a120955}
dialog::backdrop{background:#241a0fc4}dialog img{display:block;max-width:88vw;max-height:84vh;object-fit:contain}dialog button{display:block;margin-left:auto;padding:8px 18px;border:0;border-radius:6px;background:#80582f;color:white;margin-bottom:12px}
.inventory{margin-top:36px;padding:24px;border:1px solid #deccae;border-radius:14px;background:#fffaf1}.inventory p{font-size:12px;overflow-wrap:anywhere}.hidden{display:none!important}
@media(max-width:800px){header,main{padding:22px}h1{font-size:34px}.grid{grid-template-columns:1fr}.stats{flex-wrap:wrap}.toolbar{padding:12px 22px}.screen-heading{flex-wrap:wrap}}
</style>
<header><div class="eyebrow">AUCTION / QUALITY WORKSPACE</div><h1>Native client. Every surface matters.</h1>
<p>Actual JavaFX scene snapshots, not a web recreation of the client. Expand a card to inspect its header, body and action bar. Browser checks validate this report only; JavaFX self-tests exercise the desktop controls.</p>
<div class="stats"><div class="stat"><strong>${screens.length}</strong><span>NATIVE SCREENS</span></div><div class="stat"><strong>${report.componentCrops}</strong><span>COMPONENT CROPS</span></div><div class="stat"><strong>${inventory.length}</strong><span>CLIENT FX SOURCE FILES</span></div><div class="stat"><strong>${qa?.results.filter(test => test.passed).length ?? 0} / ${qa?.results.length ?? 0}</strong><span>QA GROUPS PASSED</span></div></div>
<p>Generated ${escape(report.generatedAt)}. ${report.staleImagesExcluded} older images are excluded. Source references are an inventory, not proof that every possible application state has been exercised.</p></header>
<div class="toolbar"><input type="search" id="search" placeholder="Find a screen, dialog or component…" aria-label="Find screen"><select id="group" aria-label="Capture group"><option value="all">All captures</option><option value="before">Before</option><option value="after">After / shared dialogs</option><option value="client">Client workflows</option></select></div>
<main><div class="grid">${cards}</div><section class="inventory"><h2>Source / Test Inventory</h2>${inventory.map(item => `<details><summary>${escape(item.name)} — ${item.windows.length} window/dialog sites · ${item.buttons} button constructions</summary><p>${escape(item.file)}</p><p>${item.windows.map(window => `${escape(window.type)} : ${window.line}`).join(' / ') || 'Embedded component or supporting class'}</p><p>${item.testReferences.map(escape).join('<br>') || 'No direct test reference; inspect parent workflow coverage.'}</p></details>`).join('')}</section></main>
<dialog id="zoom"><button id="close">Close</button><img alt="Full resolution native screenshot"></dialog>
<script>
const search=document.querySelector('#search'),group=document.querySelector('#group'),zoom=document.querySelector('#zoom');
function filter(){const text=search.value.toLowerCase();document.querySelectorAll('.screen').forEach(card=>card.classList.toggle('hidden',!card.dataset.name.toLowerCase().includes(text)||(group.value!=='all'&&card.dataset.group!==group.value)))}
search.addEventListener('input',filter);group.addEventListener('change',filter);
document.querySelectorAll('[data-image]').forEach(button=>button.addEventListener('click',()=>{zoom.querySelector('img').src=button.dataset.image;zoom.showModal()}));
document.querySelector('#close').addEventListener('click',()=>zoom.close());
zoom.addEventListener('click',event=>{if(event.target===zoom)zoom.close()});
</script></html>`);
console.log(`[AUDIT] ${screens.length} screens, ${report.componentCrops} crops, ${inventory.length} client files. ${relative(output)}/index.html`);

if (process.argv.includes('--playwright')) {
  let modulePath = process.env.PLAYWRIGHT_MODULE;
  if (!modulePath) {
    for (const name of ['playwright', 'playwright-core', '@playwright/test']) { try { modulePath = require.resolve(name); break; } catch {} }
  }
  if (!modulePath) {
    for (const bin of (process.env.PATH || '').split(path.delimiter)) {
      const candidate = path.resolve(bin, '..', 'playwright', 'index.mjs');
      if (existsSync(candidate)) { modulePath = candidate; break; }
    }
  }
  if (!modulePath) { throw new Error('Use npm exec --package=playwright -- node tools/client-visual-audit.mjs --playwright, or set PLAYWRIGHT_MODULE to its index.mjs.'); }
  const loaded = await import(pathToFileURL(modulePath).href);
  const { chromium } = loaded.chromium ? loaded : loaded.default;
  const browser = await chromium.launch({ headless: true });
  const verification = { browser: browser.version(), generatedAt: new Date().toISOString(), passed: false, checks: [] };
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(pathToFileURL(path.join(output, 'index.html')).href);
    await page.evaluate(() => document.fonts.ready);
    if (!await page.evaluate(() => document.fonts.check('13px BeVietnam'))) { throw new Error('Report font did not load'); }
    await page.locator('.screen img').evaluateAll(elements => elements.forEach(image => { image.loading = 'eager'; }));
    await page.waitForFunction(() => [...document.querySelectorAll('.screen img')].every(image => image.complete && image.naturalWidth > 0));
    verification.checks.push({ name: 'all-native-images-load', passed: true, images: await page.locator('.screen img').count() });
    await page.screenshot({ path: path.join(output, 'report-desktop.png') });
    await page.locator('#search').fill('room-deposit');
    const matches = await page.locator('.screen:not(.hidden)').count();
    if (matches < 3) { throw new Error('Deposit detail and compact native snapshots are missing'); }
    await page.locator('.screen:not(.hidden) > .preview').first().click();
    if (!await page.locator('#zoom').isVisible()) { throw new Error('Full resolution inspector failed'); }
    await page.keyboard.press('Escape');
    await page.locator('.screen:not(.hidden) summary').first().click();
    if (!await page.locator('.screen:not(.hidden) .crops').first().isVisible()) { throw new Error('Component inspector failed'); }
    verification.checks.push({ name: 'search-and-full-resolution-inspector', passed: true, matches });
    await page.screenshot({ path: path.join(output, 'report-deposit.png'), fullPage: true });
    await page.setViewportSize({ width: 780, height: 900 });
    await page.screenshot({ path: path.join(output, 'report-narrow.png') });
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth);
    if (overflow || errors.length) { throw new Error(`Report overflow=${overflow}, page errors=${errors.join(', ')}`); }
    verification.checks.push({ name: 'narrow-report-layout', passed: true });
    verification.passed = true;
    console.log(`[PASS] Playwright: ${verification.checks.length} report checks; actual desktop actions are tested by JavaFX.`);
  } finally {
    await browser.close(); writeFileSync(path.join(output, 'playwright-results.json'), JSON.stringify(verification, null, 2) + '\n');
  }
}
