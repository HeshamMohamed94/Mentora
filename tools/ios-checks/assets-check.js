#!/usr/bin/env node
'use strict';

/**
 * Mentora iOS Asset/Structure Checker
 *
 * Plain Node, no external dependencies — same precedent as tools/token-pipeline/generate.js and
 * tools/design-to-code/validate.js (execution/DECISIONS_LOG.md D35): the asset catalogs this
 * checks are plain JSON directory trees, so a hand-rolled structural check on Windows proves
 * real things about them without needing Xcode.
 *
 * Named in execution/PHASE_5_ACCEPTANCE_CRITERIA.md § 1 ("Asset/structure checker") and
 * execution/PHASE_5_IOS_IMPLEMENTATION_PLAN.md's T2 ("Windows structural checker").
 *
 * Checks performed (Phase 5 T2 — color/shadow only):
 *   1. Exactly 46 semantic color `.colorset` directories exist under
 *      mobile/iosApp/iosApp/Theme/MentoraColors.xcassets (one per theme-light.json#/color leaf),
 *      each with a valid Contents.json containing both an "Any Appearance" (universal, no
 *      `appearances` key) and a "Dark" (`appearances: [{appearance: "luminosity", value: "dark"}]`)
 *      color entry.
 *   2. One `mentoraShadowElevation<N>` colorset per elevation step (design-tokens.json#/elevation),
 *      each with the same Any + Dark structure.
 *
 * T3 (icon set) extends this SAME file with icon-imageset checks (count, template rendering,
 * SVG well-formedness/viewBox, name-parity with Android's MentoraIconName, mirror-set parity) —
 * see the CHECKS array below, which is exported precisely so T3 can push onto it rather than
 * writing a second checker file.
 *
 * Exit code 0 = all checks passed. Non-zero = at least one failure, printed to stderr.
 */

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..', '..');
const DS = path.join(ROOT, 'design-system');
const THEME_DIR = path.join(ROOT, 'mobile', 'iosApp', 'iosApp', 'Theme');
const XCASSETS_DIR = path.join(THEME_DIR, 'MentoraColors.xcassets');

const tokens = JSON.parse(fs.readFileSync(path.join(DS, 'design-tokens.json'), 'utf8'));
const themeLight = JSON.parse(fs.readFileSync(path.join(DS, 'themes', 'theme-light.json'), 'utf8'));

// ---------- helpers (mirrors tools/token-pipeline/generate.js's helpers, read-only here) ----------

function flattenTree(obj, prefix, out) {
  for (const [k, v] of Object.entries(obj)) {
    const key = prefix ? `${prefix}.${k}` : k;
    if (v && typeof v === 'object' && !Array.isArray(v)) {
      flattenTree(v, key, out);
    } else {
      out[key] = v;
    }
  }
  return out;
}

function capitalizeSegment(seg) {
  return seg.length ? seg.charAt(0).toUpperCase() + seg.slice(1) : seg;
}

/** Inverse of kebabPath(): "brand.primaryHover" -> "brandPrimaryHover" — same helper
 *  tools/token-pipeline/generate.js uses for Android/iOS naming. */
function camelPath(dotPath) {
  return dotPath
    .split('.')
    .map((seg, i) => (i === 0 ? seg.charAt(0).toLowerCase() + seg.slice(1) : capitalizeSegment(seg)))
    .join('');
}

function iosColorName(dotPath) {
  return `mentora${capitalizeSegment(camelPath(dotPath))}`;
}

/** Only numeric levels ("0".."4") carry a token shape; other keys (e.g. "darkModeNote") are
 *  prose, not elevation steps. */
function elevationSteps() {
  return Object.keys(tokens.elevation).filter(
    (k) => /^\d+$/.test(k) && tokens.elevation[k] && typeof tokens.elevation[k] === 'object'
  );
}

function readJsonSafe(p, errors, label) {
  try {
    return JSON.parse(fs.readFileSync(p, 'utf8'));
  } catch (e) {
    errors.push(`${label}: could not read/parse ${p} (${e.message})`);
    return null;
  }
}

function isAnyAppearanceEntry(c) {
  return !!c && c.idiom === 'universal' && !c.appearances;
}

function isDarkAppearanceEntry(c) {
  return (
    !!c &&
    c.idiom === 'universal' &&
    Array.isArray(c.appearances) &&
    c.appearances.some((a) => a && a.appearance === 'luminosity' && a.value === 'dark')
  );
}

/** Asserts one `.colorset/Contents.json` has both an Any Appearance and a Dark color entry. */
function checkColorsetContents(colorSetDir, name, errors) {
  const contentsPath = path.join(colorSetDir, 'Contents.json');
  if (!fs.existsSync(contentsPath)) {
    errors.push(`${name}.colorset is missing Contents.json`);
    return;
  }
  const contents = readJsonSafe(contentsPath, errors, `${name}.colorset`);
  if (!contents) return;
  if (!Array.isArray(contents.colors)) {
    errors.push(`${name}.colorset/Contents.json has no "colors" array`);
    return;
  }
  if (!contents.colors.some(isAnyAppearanceEntry)) {
    errors.push(`${name}.colorset/Contents.json is missing an "Any Appearance" color entry`);
  }
  if (!contents.colors.some(isDarkAppearanceEntry)) {
    errors.push(`${name}.colorset/Contents.json is missing a "Dark" (luminosity: dark) color entry`);
  }
}

// ---------- checks (exported, extensible — T3 pushes icon checks onto CHECKS) ----------

/** Exactly 46 semantic color colorsets (theme-light.json#/color leaves), each Any + Dark. */
function checkSemanticColorsets(errors) {
  if (!fs.existsSync(XCASSETS_DIR)) {
    errors.push(`MentoraColors.xcassets does not exist at ${XCASSETS_DIR} — run the token generator first`);
    return;
  }

  const colorFlat = flattenTree(themeLight.color, '', {});
  const expectedNames = Object.keys(colorFlat).map(iosColorName);

  if (expectedNames.length !== 46) {
    errors.push(
      `Expected exactly 46 semantic color dot-paths in theme-light.json#/color, found ${expectedNames.length}`
    );
  }

  for (const name of expectedNames) {
    const colorSetDir = path.join(XCASSETS_DIR, `${name}.colorset`);
    if (!fs.existsSync(colorSetDir)) {
      errors.push(`Missing colorset: ${name}.colorset`);
      continue;
    }
    checkColorsetContents(colorSetDir, name, errors);
  }

  // Catch a stale colorset left behind by a renamed/removed token (the generator itself rebuilds
  // the whole xcassets dir from scratch every run, but this check is also useful standalone).
  const shadowPrefix = 'mentoraShadowElevation';
  const expectedSet = new Set(expectedNames);
  const allColorsetNames = fs
    .readdirSync(XCASSETS_DIR, { withFileTypes: true })
    .filter((d) => d.isDirectory() && d.name.endsWith('.colorset'))
    .map((d) => d.name.replace(/\.colorset$/, ''));
  for (const name of allColorsetNames) {
    if (name.startsWith(shadowPrefix)) continue;
    if (!expectedSet.has(name)) {
      errors.push(`Unexpected colorset present (not a known semantic color dot-path): ${name}.colorset`);
    }
  }
}

/** One mentoraShadowElevation<N> colorset per elevation step, each Any + Dark. */
function checkShadowColorsets(errors) {
  if (!fs.existsSync(XCASSETS_DIR)) {
    errors.push(`MentoraColors.xcassets does not exist at ${XCASSETS_DIR} — run the token generator first`);
    return;
  }

  const steps = elevationSteps();
  for (const step of steps) {
    const name = `mentoraShadowElevation${step}`;
    const colorSetDir = path.join(XCASSETS_DIR, `${name}.colorset`);
    if (!fs.existsSync(colorSetDir)) {
      errors.push(`Missing shadow colorset: ${name}.colorset`);
      continue;
    }
    checkColorsetContents(colorSetDir, name, errors);
  }
}

const CHECKS = [
  { name: 'semantic colorsets (46, Any + Dark)', run: checkSemanticColorsets },
  { name: 'shadow colorsets (one per elevation step, Any + Dark)', run: checkShadowColorsets },
  // T3 (icon set) pushes MentoraIcons.xcassets checks here — imageset count, template-rendering
  // intent, SVG well-formedness/24x24 viewBox, icon-name parity with Android's MentoraIconName,
  // mirror-set parity with Android's autoMirror set.
];

function runAllChecks() {
  const errors = [];
  for (const check of CHECKS) check.run(errors);
  return errors;
}

module.exports = { checkSemanticColorsets, checkShadowColorsets, runAllChecks, CHECKS };

if (require.main === module) {
  const errors = runAllChecks();
  console.log(`iOS asset structural check: ${CHECKS.length} check group(s) run.`);
  if (errors.length) {
    console.error(`\n${errors.length} error(s):`);
    for (const e of errors) console.error(`  - ${e}`);
    console.error('\nCheck FAILED.');
    process.exit(1);
  }
  console.log('\nCheck PASSED.');
}
