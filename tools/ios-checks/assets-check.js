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
 * T3 (icon set) extends this SAME file with icon-imageset checks:
 *   3. Exactly 42 `.imageset` directories exist under
 *      mobile/iosApp/iosApp/Theme/MentoraIcons.xcassets, one per icon.
 *   4. Every `.imageset/Contents.json` is valid JSON with template-rendering intent set.
 *   5. Every `.imageset/<name>.svg` is well-formed XML with a 24x24 viewBox.
 *   6. The icon-name set (imageset directory names) is exactly equal to Android's
 *      `MentoraIconName` 42 entries (mobile/androidApp/.../ui/components/MentoraIcons.kt),
 *      name-translated PascalCase -> camelCase.
 *   7. The RTL-mirror set encoded in Theme/MentoraIcon.swift (parsed structurally) is exactly
 *      Android's `autoMirror = true` set — `{arrowForward, arrowBack}` — and nothing else.
 *
 * See the CHECKS array below, which is exported precisely so T3 could push onto it rather than
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
const ICONS_XCASSETS_DIR = path.join(THEME_DIR, 'MentoraIcons.xcassets');
const MENTORA_ICON_SWIFT = path.join(THEME_DIR, 'MentoraIcon.swift');
const ANDROID_ICONS_KT = path.join(
  ROOT,
  'mobile',
  'androidApp',
  'src',
  'main',
  'kotlin',
  'com',
  'mentora',
  'android',
  'ui',
  'components',
  'MentoraIcons.kt'
);

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

// ---------- T3 helpers (icon set) ----------

/** Lowercases just the first character: "Dashboard" -> "dashboard", matching Android's PascalCase
 *  enum entries to web/Android's camelCase icon names (both platforms only differ by that). */
function firstCharLower(s) {
  return s.length ? s.charAt(0).toLowerCase() + s.slice(1) : s;
}

/** Parses `enum class MentoraIconName { ... }` out of Android's MentoraIcons.kt and returns the
 *  42 icon names translated to camelCase (e.g. "ArrowForward" -> "arrowForward"). Structural
 *  (parses the actual enum body), not a hardcoded literal list, so it stays in sync with Android
 *  if that file ever changes. */
function androidIconNames(errors) {
  if (!fs.existsSync(ANDROID_ICONS_KT)) {
    errors.push(`Android MentoraIcons.kt not found at ${ANDROID_ICONS_KT}`);
    return [];
  }
  const src = fs.readFileSync(ANDROID_ICONS_KT, 'utf8');
  const match = src.match(/enum class MentoraIconName\s*\{([\s\S]*?)\}/);
  if (!match) {
    errors.push('Could not find "enum class MentoraIconName { ... }" in Android MentoraIcons.kt');
    return [];
  }
  return match[1]
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0)
    .map(firstCharLower);
}

/** Parses the `flipsForRightToLeftLayoutDirection` switch in Theme/MentoraIcon.swift structurally
 *  (walks the `case ...: return true` arm) rather than grepping for icon names anywhere in the
 *  file, so unrelated occurrences of "arrowForward"/"arrowBack" elsewhere can't false-positive. */
function swiftMirrorSet(errors) {
  if (!fs.existsSync(MENTORA_ICON_SWIFT)) {
    errors.push(`MentoraIcon.swift not found at ${MENTORA_ICON_SWIFT}`);
    return [];
  }
  const src = fs.readFileSync(MENTORA_ICON_SWIFT, 'utf8');
  const propMatch = src.match(
    /var flipsForRightToLeftLayoutDirection[\s\S]*?switch self \{([\s\S]*?)\n\s*\}\s*\n\s*\}/
  );
  if (!propMatch) {
    errors.push(
      'Could not find "var flipsForRightToLeftLayoutDirection" switch statement in MentoraIcon.swift'
    );
    return [];
  }
  const switchBody = propMatch[1];
  // The arm that returns true is the mirror set; everything else (a "default: return false", or
  // an explicit false arm) is not.
  const armMatch = switchBody.match(/case\s+([^:]+):\s*\n\s*return true/);
  if (!armMatch) {
    errors.push(
      '"flipsForRightToLeftLayoutDirection" switch has no "case ...: return true" arm'
    );
    return [];
  }
  return armMatch[1]
    .split(',')
    .map((s) => s.trim().replace(/^\./, ''))
    .filter((s) => s.length > 0);
}

/** Minimal hand-rolled well-formedness check for a single SVG file: balanced/nested tags via a
 *  stack (self-closing tags never pushed), no dangling `<`/`>` mismatch. Not a full XML/DTD
 *  validator, but genuinely walks tag structure rather than only regex-matching content. */
function checkSvgWellFormed(svgPath, name, errors) {
  let src;
  try {
    src = fs.readFileSync(svgPath, 'utf8');
  } catch (e) {
    errors.push(`${name}: could not read ${svgPath} (${e.message})`);
    return;
  }

  const withoutComments = src.replace(/<!--[\s\S]*?-->/g, '');
  const tagPattern = /<([^>]+)>/g;
  const stack = [];
  let match;
  let sawRoot = false;
  while ((match = tagPattern.exec(withoutComments)) !== null) {
    const raw = match[1].trim();
    if (raw.startsWith('?') || raw.startsWith('!')) continue; // processing instruction / doctype
    if (raw.startsWith('/')) {
      const tagName = raw.slice(1).trim().split(/\s/)[0];
      const top = stack.pop();
      if (top !== tagName) {
        errors.push(
          `${name}: mismatched closing tag </${tagName}> (expected </${top || '(nothing open)'}>)`
        );
        return;
      }
      continue;
    }
    const selfClosing = raw.endsWith('/');
    const body = selfClosing ? raw.slice(0, -1).trim() : raw;
    const tagName = body.split(/\s/)[0];
    if (tagName === 'svg') sawRoot = true;
    if (!selfClosing) stack.push(tagName);
  }
  if (stack.length > 0) {
    errors.push(`${name}: unclosed tag(s) [${stack.join(', ')}] — not well-formed XML`);
    return;
  }
  if (!sawRoot) {
    errors.push(`${name}: no root <svg> element found`);
    return;
  }

  const viewBoxMatch = src.match(/viewBox="([^"]+)"/);
  if (!viewBoxMatch) {
    errors.push(`${name}: <svg> has no viewBox attribute`);
    return;
  }
  const normalized = viewBoxMatch[1].trim().replace(/\s+/g, ' ');
  if (normalized !== '0 0 24 24' && !normalized.endsWith('24 24')) {
    errors.push(`${name}: viewBox is "${normalized}", expected "0 0 24 24" (or ending "24 24")`);
  }
}

/** Exactly 42 icon imagesets exist under MentoraIcons.xcassets. */
function checkIconImagesetCount(errors) {
  if (!fs.existsSync(ICONS_XCASSETS_DIR)) {
    errors.push(`MentoraIcons.xcassets does not exist at ${ICONS_XCASSETS_DIR}`);
    return;
  }
  const imagesetDirs = fs
    .readdirSync(ICONS_XCASSETS_DIR, { withFileTypes: true })
    .filter((d) => d.isDirectory() && d.name.endsWith('.imageset'))
    .map((d) => d.name.replace(/\.imageset$/, ''));
  if (imagesetDirs.length !== 42) {
    errors.push(
      `Expected exactly 42 .imageset directories under MentoraIcons.xcassets, found ${imagesetDirs.length}`
    );
  }
}

/** Every imageset's Contents.json is valid JSON with template-rendering intent, and its SVG is
 *  well-formed XML with a 24x24 viewBox. */
function checkIconContentsAndSvg(errors) {
  if (!fs.existsSync(ICONS_XCASSETS_DIR)) {
    errors.push(`MentoraIcons.xcassets does not exist at ${ICONS_XCASSETS_DIR}`);
    return;
  }
  const imagesetDirs = fs
    .readdirSync(ICONS_XCASSETS_DIR, { withFileTypes: true })
    .filter((d) => d.isDirectory() && d.name.endsWith('.imageset'));

  for (const dirent of imagesetDirs) {
    const name = dirent.name.replace(/\.imageset$/, '');
    const dir = path.join(ICONS_XCASSETS_DIR, dirent.name);

    const contentsPath = path.join(dir, 'Contents.json');
    if (!fs.existsSync(contentsPath)) {
      errors.push(`${name}.imageset is missing Contents.json`);
    } else {
      const contents = readJsonSafe(contentsPath, errors, `${name}.imageset`);
      if (contents) {
        if (contents.properties?.['template-rendering-intent'] !== 'template') {
          errors.push(
            `${name}.imageset/Contents.json is missing properties.template-rendering-intent = "template"`
          );
        }
        const images = Array.isArray(contents.images) ? contents.images : [];
        if (!images.some((img) => img && img.filename === `${name}.svg` && img.idiom === 'universal')) {
          errors.push(
            `${name}.imageset/Contents.json has no universal-idiom image entry for "${name}.svg"`
          );
        }
      }
    }

    const svgPath = path.join(dir, `${name}.svg`);
    if (!fs.existsSync(svgPath)) {
      errors.push(`${name}.imageset is missing ${name}.svg`);
      continue;
    }
    checkSvgWellFormed(svgPath, `${name}.imageset`, errors);
  }
}

/** The icon-name set (imageset directory names) equals Android's MentoraIconName 42 entries,
 *  exactly — no more, no fewer, same names. */
function checkIconNameParity(errors) {
  if (!fs.existsSync(ICONS_XCASSETS_DIR)) {
    errors.push(`MentoraIcons.xcassets does not exist at ${ICONS_XCASSETS_DIR}`);
    return;
  }
  const imagesetNames = fs
    .readdirSync(ICONS_XCASSETS_DIR, { withFileTypes: true })
    .filter((d) => d.isDirectory() && d.name.endsWith('.imageset'))
    .map((d) => d.name.replace(/\.imageset$/, ''));

  const androidNames = androidIconNames(errors);
  if (androidNames.length === 0) return; // androidIconNames already pushed an error

  const imagesetSet = new Set(imagesetNames);
  const androidSet = new Set(androidNames);

  const missingFromImagesets = androidNames.filter((n) => !imagesetSet.has(n));
  const extraInImagesets = imagesetNames.filter((n) => !androidSet.has(n));

  if (missingFromImagesets.length > 0) {
    errors.push(
      `Icon names present in Android's MentoraIconName but missing from MentoraIcons.xcassets: ${missingFromImagesets.join(', ')}`
    );
  }
  if (extraInImagesets.length > 0) {
    errors.push(
      `Icon imagesets present that are not in Android's MentoraIconName: ${extraInImagesets.join(', ')}`
    );
  }
}

/** The mirror set encoded in MentoraIcon.swift equals Android's exact autoMirror set
 *  (`arrowForward`, `arrowBack`) and nothing else. */
function checkIconMirrorParity(errors) {
  const mirrorSet = swiftMirrorSet(errors);
  if (mirrorSet.length === 0 && errors.length > 0) return; // swiftMirrorSet already pushed an error

  const expected = ['arrowForward', 'arrowBack'];
  const mirrorSetSorted = [...mirrorSet].sort();
  const expectedSorted = [...expected].sort();
  const matches =
    mirrorSetSorted.length === expectedSorted.length &&
    mirrorSetSorted.every((v, i) => v === expectedSorted[i]);

  if (!matches) {
    errors.push(
      `MentoraIcon.swift mirror set is [${mirrorSet.join(', ')}], expected exactly [${expected.join(', ')}]`
    );
  }
}

const CHECKS = [
  { name: 'semantic colorsets (46, Any + Dark)', run: checkSemanticColorsets },
  { name: 'shadow colorsets (one per elevation step, Any + Dark)', run: checkShadowColorsets },
  { name: 'icon imagesets (42 exist)', run: checkIconImagesetCount },
  { name: 'icon Contents.json + SVG well-formedness/viewBox', run: checkIconContentsAndSvg },
  { name: 'icon-name parity with Android MentoraIconName', run: checkIconNameParity },
  { name: 'icon RTL-mirror-set parity with Android autoMirror set', run: checkIconMirrorParity },
];

function runAllChecks() {
  const errors = [];
  for (const check of CHECKS) check.run(errors);
  return errors;
}

module.exports = {
  checkSemanticColorsets,
  checkShadowColorsets,
  checkIconImagesetCount,
  checkIconContentsAndSvg,
  checkIconNameParity,
  checkIconMirrorParity,
  runAllChecks,
  CHECKS,
};

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
