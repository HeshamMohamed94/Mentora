#!/usr/bin/env node
'use strict';

/**
 * CI lint check for translation-key parity between `messages/en.json` and `messages/ar.json`.
 * Per `architecture/IMPLEMENTATION_ROADMAP.md` M14 ("lint/CI check for missing translation keys
 * per platform") and `design-system/LOCALIZATION.md` — every key that exists in one locale's
 * message bundle must exist in the other, so a screen never silently falls back to a missing-key
 * placeholder (or raw key string) when rendered in `ar`.
 *
 * Flattens both nested JSON message trees into dotted key paths (e.g. `common.appName`) and
 * diffs the two key sets. Exits non-zero and lists every missing/extra key on either side.
 *
 * Mirrors the Android equivalent: `mobile/androidApp/src/test/kotlin/com/mentora/android/locale/
 * StringsParityTest.kt`.
 */

const fs = require('fs');
const path = require('path');

const MESSAGES_DIR = path.join(__dirname, '..', 'messages');
const EN_FILE = path.join(MESSAGES_DIR, 'en.json');
const AR_FILE = path.join(MESSAGES_DIR, 'ar.json');

/** @returns {string[]} dotted key paths for every leaf value in a nested message object. */
function flattenKeys(obj, prefix) {
  const keys = [];
  for (const key of Object.keys(obj)) {
    const value = obj[key];
    const fullKey = prefix ? `${prefix}.${key}` : key;
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
      keys.push(...flattenKeys(value, fullKey));
    } else {
      keys.push(fullKey);
    }
  }
  return keys;
}

function loadKeys(file) {
  const raw = fs.readFileSync(file, 'utf8');
  const parsed = JSON.parse(raw);
  return new Set(flattenKeys(parsed, ''));
}

const enKeys = loadKeys(EN_FILE);
const arKeys = loadKeys(AR_FILE);

const missingInAr = [...enKeys].filter((k) => !arKeys.has(k)).sort();
const missingInEn = [...arKeys].filter((k) => !enKeys.has(k)).sort();

if (missingInAr.length > 0 || missingInEn.length > 0) {
  console.error('Translation-key parity mismatch between messages/en.json and messages/ar.json:\n');
  if (missingInAr.length > 0) {
    console.error(`  Missing in ar.json (${missingInAr.length}):`);
    for (const key of missingInAr) console.error(`    - ${key}`);
  }
  if (missingInEn.length > 0) {
    console.error(`  Missing in en.json (${missingInEn.length}) (orphan ar.json keys):`);
    for (const key of missingInEn) console.error(`    - ${key}`);
  }
  console.error('\nSee design-system/LOCALIZATION.md and architecture/IMPLEMENTATION_ROADMAP.md M14.');
  process.exit(1);
}

console.log(`Translation-key parity OK: ${enKeys.size} keys match between en.json and ar.json.`);
