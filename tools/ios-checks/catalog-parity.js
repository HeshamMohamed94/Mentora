#!/usr/bin/env node
'use strict';

/**
 * Mentora iOS Localization Catalog Parity Checker (Phase 5 Task T7, slice 2)
 *
 * Plain Node, no external dependencies — same precedent as `theme-checks.js` and `assets-check.js`
 * (execution/DECISIONS_LOG.md D35): the source data this checks (a `.xcstrings` JSON file and
 * Android's plain `<string name="...">...</string>` XML resource files) is simple enough to parse
 * without a real XML/plist library on this Windows host.
 *
 * WHAT THIS FILE PROVES. `mobile/iosApp/iosApp/Resources/Localizable.xcstrings` is the full 279-key
 * port of Android's `values/strings.xml` (English) and `values-ar/strings.xml` (Arabic, 278 keys —
 * every key except `app_name`, which is EN-only by design, matching Task T7 slice 1's precedent).
 * This checker is the Windows-runnable half of the parity/specifier lint the plan requires (the other
 * half is `iosAppTests/CatalogParityTests.swift`, an in-target sanity check against the actually
 * COMPILED catalog — this file only ever looks at the source `.xcstrings` JSON).
 *
 * Named in / enforces:
 *   - `execution/PHASE_5_IOS_IMPLEMENTATION_PLAN.md` T7's own scope/acceptance criteria (H1, H2).
 *   - `execution/PHASE_5_IOS_SYSTEM_DESIGN.md § 12` — the localization design this catalog realizes.
 *   - `execution/DECISIONS_LOG.md` D123 — T7 slice 1's plumbing probe, which this slice's catalog
 *     format follows exactly (extractionState as a sibling of localizations, not nested per-language).
 *
 * Exit code 0 = all checks passed. Non-zero = at least one failure, printed to stderr.
 */

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..', '..');
const CATALOG_PATH = path.join(
  ROOT,
  'mobile',
  'iosApp',
  'iosApp',
  'Resources',
  'Localizable.xcstrings'
);
const ANDROID_EN_PATH = path.join(
  ROOT,
  'mobile',
  'androidApp',
  'src',
  'main',
  'res',
  'values',
  'strings.xml'
);
const ANDROID_AR_PATH = path.join(
  ROOT,
  'mobile',
  'androidApp',
  'src',
  'main',
  'res',
  'values-ar',
  'strings.xml'
);
const ERROR_COPY_SWIFT = path.join(
  ROOT,
  'mobile',
  'iosApp',
  'iosApp',
  'Support',
  'ErrorCopy.swift'
);

/** The one key that is deliberately EN-only (no AR translation exists in Android's own resources) —
 *  named once here with its reason, matching theme-checks.js's SANCTIONED_EXCEPTIONS convention. */
const EN_ONLY_KEY = 'app_name';

// ---------- parsing helpers ----------

/** Parses Android's `<string name="X">value</string>` entries out of a `values(-ar)/strings.xml` file.
 *  A simple regex scan is sufficient here (not a general XML parser) because these two specific files
 *  are confirmed (by direct inspection while authoring this checker) to contain no <plurals>,
 *  <string-array>, translatable= attributes, XML entities, or embedded markup — only flat <string
 *  name="...">...</string> leaves, each on its own line, with Android's own `\'` backslash-escape for
 *  apostrophes as the sole escape form present. Returns a Map<name, rawAndroidValue> (value NOT yet
 *  converted to iOS specifier style — callers do that explicitly where needed). */
function parseAndroidStrings(filePath) {
  const src = fs.readFileSync(filePath, 'utf8');
  const re = /<string\s+name="([^"]+)"\s*>([\s\S]*?)<\/string>/g;
  const out = new Map();
  let m;
  while ((m = re.exec(src)) !== null) {
    const name = m[1];
    const value = m[2].replace(/\\'/g, "'");
    out.set(name, value);
  }
  return out;
}

function readCatalog(errors) {
  if (!fs.existsSync(CATALOG_PATH)) {
    errors.push(`Catalog not found at ${relFile(CATALOG_PATH)}.`);
    return null;
  }
  const raw = fs.readFileSync(CATALOG_PATH, 'utf8');
  try {
    return JSON.parse(raw);
  } catch (e) {
    errors.push(`Catalog at ${relFile(CATALOG_PATH)} is not valid JSON: ${e.message}`);
    return null;
  }
}

function relFile(file) {
  return path.relative(ROOT, file).split(path.sep).join('/');
}

/** Extracts a language's value for a key from the catalog's own JSON shape, or undefined if that
 *  language has no localization unit for the key. */
function catalogValue(catalog, key, lang) {
  const entry = catalog.strings[key];
  if (!entry) return undefined;
  const loc = entry.localizations && entry.localizations[lang];
  if (!loc) return undefined;
  return loc.stringUnit ? loc.stringUnit.value : undefined;
}

/** Strips every `%%` literal-percent escape from `value`, then returns the set of positional
 *  specifier indices found, in the given style ('@' for iOS %N$@, 's' for Android %N$s). Stripping
 *  `%%` first prevents it from ever being miscounted as (or corrupting the detection of) a real
 *  specifier. */
function specifierIndexSet(value, style) {
  const stripped = value.split('%%').join('');
  const re = new RegExp(`%(\\d)\\$${style}`, 'g');
  const out = new Set();
  let m;
  while ((m = re.exec(stripped)) !== null) out.add(Number(m[1]));
  return out;
}

function setsEqual(a, b) {
  if (a.size !== b.size) return false;
  for (const v of a) if (!b.has(v)) return false;
  return true;
}

function setToSortedArray(s) {
  return [...s].sort((x, y) => x - y);
}

// ---------- Group A — three-way key parity ----------

function checkGroupA_keyParity(errors, ctx) {
  const { catalog, androidEn, androidAr } = ctx;
  if (!catalog) return;

  const catalogKeys = Object.keys(catalog.strings);
  const catalogEnKeys = new Set(catalogKeys.filter((k) => catalogValue(catalog, k, 'en') !== undefined));
  const catalogArKeys = new Set(catalogKeys.filter((k) => catalogValue(catalog, k, 'ar') !== undefined));
  const androidEnKeys = new Set(androidEn.keys());
  const androidArKeys = new Set(androidAr.keys());

  if (catalogEnKeys.size !== 279) {
    errors.push(`A: catalog EN key count is ${catalogEnKeys.size}, expected exactly 279.`);
  }
  if (androidEnKeys.size !== 279) {
    errors.push(`A: Android EN (values/strings.xml) key count is ${androidEnKeys.size}, expected exactly 279.`);
  }
  if (catalogArKeys.size !== 278) {
    errors.push(`A: catalog AR key count is ${catalogArKeys.size}, expected exactly 278.`);
  }
  if (androidArKeys.size !== 278) {
    errors.push(`A: Android AR (values-ar/strings.xml) key count is ${androidArKeys.size}, expected exactly 278.`);
  }

  const missingFromCatalogEn = [...androidEnKeys].filter((k) => !catalogEnKeys.has(k));
  const extraInCatalogEn = [...catalogEnKeys].filter((k) => !androidEnKeys.has(k));
  if (missingFromCatalogEn.length > 0) {
    errors.push(`A: key(s) present in Android EN but missing from the catalog's EN set: ${missingFromCatalogEn.join(', ')}`);
  }
  if (extraInCatalogEn.length > 0) {
    errors.push(`A: key(s) present in the catalog's EN set but not in Android EN (typo or stray auto-extraction?): ${extraInCatalogEn.join(', ')}`);
  }

  const missingFromCatalogAr = [...androidArKeys].filter((k) => !catalogArKeys.has(k));
  const extraInCatalogAr = [...catalogArKeys].filter((k) => !androidArKeys.has(k));
  if (missingFromCatalogAr.length > 0) {
    errors.push(`A: key(s) present in Android AR but missing from the catalog's AR set: ${missingFromCatalogAr.join(', ')}`);
  }
  if (extraInCatalogAr.length > 0) {
    errors.push(`A: key(s) present in the catalog's AR set but not in Android AR: ${extraInCatalogAr.join(', ')}`);
  }

  const enMinusAr = [...catalogEnKeys].filter((k) => !catalogArKeys.has(k));
  if (!(enMinusAr.length === 1 && enMinusAr[0] === EN_ONLY_KEY)) {
    errors.push(
      `A: catalog's EN-minus-AR key difference is [${enMinusAr.join(', ')}], expected exactly ` +
      `["${EN_ONLY_KEY}"].`
    );
  }
}

// ---------- Group B — specifier parity + conversion correctness ----------

function checkGroupB_specifierParity(errors, ctx) {
  const { catalog, androidEn, androidAr } = ctx;
  if (!catalog) return;

  let count1 = 0;
  let count2 = 0;
  let count3 = 0;

  for (const key of Object.keys(catalog.strings)) {
    const catEn = catalogValue(catalog, key, 'en');
    const catAr = catalogValue(catalog, key, 'ar');
    const andEn = androidEn.get(key);
    const andAr = androidAr.get(key);

    if (catEn === undefined || andEn === undefined) continue; // Group A already reports missing keys

    const catEnSet = specifierIndexSet(catEn, '@');
    const andEnSet = specifierIndexSet(andEn, 's');

    if (!setsEqual(catEnSet, andEnSet)) {
      errors.push(
        `B: key "${key}" EN specifier mismatch — catalog has [${setToSortedArray(catEnSet)}], ` +
        `Android has [${setToSortedArray(andEnSet)}].`
      );
    }

    if (key !== EN_ONLY_KEY) {
      if (catAr === undefined || andAr === undefined) continue; // Group A already reports missing keys
      const catArSet = specifierIndexSet(catAr, '@');
      const andArSet = specifierIndexSet(andAr, 's');

      if (!setsEqual(catArSet, andArSet)) {
        errors.push(
          `B: key "${key}" AR specifier mismatch — catalog has [${setToSortedArray(catArSet)}], ` +
          `Android has [${setToSortedArray(andArSet)}].`
        );
      }
      if (!setsEqual(catEnSet, catArSet)) {
        errors.push(
          `B: key "${key}" EN/AR specifier mismatch within the catalog itself — EN has ` +
          `[${setToSortedArray(catEnSet)}], AR has [${setToSortedArray(catArSet)}].`
        );
      }
    }

    if (catEnSet.has(1)) count1++;
    if (catEnSet.has(2)) count2++;
    if (catEnSet.has(3)) count3++;
  }

  console.log(
    `  (derived specifier distribution: %1$@ used by ${count1} keys, %2$@ additionally used by ` +
    `${count2} keys, %3$@ additionally used by ${count3} keys)`
  );

  if (count1 !== 38) {
    errors.push(`B: expected 38 keys using %1$@, derived ${count1} from the real catalog data.`);
  }
  if (count2 !== 14) {
    errors.push(`B: expected 14 keys additionally using %2$@, derived ${count2} from the real catalog data.`);
  }
  if (count3 !== 2) {
    errors.push(`B: expected 2 keys additionally using %3$@, derived ${count3} from the real catalog data.`);
  }
}

// ---------- Group C — iOS specifier lint (catalog must be fully converted) ----------

/** Every value string the catalog carries, alongside its key + language, for Group C/D's per-value
 *  lint passes. */
function allCatalogValues(catalog) {
  const out = [];
  for (const [key, entry] of Object.entries(catalog.strings)) {
    const localizations = entry.localizations || {};
    for (const [lang, loc] of Object.entries(localizations)) {
      if (loc.stringUnit && typeof loc.stringUnit.value === 'string') {
        out.push({ key, lang, value: loc.stringUnit.value });
      }
    }
  }
  return out;
}

function checkGroupC_iosSpecifierLint(errors, ctx) {
  const { catalog } = ctx;
  if (!catalog) return;

  const bannedPatterns = [
    { re: /%\d\$s/g, label: 'Android-style positional string specifier (%N$s)' },
    { re: /%\d\$d/g, label: 'Android-style positional integer specifier (%N$d)' },
    { re: /(?<!%)%s(?!\$)/g, label: 'bare %s' },
    { re: /(?<!%)%d(?!\$)/g, label: 'bare %d' },
    { re: /%lld/g, label: '%lld' },
    { re: /%f/g, label: '%f' },
  ];

  for (const { key, lang, value } of allCatalogValues(catalog)) {
    const withoutDoublePercent = value.split('%%').join('');

    for (const { re, label } of bannedPatterns) {
      re.lastIndex = 0;
      if (re.test(withoutDoublePercent)) {
        errors.push(`C: key "${key}" (${lang}) contains a banned leftover specifier form: ${label} — value: ${JSON.stringify(value)}`);
      }
    }

    // Non-positional bare "%@" mixed with a positional "%N$@" in the same value is unsafe.
    const hasPositional = /%\d\$@/.test(withoutDoublePercent);
    const hasBarePositionless = /(?<!\$)%@/.test(withoutDoublePercent);
    if (hasPositional && hasBarePositionless) {
      errors.push(`C: key "${key}" (${lang}) mixes a positional %N$@ with a non-positional bare %@ in the same value: ${JSON.stringify(value)}`);
    }

    // %% may only appear alongside at least one real positional specifier — never standalone.
    if (value.includes('%%')) {
      if (!hasPositional) {
        errors.push(`C: key "${key}" (${lang}) contains "%%" with no positional %N$@ specifier alongside it (standalone %% is not expected): ${JSON.stringify(value)}`);
      }
    }

    // After removing every %% and every %N$@, zero bare "%" characters may remain. This is the real
    // catch-all of Group C -- it alone catches %N$f/%lld/bare %@/a lone stray % that the explicit
    // bannedPatterns list above might miss for a form not yet named there. Do not remove it under the
    // assumption bannedPatterns is exhaustive; it is deliberately a backstop, not a duplicate.
    const residual = value.split('%%').join('').replace(/%\d\$@/g, '');
    if (residual.includes('%')) {
      errors.push(`C: key "${key}" (${lang}) has a bare "%" left over after stripping %% and %N$@: ${JSON.stringify(value)}`);
    }
  }
}

// ---------- Group D — structure ----------

function checkGroupD_structure(errors, ctx) {
  const { catalog } = ctx;
  if (!catalog) return;

  if (catalog.sourceLanguage !== 'en') {
    errors.push(`D: catalog sourceLanguage is "${catalog.sourceLanguage}", expected "en".`);
  }

  for (const [key, entry] of Object.entries(catalog.strings)) {
    if (entry.extractionState !== 'manual') {
      errors.push(`D: key "${key}" has extractionState "${entry.extractionState}", expected "manual".`);
    }
    if (entry.variations !== undefined) {
      errors.push(`D: key "${key}" has a "variations" node (plural/device variation) — Android has no plurals; this must never appear.`);
    }
    const localizations = entry.localizations || {};
    for (const [lang, loc] of Object.entries(localizations)) {
      if (loc.variations !== undefined) {
        errors.push(`D: key "${key}" (${lang}) has a "variations" node inside its localization — Android has no plurals; this must never appear.`);
      }
      const value = loc.stringUnit && loc.stringUnit.value;
      if (typeof value !== 'string' || value.length === 0) {
        errors.push(`D: key "${key}" (${lang}) has an empty or missing stringUnit.value.`);
      }
      if (loc.stringUnit && loc.stringUnit.state !== 'translated') {
        errors.push(`D: key "${key}" (${lang}) has stringUnit.state "${loc.stringUnit.state}", expected "translated".`);
      }
    }
  }
}

// ---------- Group E — consumer cross-check ----------

function checkGroupE_consumerCrossCheck(errors, ctx) {
  const { catalog } = ctx;
  if (!catalog) return;

  if (!fs.existsSync(ERROR_COPY_SWIFT)) {
    errors.push(`E: ${relFile(ERROR_COPY_SWIFT)} does not exist.`);
    return;
  }
  const src = fs.readFileSync(ERROR_COPY_SWIFT, 'utf8');

  const allKeysMatch = src.match(/static let allKeys:\s*Set<String>\s*=\s*\[([\s\S]*?)\]/);
  if (!allKeysMatch) {
    errors.push(`E: could not find "static let allKeys: Set<String> = [ ... ]" in ${relFile(ERROR_COPY_SWIFT)}.`);
    return;
  }
  const allKeys = [...allKeysMatch[1].matchAll(/"([^"]+)"/g)].map((m) => m[1]);
  if (allKeys.length === 0) {
    errors.push(`E: parsed zero keys out of ErrorCopy.allKeys in ${relFile(ERROR_COPY_SWIFT)}.`);
  }

  const retryLabelMatch = src.match(/static let retryLabelKey\s*=\s*"([^"]+)"/);
  if (!retryLabelMatch) {
    errors.push(`E: could not find "static let retryLabelKey = \\"...\\"" in ${relFile(ERROR_COPY_SWIFT)}.`);
  }

  const keysToCheck = [...allKeys];
  if (retryLabelMatch) keysToCheck.push(retryLabelMatch[1]);

  const catalogKeySet = new Set(Object.keys(catalog.strings));
  for (const key of keysToCheck) {
    if (!catalogKeySet.has(key)) {
      errors.push(`E: ErrorCopy key "${key}" (from ${relFile(ERROR_COPY_SWIFT)}) does not exist in the catalog.`);
    }
  }
}

// ---------- Group F — value-text fidelity (catalog values must MATCH Android's copy, not just its
//            key/specifier shape) ----------

/** Keys the catalog is deliberately allowed to diverge from Android's literal copy for, with a reason.
 *  Same convention as `theme-checks.js`'s SANCTIONED_EXCEPTIONS. Empty today — every key is a verbatim
 *  port, converted only by the documented %N$s -> %N$@ specifier-style rewrite. */
const SANCTIONED_VALUE_DIVERGENCE = new Set([]);

/** Applies the ONE documented, mechanical Android -> iOS value transform: unescape Android's `\'`
 *  apostrophe escape, then convert every %N$s -> %N$@. Nothing else may differ — this is what makes
 *  Group F a real copy-fidelity check rather than a specifier-shape check (Group B already covers the
 *  latter). Groups A/B never compare value TEXT at all, which is exactly the gap Group F closes: a
 *  copy-paste error (e.g. an AR unit accidentally holding the EN string) preserves key sets and
 *  specifier-index sets perfectly and would otherwise pass every other group silently. */
function expectedIosValue(androidValue) {
  return androidValue.replace(/\\'/g, "'").replace(/%(\d)\$s/g, '%$1$@');
}

function checkGroupF_valueFidelity(errors, ctx) {
  const { catalog, androidEn, androidAr } = ctx;
  if (!catalog) return;

  for (const key of Object.keys(catalog.strings)) {
    if (SANCTIONED_VALUE_DIVERGENCE.has(key)) continue;

    const catEn = catalogValue(catalog, key, 'en');
    const andEn = androidEn.get(key);
    if (catEn !== undefined && andEn !== undefined) {
      const expected = expectedIosValue(andEn);
      if (catEn !== expected) {
        errors.push(
          `F: key "${key}" (en) value does not match Android's copy after the documented %N$s->%N$@ ` +
          `conversion — catalog has ${JSON.stringify(catEn)}, expected ${JSON.stringify(expected)} ` +
          `(from Android's ${JSON.stringify(andEn)}).`
        );
      }
    }

    if (key === EN_ONLY_KEY) continue;
    const catAr = catalogValue(catalog, key, 'ar');
    const andAr = androidAr.get(key);
    if (catAr !== undefined && andAr !== undefined) {
      const expected = expectedIosValue(andAr);
      if (catAr !== expected) {
        errors.push(
          `F: key "${key}" (ar) value does not match Android's copy after the documented %N$s->%N$@ ` +
          `conversion — catalog has ${JSON.stringify(catAr)}, expected ${JSON.stringify(expected)} ` +
          `(from Android's ${JSON.stringify(andAr)}).`
        );
      }
    }
  }
}

// ---------- CHECKS array ----------

const CHECKS = [
  { name: 'A: three-way key parity (catalog EN/AR vs Android EN/AR)', run: checkGroupA_keyParity },
  { name: 'B: specifier parity + conversion correctness', run: checkGroupB_specifierParity },
  { name: 'C: iOS specifier lint (fully converted, zero Android-style leftovers)', run: checkGroupC_iosSpecifierLint },
  { name: 'D: structure (sourceLanguage/extractionState/no variations/non-empty values)', run: checkGroupD_structure },
  { name: 'E: consumer cross-check (ErrorCopy.swift keys exist in the catalog)', run: checkGroupE_consumerCrossCheck },
  { name: 'F: value-text fidelity (catalog copy matches Android verbatim, not just key/specifier shape)', run: checkGroupF_valueFidelity },
];

function runAllChecks() {
  const errors = [];
  const catalog = readCatalog(errors);
  let androidEn = new Map();
  let androidAr = new Map();
  if (!fs.existsSync(ANDROID_EN_PATH)) {
    errors.push(`Android EN strings.xml not found at ${relFile(ANDROID_EN_PATH)}.`);
  } else {
    androidEn = parseAndroidStrings(ANDROID_EN_PATH);
  }
  if (!fs.existsSync(ANDROID_AR_PATH)) {
    errors.push(`Android AR strings.xml not found at ${relFile(ANDROID_AR_PATH)}.`);
  } else {
    androidAr = parseAndroidStrings(ANDROID_AR_PATH);
  }

  const ctx = { catalog, androidEn, androidAr };
  for (const check of CHECKS) check.run(errors, ctx);
  return errors;
}

module.exports = {
  parseAndroidStrings,
  specifierIndexSet,
  checkGroupA_keyParity,
  checkGroupB_specifierParity,
  checkGroupC_iosSpecifierLint,
  checkGroupD_structure,
  checkGroupE_consumerCrossCheck,
  checkGroupF_valueFidelity,
  expectedIosValue,
  runAllChecks,
  CHECKS,
};

if (require.main === module) {
  console.log(`iOS localization catalog parity check: ${CHECKS.length} check group(s) run.`);
  const errors = runAllChecks();
  if (errors.length) {
    console.error(`\n${errors.length} error(s):`);
    for (const e of errors) console.error(`  - ${e}`);
    console.error('\nCheck FAILED.');
    process.exit(1);
  }
  console.log('\nCheck PASSED.');
}
