#!/usr/bin/env node
'use strict';

/**
 * Mentora Design-to-Code Validator
 *
 * Plain Node, no external dependencies — follows the same precedent as
 * tools/token-pipeline/generate.js (execution/DECISIONS_LOG.md D35): a hand-rolled
 * structural check satisfies this phase's actual requirement (fail loudly on a broken
 * cross-reference) without adding a schema-library dependency (Zod/JSON-Schema) whose
 * value here is mostly the same reference-graph check this file already implements directly.
 *
 * Checks performed (see design-to-code/shared/platform-contract.json#/generatorFailureContract):
 *   1. Every screens/*.json has a unique screenId matching its filename.
 *   2. Every screen's referenceType is one of exact-showcase | approved-pattern | ux-only.
 *   3. Every screen has the required top-level fields.
 *   4. Every screen's componentSequence entries resolve to a real top-level (or dotted
 *      first-segment) key in shared/components.json.
 *   5. Every pattern's usedBy array points to a real screens/*.json file.
 *   6. No duplicate patternId across patterns/*.json.
 *   7. A spot-check of structured (non-prose) token dot-paths inside shared/components.json's
 *      state tables resolves against shared/tokens.json's color.semantic.light namespace.
 *   8. No pattern references another pattern (a simple, current-state circular-reference
 *      guard — patterns/*.json today reference screens only, never each other).
 *
 * Exit code 0 = all checks passed. Non-zero = at least one failure, printed to stderr.
 */

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..', '..');
const DTC = path.join(ROOT, 'design-to-code');

function readJson(p) {
  return JSON.parse(fs.readFileSync(p, 'utf8'));
}

function listJsonFiles(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir).filter((f) => f.endsWith('.json')).sort();
}

const errors = [];
const warnings = [];

// ---------- load shared ----------

const sharedDir = path.join(DTC, 'shared');
const shared = {};
for (const file of listJsonFiles(sharedDir)) {
  shared[path.basename(file, '.json')] = readJson(path.join(sharedDir, file));
}

const REQUIRED_SHARED = ['tokens', 'typography', 'spacing', 'shape', 'elevation', 'components', 'navigation', 'artwork', 'responsive', 'localization', 'platform-contract'];
for (const name of REQUIRED_SHARED) {
  if (!shared[name]) errors.push(`shared/${name}.json is missing`);
}

// ---------- load screens ----------

const screensDir = path.join(DTC, 'screens');
const screenFiles = listJsonFiles(screensDir);
const screens = {};
const seenScreenIds = new Set();
const VALID_REFERENCE_TYPES = new Set(['exact-showcase', 'approved-pattern', 'ux-only']);
const REQUIRED_SCREEN_FIELDS = ['screenId', 'role', 'shell', 'routeIntent', 'referenceType', 'sourceReferences', 'sections', 'componentSequence'];

for (const file of screenFiles) {
  const slug = path.basename(file, '.json');
  const data = readJson(path.join(screensDir, file));
  screens[slug] = data;

  if (data.screenId !== slug) {
    errors.push(`screens/${file}: screenId "${data.screenId}" does not match filename "${slug}"`);
  }
  if (seenScreenIds.has(data.screenId)) {
    errors.push(`screens/${file}: duplicate screenId "${data.screenId}"`);
  }
  seenScreenIds.add(data.screenId);

  for (const field of REQUIRED_SCREEN_FIELDS) {
    if (data[field] === undefined) errors.push(`screens/${file}: missing required field "${field}"`);
  }

  if (data.referenceType && !VALID_REFERENCE_TYPES.has(data.referenceType)) {
    errors.push(`screens/${file}: invalid referenceType "${data.referenceType}" (must be one of ${[...VALID_REFERENCE_TYPES].join(', ')})`);
  }

  if (Array.isArray(data.sourceReferences) && data.sourceReferences.length === 0) {
    errors.push(`screens/${file}: sourceReferences must not be empty — every screen must cite at least one source`);
  }

  if (data.referenceType === 'exact-showcase') {
    const citesShowcase = (data.sourceReferences || []).some((s) => /Mentora Showcase\.dc\.html/.test(s));
    if (!citesShowcase) {
      errors.push(`screens/${file}: referenceType is "exact-showcase" but sourceReferences does not cite the showcase file`);
    }
  }
}

// ---------- component-name resolution ----------

function componentTopLevelKeys(componentsJson) {
  const keys = new Set();
  for (const k of Object.keys(componentsJson)) {
    if (k.startsWith('$')) continue;
    keys.add(k);
  }
  return keys;
}

const componentKeys = shared.components ? componentTopLevelKeys(shared.components) : new Set();

function resolvesAsComponentRef(ref) {
  // Accept "textField", "card.courseCard", "chip.badge", "statePatterns.emptyState", etc.
  // Also accept parenthetical variant notes, e.g. "textField (passwordField variant)".
  const bare = ref.split(' ')[0];
  const firstSegment = bare.split('.')[0];
  return componentKeys.has(firstSegment) || componentKeys.has(bare);
}

for (const [slug, data] of Object.entries(screens)) {
  if (!Array.isArray(data.componentSequence)) continue;
  for (const ref of data.componentSequence) {
    if (typeof ref !== 'string') continue;
    if (!resolvesAsComponentRef(ref)) {
      errors.push(`screens/${slug}.json: componentSequence entry "${ref}" does not resolve to any top-level key in shared/components.json`);
    }
  }
}

// ---------- patterns ----------

const patternsDir = path.join(DTC, 'patterns');
const patternFiles = listJsonFiles(patternsDir);
const patterns = {};
const seenPatternIds = new Set();

for (const file of patternFiles) {
  const data = readJson(path.join(patternsDir, file));
  patterns[path.basename(file, '.json')] = data;

  if (seenPatternIds.has(data.patternId)) {
    errors.push(`patterns/${file}: duplicate patternId "${data.patternId}"`);
  }
  seenPatternIds.add(data.patternId);

  if (Array.isArray(data.usedBy)) {
    for (const usedByRef of data.usedBy) {
      const m = usedByRef.match(/^screens\/([a-z0-9-]+)\.json$/);
      if (!m) {
        errors.push(`patterns/${file}: usedBy entry "${usedByRef}" is not a screens/<id>.json reference`);
        continue;
      }
      if (!screens[m[1]]) {
        errors.push(`patterns/${file}: usedBy references screens/${m[1]}.json, which does not exist`);
      }
    }
  }

  // Circular-pattern guard: a pattern must never reference another pattern in usedBy/derivedFrom.
  const serialized = JSON.stringify(data);
  for (const otherPatternId of Object.keys(patterns)) {
    if (otherPatternId === path.basename(file, '.json')) continue;
    if (serialized.includes(`patterns/${otherPatternId}.json`)) {
      errors.push(`patterns/${file}: references another pattern (patterns/${otherPatternId}.json) — patterns must only reference screens, never each other (circular-reference guard)`);
    }
  }
}

// ---------- structured token dot-path spot-check ----------
// Only validates values that are an EXACT, bare "color.x.y" string (no prose, no "@state" suffix) —
// a deliberately narrow, honest check rather than parsing every prose token reference.

const lightColorKeys = new Set(
  shared.tokens && shared.tokens.color && shared.tokens.color.semantic && shared.tokens.color.semantic.light
    ? Object.keys(shared.tokens.color.semantic.light)
    : []
);

function walkForBareColorRefs(node, pathLabel, cb) {
  if (typeof node === 'string') {
    const m = node.match(/^color\.([a-zA-Z]+\.[a-zA-Z]+)$/);
    if (m) cb(m[1], pathLabel);
    return;
  }
  if (Array.isArray(node)) {
    node.forEach((v, i) => walkForBareColorRefs(v, `${pathLabel}[${i}]`, cb));
    return;
  }
  if (node && typeof node === 'object') {
    for (const [k, v] of Object.entries(node)) walkForBareColorRefs(v, `${pathLabel}.${k}`, cb);
  }
}

if (shared.components) {
  walkForBareColorRefs(shared.components, 'shared/components.json', (dotPath, where) => {
    if (!lightColorKeys.has(dotPath)) {
      warnings.push(`${where}: bare color reference "color.${dotPath}" not found in shared/tokens.json color.semantic.light — verify this is a real token`);
    }
  });
}

// ---------- report ----------

console.log(`Design-to-Code validation: ${screenFiles.length} screens, ${patternFiles.length} patterns, ${Object.keys(shared).length} shared files checked.`);

if (warnings.length) {
  console.log(`\n${warnings.length} warning(s):`);
  for (const w of warnings) console.log(`  - ${w}`);
}

if (errors.length) {
  console.error(`\n${errors.length} error(s):`);
  for (const e of errors) console.error(`  - ${e}`);
  console.error('\nValidation FAILED.');
  process.exit(1);
}

console.log('\nValidation PASSED.');
