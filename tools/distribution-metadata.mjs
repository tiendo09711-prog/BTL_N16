import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { cpSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { projectRoot } from './dev-utils.mjs';
import { mainJar } from './package-layout.mjs';

export const distributionGuides = [
  '23_ANH_VIDEO_SAN_PHAM.md', '24_KIEM_THU_TOAN_BO.md', '25_MYSQL_XAMPP_ONLY.md',
  '26_LIVE_AUCTION_ROOM.md', '27_CHAT_VA_KHACH_AN_DANH.md', '28_DIEN_BIEN_PHIEN.md',
  '29_TUONG_TAC_CHAT.md', '30_KET_QUA_PHIEN.md', '31_HOAN_THIEN_CHUNG.md',
  '32_DEMO_VA_NGHIEM_THU.md', '33_KET_QUA_NGHIEM_THU.md', '34_CAP_NHAT_BAN_PHAN_PHOI.md',
  '35_AM_THANH_CLIENT.md',
  'LUAT_DAU_GIA_BTL16.docx'
];
export const distributionMigrations = [
  '00_schema.sql', '03_chat_interactions.sql', '04_auction_receipts.sql', '05_personal_notifications.sql'
];
export const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');

export function sourceFingerprint(root = projectRoot) {
  const files = ['pom.xml'];
  function visit(directory) {
    for (const entry of readdirSync(path.join(root, directory), { withFileTypes: true })) {
      const file = path.posix.join(directory, entry.name);
      if (entry.isDirectory()) visit(file);
      else if (entry.isFile()) files.push(file);
      else throw new Error(`Source must not contain links: ${file}`);
    }
  }
  visit('src/main');
  const hash = createHash('sha256');
  for (const file of files.sort()) hash.update(file).update('\0').update(readFileSync(path.join(root, file))).update('\0');
  return hash.digest('hex');
}

export function writeBuildInfo(directory, role, platform, architecture) {
  const info = {
    schemaVersion: 1, builtAt: new Date().toISOString(), role, platform, architecture,
    jar: mainJar, jarSha256: sha256(readFileSync(path.join(projectRoot, 'target', mainJar))),
    sourceSha256: sourceFingerprint()
  };
  writeFileSync(path.join(directory, 'BUILD_INFO.json'), JSON.stringify(info, null, 2) + '\n');
  return info;
}

export function verifyBuildInfo(info, jarHash, sourceHash, expected) {
  assert.equal(info.schemaVersion, 1, 'unsupported build metadata');
  assert.equal(info.jar, mainJar, 'unexpected application JAR');
  assert.equal(info.jarSha256, jarHash, 'package was built from a different JAR');
  assert.equal(info.sourceSha256, sourceHash, 'source changed: rebuild all distributions with npm run dist:all');
  assert.ok(Number.isFinite(Date.parse(info.builtAt)), 'missing build time');
  for (const [key, value] of Object.entries(expected)) assert.equal(info[key], value, `wrong build ${key}`);
}

export function copyDistributionGuides(directory, includeMigrations = false) {
  mkdirSync(directory, { recursive: true });
  cpSync(path.join(projectRoot, 'docs', '22_HAI_UNG_DUNG_JAVAFX.md'), path.join(directory, 'HUONG_DAN.md'));
  for (const guide of distributionGuides) cpSync(path.join(projectRoot, 'docs', guide), path.join(directory, guide));
  if (includeMigrations) {
    mkdirSync(path.join(directory, 'sql'), { recursive: true });
    for (const migration of distributionMigrations) cpSync(path.join(projectRoot, 'sql', migration), path.join(directory, 'sql', migration));
  }
}
