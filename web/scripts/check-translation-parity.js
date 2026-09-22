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
 * diffs the two key sets, plus flags any key whose value is blank (empty string) in either
 * locale. Exits non-zero and lists every missing/extra/blank key.
 *
 * Partial mirror of the Android equivalent: `mobile/androidApp/src/test/kotlin/com/mentora/android/
 * locale/StringsParityTest.kt` — that test additionally checks format-specifier parity (e.g. a
 * string with `{count}` in English must also have `{count}` in Arabic). This script does NOT do
 * format-specifier/ICU-placeholder parity checking; that's a known, documented gap, not silently
 * assumed equivalent.
 */

const fs = require('fs');
const path = require('path');

const MESSAGES_DIR = path.join(__dirname, '..', 'messages');
const EN_FILE = path.join(MESSAGES_DIR, 'en.json');
const AR_FILE = path.join(MESSAGES_DIR, 'ar.json');

/** @returns {[string, string][]} dotted-key/value pairs for every leaf value in a nested message object. */
function flattenEntries(obj, prefix) {
  const entries = [];
  for (const key of Object.keys(obj)) {
    const value = obj[key];
    const fullKey = prefix ? `${prefix}.${key}` : key;
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
      entries.push(...flattenEntries(value, fullKey));
    } else {
      entries.push([fullKey, value]);
    }
  }
  return entries;
}

function loadEntries(file) {
  const raw = fs.readFileSync(file, 'utf8');
  const parsed = JSON.parse(raw);
  return new Map(flattenEntries(parsed, ''));
}

const enEntries = loadEntries(EN_FILE);
const arEntries = loadEntries(AR_FILE);
const enKeys = new Set(enEntries.keys());
const arKeys = new Set(arEntries.keys());

const missingInAr = [...enKeys].filter((k) => !arKeys.has(k)).sort();
const missingInEn = [...arKeys].filter((k) => !enKeys.has(k)).sort();

// Blank-value check: a key present in both locales but with an empty-string value in either one
// is just as broken as a missing key (renders nothing) — flagged here even though it's not a
// key-set mismatch. Does NOT check format-specifier/ICU-placeholder parity (see header comment).
const blankValues = [];
for (const key of enKeys) {
  if (arKeys.has(key)) {
    if (enEntries.get(key) === '') blankValues.push(`${key} (en.json)`);
    if (arEntries.get(key) === '') blankValues.push(`${key} (ar.json)`);
  }
}
blankValues.sort();

if (missingInAr.length > 0 || missingInEn.length > 0 || blankValues.length > 0) {
  console.error('Translation-key parity mismatch between messages/en.json and messages/ar.json:\n');
  if (missingInAr.length > 0) {
    console.error(`  Missing in ar.json (${missingInAr.length}):`);
    for (const key of missingInAr) console.error(`    - ${key}`);
  }
  if (missingInEn.length > 0) {
    console.error(`  Missing in en.json (${missingInEn.length}) (orphan ar.json keys):`);
    for (const key of missingInEn) console.error(`    - ${key}`);
  }
  if (blankValues.length > 0) {
    console.error(`  Blank value (${blankValues.length}):`);
    for (const key of blankValues) console.error(`    - ${key}`);
  }
  console.error('\nSee design-system/LOCALIZATION.md and architecture/IMPLEMENTATION_ROADMAP.md M14.');
  process.exit(1);
}

console.log(`Translation-key parity OK: ${enKeys.size} keys match between en.json and ar.json.`);
