#!/usr/bin/env node
'use strict';

/**
 * Mentora Design-to-Code Web Generator
 *
 * Reads design-to-code/shared/*.json + design-to-code/screens/*.json (the shared,
 * platform-neutral source) and writes:
 *   - web/src/lib/design-to-code.generated.ts  — typed constants for the layer ABOVE raw
 *     tokens: navigation shell item lists, the course-artwork motif set, and a screen-id ->
 *     route/referenceType table. Raw CSS custom properties remain tools/token-pipeline/
 *     generate.js's job — this script does not touch web/styles/tokens.css.
 *   - design-to-code/generated/web/resolved-screens.json — a flat, non-code audit-trail dump
 *     of every screen's id/referenceType/route, for tooling that wants the whole set without
 *     reading 24 files.
 *   - design-to-code/generated/web/component-index.json — the flat list of component recipe
 *     names shared/components.json defines, for the same audit-trail reason.
 *
 * GENERATED FILES — DO NOT EDIT MANUALLY. Regenerate with: node tools/design-to-code/generate.js
 * (or `npm run generate:design-to-code` from web/). Always run tools/design-to-code/validate.js
 * first (or let this script's own pre-flight check below do it) — generation must fail loudly on
 * a broken reference, never emit partial/guessed output. Plain Node, no external dependencies —
 * same precedent as tools/token-pipeline/generate.js (execution/DECISIONS_LOG.md D35).
 */

const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const ROOT = path.resolve(__dirname, '..', '..');
const DTC = path.join(ROOT, 'design-to-code');
const WEB = path.join(ROOT, 'web');

function readJson(p) {
  return JSON.parse(fs.readFileSync(p, 'utf8'));
}

function listJsonFiles(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir).filter((f) => f.endsWith('.json')).sort();
}

// ---------- pre-flight: validation must pass before generating ----------

try {
  execFileSync(process.execPath, [path.join(__dirname, 'validate.js')], { stdio: 'inherit' });
} catch {
  console.error('\nGeneration aborted: validation failed (see errors above).');
  process.exit(1);
}

// ---------- load shared + screens ----------

const shared = {};
for (const file of listJsonFiles(path.join(DTC, 'shared'))) {
  shared[path.basename(file, '.json')] = readJson(path.join(DTC, 'shared', file));
}

const screens = {};
for (const file of listJsonFiles(path.join(DTC, 'screens'))) {
  screens[path.basename(file, '.json')] = readJson(path.join(DTC, 'screens', file));
}

// ---------- web/src/lib/design-to-code.generated.ts ----------

const nav = shared.navigation.shells;

function navItemsTs(itemsStructured) {
  return itemsStructured
    .map((item) => `  { key: "${item.key}", href: "${item.href}", icon: "${item.icon}" }`)
    .join(',\n');
}

const artworkMotifsTs = shared.artwork.motifSystem.motifs
  .map((m) => `  { id: "${m.id}", icon: "${m.icon}", gradient: ${JSON.stringify(m.gradient)} }`)
  .join(',\n');

const screenRouteEntries = Object.values(screens)
  .sort((a, b) => a.screenNumber - b.screenNumber)
  .map((s) => `  "${s.screenId}": { routeIntent: ${JSON.stringify(s.routeIntent)}, referenceType: "${s.referenceType}", screenNumber: ${s.screenNumber} }`)
  .join(',\n');

const ts = `// GENERATED — DO NOT EDIT.
// Source: design-to-code/shared/*.json, design-to-code/screens/*.json
// Regenerate with: node tools/design-to-code/generate.js (or npm run generate:design-to-code from web/)
//
// This is the layer ABOVE raw design tokens — raw CSS custom properties are generated
// separately by tools/token-pipeline/generate.js into web/styles/tokens.css and
// web/src/lib/design-tokens.generated.ts. This file never redefines a token value.

export interface NavItem {
  key: string;
  href: string;
  icon: string;
}

/** Authenticated Student Sidebar items — design-to-code/shared/navigation.json#/shells/authenticatedStudent */
export const studentNavItems: readonly NavItem[] = [
${navItemsTs(nav.authenticatedStudent.itemsStructured)},
] as const;

/** Instructor Sidebar items — design-to-code/shared/navigation.json#/shells/instructorWeb */
export const instructorNavItems: readonly NavItem[] = [
${navItemsTs(nav.instructorWeb.itemsStructured)},
] as const;

export interface ArtworkMotif {
  id: string;
  icon: string;
  gradient: string;
}

/** The governed 5-motif course-artwork system — design-to-code/shared/artwork.json#/motifSystem */
export const artworkMotifs: readonly ArtworkMotif[] = [
${artworkMotifsTs},
] as const;

/** Deterministic motif assignment — design-to-code/shared/artwork.json#/motifSystem/assignmentRule */
export function artworkMotifFor(seedOrCategoryId: string): ArtworkMotif {
  let hash = 0;
  for (let i = 0; i < seedOrCategoryId.length; i++) {
    hash = (hash * 31 + seedOrCategoryId.charCodeAt(i)) >>> 0;
  }
  return artworkMotifs[hash % artworkMotifs.length] ?? artworkMotifs[0]!;
}

export type ReferenceType = "exact-showcase" | "approved-pattern" | "ux-only";

export interface ScreenRoute {
  routeIntent: string;
  referenceType: ReferenceType;
  screenNumber: number;
}

/** screenId -> route/referenceType, one entry per design-to-code/screens/*.json (24 screens, Tasks 1-11 scope) */
export const screenRoutes: Readonly<Record<string, ScreenRoute>> = {
${screenRouteEntries},
} as const;
`;

fs.mkdirSync(path.join(WEB, 'src', 'lib'), { recursive: true });
fs.writeFileSync(path.join(WEB, 'src', 'lib', 'design-to-code.generated.ts'), ts, 'utf8');

// ---------- design-to-code/generated/web/*.json (non-code audit-trail artifacts) ----------

const resolvedScreens = Object.values(screens)
  .sort((a, b) => a.screenNumber - b.screenNumber)
  .map((s) => ({
    screenId: s.screenId,
    screenNumber: s.screenNumber,
    role: s.role,
    routeIntent: s.routeIntent,
    referenceType: s.referenceType,
    conflictCount: Array.isArray(s.conflicts) ? s.conflicts.length : 0,
    knownGapCount: Array.isArray(s.knownGaps) ? s.knownGaps.length : 0,
  }));

fs.mkdirSync(path.join(DTC, 'generated', 'web'), { recursive: true });
fs.writeFileSync(
  path.join(DTC, 'generated', 'web', 'resolved-screens.json'),
  JSON.stringify({ generatedNote: 'GENERATED — DO NOT EDIT. Source: design-to-code/screens/*.json', screens: resolvedScreens }, null, 2),
  'utf8'
);

const componentIndex = Object.keys(shared.components).filter((k) => !k.startsWith('$')).sort();
fs.writeFileSync(
  path.join(DTC, 'generated', 'web', 'component-index.json'),
  JSON.stringify({ generatedNote: 'GENERATED — DO NOT EDIT. Source: design-to-code/shared/components.json', components: componentIndex }, null, 2),
  'utf8'
);

console.log('Generated web/src/lib/design-to-code.generated.ts, design-to-code/generated/web/resolved-screens.json, design-to-code/generated/web/component-index.json');
