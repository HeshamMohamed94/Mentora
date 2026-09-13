#!/usr/bin/env node
'use strict';

/**
 * Mentora Design Token Pipeline
 *
 * Reads design-system/design-tokens.json + design-system/themes/theme-{light,dark}.json
 * (the single hand-authored sources of truth) and generates:
 *   - web/styles/tokens.css     — CSS custom properties (semantic + resolvable component layer)
 *   - web/src/lib/design-tokens.generated.ts — typed JS constants for values CSS vars can't
 *     carry cleanly (breakpoints, course grid, icon directional lists, spacing scale numbers).
 *
 * Both outputs are GENERATED — never hand-edited. Re-run `npm run generate` (or `node
 * tools/token-pipeline/generate.js`) after any change to design-tokens.json/themes/*.json.
 *
 * Deviation from ADR-011 recorded as D35 (execution/DECISIONS_LOG.md): ADR-011 names
 * Style Dictionary as the transform tool. This script is a plain Node implementation instead —
 * it satisfies ADR-011's actual requirements (single hand-authored source, generated-never-edited
 * output, deterministic, CI-diffable) without a dependency whose v5 custom-transform API would
 * need to be learned just to implement the same base+tint@opacity resolution this file already
 * does directly. If mobile (Android/iOS) token output is ever needed, this script gains a second
 * output target the same way — see the `--- OUTPUT TARGETS ---` section below.
 *
 * Phase 4 Task 2 (execution/PHASE_4_ANDROID_PLAN.md § 3) added the Android output target:
 *   - mobile/androidApp/src/main/kotlin/com/mentora/android/theme/MentoraTokens.kt — colors
 *     (light/dark), typography scale, radius, elevation, spacing, and icon sizes as Kotlin
 *     constants, per design-to-code/shared/platform-contract.json's "android" mapping table.
 * This is purely additive — see the `--- ANDROID (KOTLIN) OUTPUT TARGET ---` section near the end
 * of this file. It does not read/write anything the three Web-output functions above it touch.
 */

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..', '..');
const DS = path.join(ROOT, 'design-system');
const WEB = path.join(ROOT, 'web');

const tokens = JSON.parse(fs.readFileSync(path.join(DS, 'design-tokens.json'), 'utf8'));
const themeLight = JSON.parse(fs.readFileSync(path.join(DS, 'themes', 'theme-light.json'), 'utf8'));
const themeDark = JSON.parse(fs.readFileSync(path.join(DS, 'themes', 'theme-dark.json'), 'utf8'));

// ---------- helpers ----------

function camelToKebabSegment(seg) {
  return seg.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase();
}

/** "brand.primaryHover" -> "brand-primary-hover"; "space.6" -> "space-6" */
function kebabPath(dotPath) {
  return dotPath.split('.').map(camelToKebabSegment).join('-');
}

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

// ---------- semantic color vars (per theme) ----------

function colorVarLines(themeColorTree) {
  const flat = flattenTree(themeColorTree, '', {});
  return Object.entries(flat).map(([dotPath, value]) => `  --color-${kebabPath(dotPath)}: ${value};`);
}

function stateVarLines(stateOpacity) {
  return Object.entries(stateOpacity).map(
    ([k, v]) => `  --state-${camelToKebabSegment(k)}-opacity: ${v};`
  );
}

// ---------- component-layer alias resolution ----------

/** Map a token dot-path (as it appears inside `{...}` in component.* values) to a CSS var reference. */
function refToVar(refPath) {
  if (refPath.startsWith('color.')) {
    return `var(--color-${kebabPath(refPath.slice('color.'.length))})`;
  }
  if (refPath.startsWith('shape.radius.')) {
    return `var(--radius-${camelToKebabSegment(refPath.slice('shape.radius.'.length))})`;
  }
  if (refPath.startsWith('spacing.scale.space.')) {
    return `var(--space-${refPath.slice('spacing.scale.space.'.length)})`;
  }
  if (refPath.startsWith('state.')) {
    // state.* ref names already end in "Opacity" (e.g. "disabledContainerOpacity"),
    // matching the var names stateVarLines() generates 1:1 — no extra suffix needed.
    return `var(--state-${camelToKebabSegment(refPath.slice('state.'.length))})`;
  }
  if (refPath.startsWith('border.width.')) {
    return `var(--border-width-${camelToKebabSegment(refPath.slice('border.width.'.length))})`;
  }
  if (refPath.startsWith('typography.scale.')) {
    return null; // compound value — components apply the .mtx-text-* utility class instead
  }
  return null;
}

const SINGLE_REF = /^\{([^{}]+)\}$/;
const COMPOSITE_WITH_BASE = /^\{([^{}]+)\}\+\{([^{}]+)\}@\{([^{}]+)\}$/;
const COMPOSITE_NO_BASE = /^\{([^{}]+)\}@\{([^{}]+)\}$/;
const KNOWN_LITERALS = new Set(['transparent', 'none']);

/** Resolve one component.* leaf value into a CSS value expression, or null if not resolvable
 *  (compound typography refs, free-text usage notes like "n/a (no container shape)"). */
function resolveComponentLeaf(raw) {
  if (typeof raw === 'number') return `${raw}px`;
  if (typeof raw !== 'string') return null;
  const v = raw.trim();

  const withBase = v.match(COMPOSITE_WITH_BASE);
  if (withBase) {
    const [, basePath, tintPath, opPath] = withBase;
    const base = refToVar(basePath);
    const tint = refToVar(tintPath);
    const op = refToVar(opPath);
    if (!base || !tint || !op) return null;
    return `color-mix(in srgb, ${tint} calc(${op} * 100%), ${base})`;
  }

  const noBase = v.match(COMPOSITE_NO_BASE);
  if (noBase) {
    const [, tintPath, opPath] = noBase;
    const tint = refToVar(tintPath);
    const op = refToVar(opPath);
    if (!tint || !op) return null;
    return `color-mix(in srgb, ${tint} calc(${op} * 100%), transparent)`;
  }

  const single = v.match(SINGLE_REF);
  if (single) {
    return refToVar(single[1]);
  }

  if (KNOWN_LITERALS.has(v)) return v;
  if (/^#[0-9a-fA-F]{3,8}$/.test(v)) return v;

  return null; // free-text usage note or otherwise-unresolvable value — skip, not guess
}

function componentVarLines(componentTree) {
  const flat = flattenTree(componentTree, '', {});
  const lines = [];
  for (const [dotPath, raw] of Object.entries(flat)) {
    if (dotPath.startsWith('$')) continue; // $description / $note fields
    const resolved = resolveComponentLeaf(raw);
    if (resolved === null) continue;
    lines.push(`  --component-${kebabPath(dotPath)}: ${resolved};`);
  }
  return lines;
}

// ---------- typography ----------

function typographyBaseVarLines() {
  const lines = [];
  for (const [name, style] of Object.entries(tokens.typography.scale)) {
    const slug = kebabPath(name);
    const remSize = style.fontSize / 16;
    const remLine = style.lineHeight / 16;
    const emTracking = style.fontSize ? style.letterSpacing / style.fontSize : 0;
    lines.push(`  --typography-${slug}-font-size: ${remSize}rem;`);
    lines.push(`  --typography-${slug}-line-height: ${remLine}rem;`);
    lines.push(`  --typography-${slug}-font-weight: ${style.fontWeight};`);
    lines.push(`  --typography-${slug}-letter-spacing: ${emTracking}em;`);
  }
  return lines;
}

function typographyResponsiveBlocks() {
  const bp = tokens.breakpoints;
  const tiers = [
    { name: 'mobile', minWidth: null },
    { name: 'tablet', minWidth: bp.tablet.min },
    { name: 'desktop', minWidth: bp.desktop.min },
    { name: 'largeDesktop', minWidth: bp.largeDesktop.min },
  ];
  const responsive = tokens.typography.responsive;
  const responsiveTokenNames = Object.keys(responsive).filter((k) => !k.startsWith('$') && k !== 'note');

  const blocks = [];
  for (const tier of tiers) {
    const decls = [];
    for (const name of responsiveTokenNames) {
      const slug = kebabPath(name);
      const val = responsive[name][tier.name];
      if (!val) continue;
      decls.push(`  --typography-${slug}-font-size: ${val.fontSize / 16}rem;`);
      decls.push(`  --typography-${slug}-line-height: ${val.lineHeight / 16}rem;`);
    }
    if (decls.length === 0) continue;
    if (tier.minWidth === null) {
      blocks.push(`:root {\n${decls.join('\n')}\n}`);
    } else {
      blocks.push(`@media (min-width: ${tier.minWidth}px) {\n  :root {\n${decls.map((d) => '  ' + d).join('\n')}\n  }\n}`);
    }
  }
  return blocks.join('\n\n');
}

function typographyUtilityClasses() {
  const lines = [];
  for (const name of Object.keys(tokens.typography.scale)) {
    const slug = kebabPath(name);
    lines.push(
      `.mtx-text-${slug} {\n` +
        `  font-family: var(--font-family-latin);\n` +
        `  font-size: var(--typography-${slug}-font-size);\n` +
        `  line-height: var(--typography-${slug}-line-height);\n` +
        `  font-weight: var(--typography-${slug}-font-weight);\n` +
        `  letter-spacing: var(--typography-${slug}-letter-spacing);\n` +
        `}`
    );
  }
  // Arabic overrides — LOCALIZATION.md § 4: no tracking on Arabic runs, own font stack,
  // +10% line-height on body.* only.
  lines.push(`html[lang="ar"] [class^="mtx-text-"],\nhtml[lang="ar"] [class*=" mtx-text-"] {\n  font-family: var(--font-family-arabic);\n  letter-spacing: 0;\n}`);
  for (const name of Object.keys(tokens.typography.scale)) {
    if (!name.startsWith('body.')) continue;
    const slug = kebabPath(name);
    lines.push(
      `html[lang="ar"] .mtx-text-${slug} {\n  line-height: calc(var(--typography-${slug}-line-height) * 1.1);\n}`
    );
  }
  return lines.join('\n\n');
}

// ---------- spacing / shape / elevation / border / motion / icon ----------

function spacingVarLines() {
  // Mentora's space.N scale is numerically identical to Tailwind's default spacing scale
  // (N * 4px == Tailwind's N * 0.25rem default multiplier) — verified for every step below.
  // Tailwind utilities (p-4, gap-6, m-8, ...) ARE the space.* tokens; these vars exist for the
  // rare non-utility-class (inline style / non-Tailwind CSS) consumer.
  return Object.entries(tokens.spacing.scale).map(([k, v]) => `  --${kebabPath(k)}: ${v}px;`);
}

function radiusVarLines() {
  return Object.entries(tokens.shape.radius).map(([k, v]) => `  --radius-${camelToKebabSegment(k)}: ${v}px;`);
}

function elevationLevels() {
  // Only numeric levels ("0".."4") carry a {web,...} shape; other keys (e.g. "darkModeNote")
  // are prose notes, not token values.
  return Object.entries(tokens.elevation).filter(([k, v]) => /^\d+$/.test(k) && v && typeof v === 'object');
}

function elevationVarLines() {
  return elevationLevels().map(([k, v]) => `  --elevation-${k}: ${v.web};`);
}

function elevationUtilityClasses() {
  return elevationLevels()
    .map(([k]) => `.mtx-elevation-${k} {\n  box-shadow: var(--elevation-${k});\n}`)
    .join('\n\n');
}

function borderVarLines() {
  return [
    `  --border-width-default: ${tokens.border.width.default}px;`,
    `  --border-width-focus: ${tokens.border.width.focus}px;`,
  ];
}

function motionVarLines() {
  const lines = [];
  for (const [k, v] of Object.entries(tokens.motion.duration)) {
    lines.push(`  --motion-duration-${camelToKebabSegment(k)}: ${v}ms;`);
  }
  for (const [k, v] of Object.entries(tokens.motion.easing)) {
    lines.push(`  --motion-easing-${camelToKebabSegment(k)}: ${v};`);
  }
  return lines;
}

function iconVarLines() {
  return Object.entries(tokens.icon.sizes).map(([k, v]) => `  --icon-${camelToKebabSegment(k)}: ${v}px;`);
}

function fontFamilyVarLines() {
  return [
    `  --font-family-latin: ${tokens.typography.fontFamily.web};`,
    `  --font-family-arabic: ${tokens.typography.fontFamily.webArabic};`,
  ];
}

// ---------- assemble tokens.css ----------

const generatedHeader = `/* GENERATED — DO NOT EDIT.
 * Source: design-system/design-tokens.json, design-system/themes/theme-{light,dark}.json
 * Regenerate with: npm run generate (from tools/token-pipeline/), or node tools/token-pipeline/generate.js
 */\n`;

const lightColorVars = colorVarLines(themeLight.color).concat(stateVarLines(themeLight.stateOpacity));
const darkColorVars = colorVarLines(themeDark.color).concat(stateVarLines(themeDark.stateOpacity));

const themeIndependentVars = []
  .concat(spacingVarLines())
  .concat(radiusVarLines())
  .concat(elevationVarLines())
  .concat(borderVarLines())
  .concat(motionVarLines())
  .concat(iconVarLines())
  .concat(fontFamilyVarLines())
  .concat(typographyBaseVarLines());

const componentVars = componentVarLines(tokens.component);

const css = `${generatedHeader}
:root {
${themeIndependentVars.join('\n')}
}

/* Light theme (default) */
:root,
:root[data-theme="light"] {
${lightColorVars.join('\n')}
}

/* Dark theme via explicit override */
:root[data-theme="dark"] {
${darkColorVars.join('\n')}
}

/* Dark theme via system preference, unless explicitly overridden to light */
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
${darkColorVars.map((l) => '  ' + l).join('\n')}
  }
}

/* Component-layer aliases (design-tokens.json § component.*) — theme-resolved automatically
 * since every value above is itself a var() into the theme-scoped blocks. */
:root {
${componentVars.join('\n')}
}

/* Responsive typography — only display.* and heading.h1/h2 scale by breakpoint (DESIGN_SYSTEM.md § 2.2) */
${typographyResponsiveBlocks()}

/* Typography utility classes — one per typography.scale entry, with Arabic-script overrides
 * per LOCALIZATION.md § 4 (own font stack, zero letter-spacing, +10% line-height for body.*). */
${typographyUtilityClasses()}

/* Elevation utility classes */
${elevationUtilityClasses()}
`;

fs.mkdirSync(path.join(WEB, 'styles'), { recursive: true });
fs.writeFileSync(path.join(WEB, 'styles', 'tokens.css'), css, 'utf8');

// ---------- design-tokens.generated.ts (JS-side constants) ----------

const ts = `// GENERATED — DO NOT EDIT.
// Source: design-system/design-tokens.json
// Regenerate with: npm run generate (from tools/token-pipeline/)

export const breakpoints = ${JSON.stringify(tokens.breakpoints, null, 2)} as const;

export const grid = ${JSON.stringify(tokens.grid, null, 2)} as const;

export const courseGrid = ${JSON.stringify(tokens.courseGrid, null, 2)} as const;

export const spacingScale = ${JSON.stringify(tokens.spacing.scale, null, 2)} as const;

export const iconDirectional = ${JSON.stringify(tokens.icon.directional, null, 2)} as const;

export const touchTarget = ${JSON.stringify(tokens.touchTarget, null, 2)} as const;
`;

fs.mkdirSync(path.join(WEB, 'src', 'lib'), { recursive: true });
fs.writeFileSync(path.join(WEB, 'src', 'lib', 'design-tokens.generated.ts'), ts, 'utf8');

// ---------- tailwind-theme.css (Tailwind v4 CSS-first @theme mapping) ----------
// `@theme inline { --color-X: var(--color-X) }` maps a Tailwind utility namespace key to our
// OWN runtime custom property of the same name (declared in tokens.css). Tailwind then
// generates bg-X/text-X/border-X utilities that reference our runtime var directly, so
// light/dark theme switching (which only changes the runtime var's value) works with zero
// utility-class regeneration. See https://tailwindcss.com/docs/theme (@theme inline).
function tailwindColorInlineLines() {
  const flat = flattenTree(themeLight.color, '', {});
  return Object.keys(flat).map((dotPath) => {
    const varName = `--color-${kebabPath(dotPath)}`;
    return `  ${varName}: var(${varName});`;
  });
}

function tailwindRadiusLines() {
  return Object.keys(tokens.shape.radius).map((k) => {
    const varName = `--radius-${camelToKebabSegment(k)}`;
    return `  ${varName}: var(${varName});`;
  });
}

const tailwindTheme = `${generatedHeader}
@theme inline {
${tailwindColorInlineLines().join('\n')}
${tailwindRadiusLines().join('\n')}
}

/* Mentora's own named breakpoints (design-tokens.json § breakpoints), replacing Tailwind's
 * default sm/md/lg/xl/2xl scale — RESPONSIVE_BEHAVIOR.md's four tiers, mobile-first.
 * Mobile (0-599) is the unprefixed default; tablet:/desktop:/large-desktop: apply upward. */
@theme {
  --breakpoint-*: initial;
  --breakpoint-tablet: ${tokens.breakpoints.tablet.min}px;
  --breakpoint-desktop: ${tokens.breakpoints.desktop.min}px;
  --breakpoint-large-desktop: ${tokens.breakpoints.largeDesktop.min}px;
}
`;

fs.writeFileSync(path.join(WEB, 'styles', 'tailwind-theme.css'), tailwindTheme, 'utf8');

// ---------- ANDROID (KOTLIN) OUTPUT TARGET ----------
// mobile/androidApp/src/main/kotlin/com/mentora/android/theme/MentoraTokens.kt — GENERATED,
// never hand-edited (mobile/androidApp/.../theme/MentoraTheme.kt, hand-authored, consumes this).
// Reuses flattenTree()/elevationLevels() above; introduces the camelPath() inverse of
// kebabPath()/camelToKebabSegment() for Android/iOS naming (dot-path -> camelCase), plus a
// hex/rgba() -> Kotlin ARGB literal converter (Compose Color(Long) takes 0xAARRGGBB).
// Mapping table: design-to-code/shared/platform-contract.json#/android.

const MOBILE_ANDROID_THEME_DIR = path.join(
  ROOT,
  'mobile',
  'androidApp',
  'src',
  'main',
  'kotlin',
  'com',
  'mentora',
  'android',
  'theme'
);

function capitalizeSegment(seg) {
  return seg.length ? seg.charAt(0).toUpperCase() + seg.slice(1) : seg;
}

/** Inverse of kebabPath(): "brand.primaryHover" -> "brandPrimaryHover" (first segment
 *  lowercased, subsequent segments capitalized, concatenated with no separator) — the
 *  Android/iOS naming convention from platform-contract.json#/android/namingConvention. */
function camelPath(dotPath) {
  return dotPath
    .split('.')
    .map((seg, i) => (i === 0 ? seg.charAt(0).toLowerCase() + seg.slice(1) : capitalizeSegment(seg)))
    .join('');
}

/** Kotlin numeric literal, parenthesized if negative (so `.sp`/`.dp` chains, e.g. `(-0.25).sp`,
 *  parse correctly — a bare `-0.25.sp` is a unary-minus-of-`0.25.sp`, not what we want). */
function kotlinNumberLiteral(n) {
  return n < 0 ? `(${n})` : `${n}`;
}

/** "#F8F9FC" -> "0xFFF8F9FC"; "rgba(17,18,23,0.48)" -> "0x7A111217". Compose's Color(Long)
 *  constructor expects 0xAARRGGBB. */
function colorLiteralToKotlinArgb(raw) {
  const v = String(raw).trim();
  if (v.startsWith('#')) {
    const hex = v.slice(1);
    if (hex.length === 6) return `0xFF${hex.toUpperCase()}`;
    if (hex.length === 8) return `0x${hex.toUpperCase()}`;
    throw new Error(`generate.js (android): unexpected hex color length in "${v}"`);
  }
  const m = v.match(/^rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*([\d.]+)\s*)?\)$/);
  if (m) {
    const [, r, g, b, a] = m;
    const alphaByte = a !== undefined ? Math.round(parseFloat(a) * 255) : 255;
    const toHex = (n) => Number(n).toString(16).padStart(2, '0').toUpperCase();
    return `0x${toHex(alphaByte)}${toHex(r)}${toHex(g)}${toHex(b)}`;
  }
  throw new Error(`generate.js (android): cannot convert color "${v}" to a Kotlin ARGB literal`);
}

function androidColorObjectLines(themeColorTree) {
  const flat = flattenTree(themeColorTree, '', {});
  return Object.entries(flat).map(
    ([dotPath, value]) => `    val ${camelPath(dotPath)}: Color = Color(${colorLiteralToKotlinArgb(value)})`
  );
}

/** theme-{light,dark}.json's own `stateOpacity` object has short keys ("hover", "pressed", ...);
 *  design-tokens.json's color.semantic.*.state.* keys already carry the "Opacity" suffix
 *  ("hoverOpacity", ...) — property names here match the LATTER convention for readability. */
function androidStateOpacityObjectLines(stateOpacity) {
  return Object.entries(stateOpacity).map(([k, v]) => `    val ${camelPath(k)}Opacity: Float = ${v}f`);
}

function androidTypographyObjectLines() {
  return Object.entries(tokens.typography.scale).map(([name, style]) => {
    const prop = camelPath(name);
    const fontSize = kotlinNumberLiteral(style.fontSize);
    const lineHeight = kotlinNumberLiteral(style.lineHeight);
    const letterSpacing = kotlinNumberLiteral(style.letterSpacing);
    return (
      `    val ${prop}: MentoraTypographyStyle = MentoraTypographyStyle(\n` +
      `        fontSize = ${fontSize}.sp,\n` +
      `        lineHeight = ${lineHeight}.sp,\n` +
      `        fontWeight = FontWeight(${style.fontWeight}),\n` +
      `        letterSpacing = ${letterSpacing}.sp,\n` +
      `    )`
    );
  });
}

function androidRadiusObjectLines() {
  return Object.entries(tokens.shape.radius).map(([k, v]) => `    val ${camelPath(k)}: Dp = ${v}.dp`);
}

function androidElevationObjectLines() {
  return elevationLevels().map(([k, v]) => `    val level${k}: Dp = ${v.compose_dp}.dp`);
}

function androidSpacingObjectLines() {
  return Object.entries(tokens.spacing.scale).map(([k, v]) => `    val ${camelPath(k)}: Dp = ${v}.dp`);
}

function androidIconSizeObjectLines() {
  return Object.entries(tokens.icon.sizes).map(([k, v]) => `    val ${camelPath(k)}: Dp = ${v}.dp`);
}

const androidGeneratedHeader = `// GENERATED — DO NOT EDIT.
// Source: design-system/design-tokens.json, design-system/themes/theme-{light,dark}.json
// Regenerate with: npm run generate (from tools/token-pipeline/), or node tools/token-pipeline/generate.js
`;

const androidKotlin = `${androidGeneratedHeader}
package com.mentora.android.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One typography.scale entry (design-tokens.json § typography.scale), in Compose-native units. */
data class MentoraTypographyStyle(
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val fontWeight: FontWeight,
    val letterSpacing: TextUnit,
)

/** color.semantic.light (design-tokens.json / theme-light.json) — one property per flattened
 *  dot-path in the color tree. */
object MentoraColorsLight {
${androidColorObjectLines(themeLight.color).join('\n')}
}

/** color.semantic.dark (design-tokens.json / theme-dark.json) — the SAME property set as
 *  MentoraColorsLight, dark-theme values. */
object MentoraColorsDark {
${androidColorObjectLines(themeDark.color).join('\n')}
}

/** theme-light.json's stateOpacity — hover/pressed/focus/disabled interaction-state opacities. */
object MentoraStateOpacityLight {
${androidStateOpacityObjectLines(themeLight.stateOpacity).join('\n')}
}

/** theme-dark.json's stateOpacity — same properties as MentoraStateOpacityLight, dark values. */
object MentoraStateOpacityDark {
${androidStateOpacityObjectLines(themeDark.stateOpacity).join('\n')}
}

/** typography.scale (design-tokens.json) — one MentoraTypographyStyle per scale entry. */
object MentoraTypographyTokens {
${androidTypographyObjectLines().join('\n\n')}
}

/** shape.radius (design-tokens.json), as Dp. */
object MentoraRadiusTokens {
${androidRadiusObjectLines().join('\n')}
}

/** elevation.0..4 (design-tokens.json), using each level's Compose-recommended compose_dp value. */
object MentoraElevationTokens {
${androidElevationObjectLines().join('\n')}
}

/** spacing.scale (design-tokens.json), as Dp. */
object MentoraSpacingTokens {
${androidSpacingObjectLines().join('\n')}
}

/** icon.sizes (design-tokens.json), as Dp. */
object MentoraIconSizeTokens {
${androidIconSizeObjectLines().join('\n')}
}

/** touchTarget.android_dp (design-tokens.json) — minimum touch target size. */
val MentoraTouchTargetMinDp: Dp = ${tokens.touchTarget.android_dp}.dp
`;

fs.mkdirSync(MOBILE_ANDROID_THEME_DIR, { recursive: true });
fs.writeFileSync(path.join(MOBILE_ANDROID_THEME_DIR, 'MentoraTokens.kt'), androidKotlin, 'utf8');

console.log(
  'Generated web/styles/tokens.css, web/styles/tailwind-theme.css, web/src/lib/design-tokens.generated.ts, ' +
    'and mobile/androidApp/src/main/kotlin/com/mentora/android/theme/MentoraTokens.kt'
);
