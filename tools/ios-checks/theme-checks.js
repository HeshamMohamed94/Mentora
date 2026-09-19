#!/usr/bin/env node
'use strict';

/**
 * Mentora iOS Theme/Token Completion-Gate Checker (Phase 5 Task T6, sub-slice 3c)
 *
 * Plain Node, no external dependencies — same precedent as `tools/token-pipeline/generate.js` and
 * `tools/ios-checks/assets-check.js` (execution/DECISIONS_LOG.md D35): this checker's whole job is
 * source-TEXT policy over plain `.swift` files, which a hand-rolled Node script can prove real things
 * about without a Swift compiler on this Windows host.
 *
 * This is a NEW file, not an extension of `assets-check.js` — different domain (source-text/grep
 * policy over `.swift` files, vs. asset-catalog JSON/SVG structure) and a different CI role (this one
 * is meant to run FIRST, before any toolchain is even installed; see `ios-ci.yml`'s ordering comment).
 *
 * Named in / enforces:
 *   - `execution/DECISIONS_LOG.md` D35 — plain-Node-no-deps precedent for this whole `tools/` family.
 *   - `execution/DECISIONS_LOG.md` D121 — where T6 slice 3b first recorded the 5 theme-root gate
 *     patterns (`preferredColorScheme(`, `environment(\.locale`/`transformEnvironment(\.locale`,
 *     `environment(\.layoutDirection`/`transformEnvironment(\.layoutDirection`, `.mentoraTheme(`,
 *     `Locale(identifier: "ar`) this file now automates as Check Group C.
 *   - `execution/PHASE_5_IOS_IMPLEMENTATION_PLAN.md` T6's own "Completion gate" section — "no raw
 *     `.system(size:)` and no raw system color outside `Theme/` (grep test) — `Font.system(size:
 *     weight:)` appears exactly once ... and nowhere else; no `UIFontMetrics` anywhere" — Check
 *     Groups A and B.
 *   - `design-to-code/shared/platform-contract.json#/ios/colorMapping/systemColorRule` (line ~96):
 *     "Never substitute a raw UIKit/SwiftUI system color ... for a semantic token slot — every one of
 *     the dot-paths above must resolve through its mentora-prefixed asset."
 *
 * THIS IS A REGRESSION GUARD FOR COMMON FORMS OF RAW-COLOR/FONT MISUSE, NOT AN EXHAUSTIVE ONE. A grep
 * gate over source text cannot catch every possible Swift expression shape that ultimately produces a
 * raw system color or an unscaled font — e.g. `let c: Color = .blue` (assigned through a typed `let`,
 * never appearing as `Color.blue` or `.blue` inside a recognized modifier call) or a computed property
 * that returns `.gray` (the raw color never appears at a call site this checker scans for) both slip
 * past every check below. Do not read a clean run of this script as proof no raw color/font exists
 * anywhere in the target — only that none of the COMMON, textually-recognizable forms do.
 *
 * ---------------------------------------------------------------------------------------------------
 * WHY A NAIVE TEXT-REGEX CHECKER IS WRONG ON THIS TREE TODAY (the finding that shaped this file's
 * design — see DECISIONS_LOG.md D122 for the concrete numbers actually observed):
 * `.system(size:` matches 4 times in `Theme/MentoraTypography.swift`'s RAW text, but only 1 of those
 * is real code — the other 3 are doc comments. `UIFontMetrics` matches 4 times in raw text, all in
 * comments. `Font.custom(` matches 2 times in raw text ACROSS THE WHOLE `mobile/iosApp/iosApp/**`
 * tree (1 in a `MentoraTypography.swift` comment, 1 in a `MentoraTokens.swift` comment) — zero real
 * code. A checker that regexes raw source text directly would report false violations (A1: "found 4,
 * expected 1"; A2: "found 4, expected 0") on a tree the architect independently verified is clean.
 * Every check in this file therefore runs against COMMENT-STRIPPED text (see `stripSwiftComments`
 * below), never raw text — except Check F1, which is deliberately about the stripper's own blind spot
 * and must see raw text. (Exact current line numbers for the examples above are NOT reproduced here
 * deliberately — they drift with every unrelated edit to `MentoraTypography.swift`; the COUNTS are
 * the number this design actually rests on. See `DECISIONS_LOG.md` D122 for the numbers as measured
 * at the time this file was written.)
 * ---------------------------------------------------------------------------------------------------
 *
 * Exit code 0 = all checks passed. Non-zero = at least one failure, printed to stderr.
 */

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..', '..');
const PROD_DIR = path.join(ROOT, 'mobile', 'iosApp', 'iosApp');
const TESTS_DIRS = [
  path.join(ROOT, 'mobile', 'iosApp', 'iosAppTests'),
  path.join(ROOT, 'mobile', 'iosApp', 'iosAppUITests'),
];
const THEME_DIR = path.join(PROD_DIR, 'Theme');
const TYPOGRAPHY_FILE = path.join(THEME_DIR, 'MentoraTypography.swift');
const THEME_ROOT_FILE = path.join(THEME_DIR, 'MentoraTheme.swift');
const COLOR_MENTORA_FILE = path.join(THEME_DIR, 'Color+Mentora.swift');
const GALLERY_FILE = path.join(THEME_DIR, 'MentoraTokenGallery.swift');
const APP_FILE = path.join(PROD_DIR, 'MentoraApp.swift');

/**
 * Every deliberate exception to a rule below, named once here with its reason, instead of scattered
 * inline in regexes/comments. Read this before "fixing" a check that looks too lenient.
 */
const SANCTIONED_EXCEPTIONS = {
  systemSizeCallSite:
    `Theme/MentoraTypography.swift's MentoraFontModifier.body is the ONE legitimate ".system(size:" ` +
    `call site (Check A1) — the single place Font.system(size:weight:) is composed from a @ScaledMetric ` +
    `value, per PHASE_5_IOS_SYSTEM_DESIGN.md § 15.1.`,
  colorClearExcluded:
    `Color.clear is deliberately EXCLUDED from the forbidden-color palette (Check Group B) — it carries ` +
    `no visual identity and has no semantic-token equivalent; banning it would push authors toward worse ` +
    `patterns like .opacity(0) hacks.`,
  galleryMentoraThemeCallSites:
    `Theme/MentoraTokenGallery.swift's ".mentoraTheme(" call sites (one per #Preview) are exempt from ` +
    `Check C4's "exactly one production call site" rule — the gallery is a sanctioned SECOND call site, ` +
    `routing every preview variation through the real .mentoraTheme(theme:locale:) entry point rather ` +
    `than injecting environment values directly.`,
  colorStringLiteralGeneratedOnly:
    `Color("...") string-literal construction (Check B4) is allowed only in the GENERATED ` +
    `Theme/Color+Mentora.swift — every other file must go through that file's static accessors, never a ` +
    `raw asset-name string.`,
};

// ---------- the comment stripper ----------

/**
 * Strips Swift `//` line comments and slash-star block comments (which Swift allows NESTING, so
 * nesting depth is tracked) from `src`, replacing stripped characters with spaces and leaving every
 * newline intact — so line numbers computed against the returned string are identical to line numbers
 * in the original source. String literal CONTENTS are preserved verbatim (both `"..."` with
 * backslash-escape handling, and `"""..."""` triple-quoted strings), so a `//` or a block-comment
 * opener appearing inside a string literal is never mistaken for the start of a comment.
 *
 * Deliberately does NOT support Swift raw string literals (`#"..."#`, `#"""..."""#`, etc.) — see Check
 * F1 below, which fails loudly (rather than silently mis-scanning) if `#"` is ever found anywhere in
 * the scanned file set. Extend this function first if that check ever fires.
 *
 * Also does not special-case string interpolation (`\(...)`) containing a nested string literal with
 * its own quotes — an extremely rare shape this codebase does not use today; the interpolation's `\(`
 * is treated as an ordinary backslash-escape (the `\` and the following character are both copied
 * through, unstripped), so interpolated expression text is preserved as plain non-comment text either
 * way, which is all this checker needs.
 */
function stripSwiftComments(src) {
  const out = [];
  const n = src.length;
  let i = 0;
  let blockDepth = 0;

  while (i < n) {
    const c = src[i];
    const c2 = i + 1 < n ? src[i + 1] : '';

    if (blockDepth > 0) {
      if (c === '/' && c2 === '*') {
        blockDepth++;
        out.push('  ');
        i += 2;
        continue;
      }
      if (c === '*' && c2 === '/') {
        blockDepth--;
        out.push('  ');
        i += 2;
        continue;
      }
      out.push(c === '\n' ? '\n' : ' ');
      i++;
      continue;
    }

    // Line comment: blank through (but not past) the newline.
    if (c === '/' && c2 === '/') {
      while (i < n && src[i] !== '\n') {
        out.push(' ');
        i++;
      }
      continue;
    }

    // Block comment start.
    if (c === '/' && c2 === '*') {
      blockDepth = 1;
      out.push('  ');
      i += 2;
      continue;
    }

    // Triple-quoted string literal — preserve verbatim (incl. embedded newlines/quotes) until the
    // closing `"""`.
    if (c === '"' && src[i + 1] === '"' && src[i + 2] === '"') {
      out.push('"""');
      i += 3;
      while (i < n) {
        if (src[i] === '\\') {
          out.push(src[i]);
          if (i + 1 < n) out.push(src[i + 1]);
          i += 2;
          continue;
        }
        if (src[i] === '"' && src[i + 1] === '"' && src[i + 2] === '"') {
          out.push('"""');
          i += 3;
          break;
        }
        out.push(src[i]);
        i++;
      }
      continue;
    }

    // Regular double-quoted string literal — preserve verbatim, honoring backslash escapes so an
    // escaped quote (`\"`) never terminates the string early.
    if (c === '"') {
      out.push('"');
      i++;
      while (i < n) {
        if (src[i] === '\\') {
          out.push(src[i]);
          if (i + 1 < n) out.push(src[i + 1]);
          i += 2;
          continue;
        }
        if (src[i] === '"') {
          out.push('"');
          i++;
          break;
        }
        out.push(src[i]);
        i++;
      }
      continue;
    }

    out.push(c);
    i++;
  }

  return out.join('');
}

// ---------- file-set helpers ----------

/** Recursive `.swift` file walker — no external glob dependency, matching this repo's zero-external-
 *  deps convention (same as assets-check.js). Tolerates a missing root directory (returns []). */
function listSwiftFiles(dir) {
  const out = [];
  if (!fs.existsSync(dir)) return out;
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...listSwiftFiles(full));
    } else if (entry.isFile() && entry.name.endsWith('.swift')) {
      out.push(full);
    }
  }
  return out;
}

/** PROD = the app target's own sources. */
function getProdFiles() {
  return listSwiftFiles(PROD_DIR);
}

/** TESTS = iosAppTests + iosAppUITests (the latter tolerated absent, per fs.existsSync inside
 *  listSwiftFiles). */
function getTestsFiles() {
  let out = [];
  for (const dir of TESTS_DIRS) out = out.concat(listSwiftFiles(dir));
  return out;
}

/** ALL = PROD + TESTS. */
function getAllFiles() {
  return getProdFiles().concat(getTestsFiles());
}

function relFile(file) {
  return path.relative(ROOT, file).split(path.sep).join('/');
}

function isSameFile(file, target) {
  return path.resolve(file) === path.resolve(target);
}

/** Line number (1-based) of `index` within `text`, counting '\n' characters before it. Safe to call
 *  against comment-stripped text because stripSwiftComments() preserves every newline verbatim. */
function lineAt(text, index) {
  let line = 1;
  for (let i = 0; i < index && i < text.length; i++) {
    if (text[i] === '\n') line++;
  }
  return line;
}

/** All non-overlapping matches of `regex` in `text`, with 0-based start index and matched text. */
function findAll(text, regex) {
  const flags = regex.flags.includes('g') ? regex.flags : regex.flags + 'g';
  const re = new RegExp(regex.source, flags);
  const out = [];
  let m;
  while ((m = re.exec(text)) !== null) {
    out.push({ index: m.index, text: m[0] });
    if (m[0].length === 0) re.lastIndex++;
  }
  return out;
}

/** Scans every file in `files` for `regex`, over comment-STRIPPED text (unless `useRaw` is set), and
 *  returns `{ file, line, match }` per hit. */
function scanFiles(files, regex, useRaw = false) {
  const results = [];
  for (const file of files) {
    const raw = fs.readFileSync(file, 'utf8');
    const text = useRaw ? raw : stripSwiftComments(raw);
    for (const hit of findAll(text, regex)) {
      results.push({ file, line: lineAt(text, hit.index), match: hit.text });
    }
  }
  return results;
}

/** Captures the text between a `(` already known to be open at `startIndex` (i.e. `startIndex` is the
 *  index right AFTER that opening paren) and its balanced closing `)`, tracking nested parens. Used by
 *  Check D2 to inspect a `.dynamicTypeSize(...)` call's argument without assuming it fits on one line. */
function captureBalancedParens(text, startIndex) {
  let depth = 1;
  let i = startIndex;
  while (i < text.length && depth > 0) {
    if (text[i] === '(') depth++;
    else if (text[i] === ')') {
      depth--;
      if (depth === 0) break;
    }
    i++;
  }
  return { text: text.slice(startIndex, i), endIndex: i + 1 };
}

// ---------- Check Group A — typography (A1/A2 plan/D121-mandated; A3/A4 additive) ----------

/** A1 (plan-mandated): ".system(size:" (covers both "Font.system(size:" and SwiftUI's inferred
 *  ".system(size:" form) must appear EXACTLY ONCE across ALL, and that occurrence must be in
 *  Theme/MentoraTypography.swift (SANCTIONED_EXCEPTIONS.systemSizeCallSite). */
function checkA1_systemSizeExactlyOnce(errors) {
  const matches = scanFiles(getAllFiles(), /\.system\(size:/);
  if (matches.length !== 1) {
    errors.push(
      `A1: expected exactly 1 occurrence of ".system(size:" (after comment-stripping) across the app ` +
      `+ test targets, found ${matches.length}` +
      (matches.length ? `: ${matches.map((m) => `${relFile(m.file)}:${m.line}`).join(', ')}` : '.')
    );
    return;
  }
  const [only] = matches;
  if (!isSameFile(only.file, TYPOGRAPHY_FILE)) {
    errors.push(
      `A1: the one ".system(size:" occurrence is at ${relFile(only.file)}:${only.line}, expected it in ` +
      `${relFile(TYPOGRAPHY_FILE)} (the sanctioned MentoraFontModifier call site).`
    );
  }
}

/** A2 (plan-mandated): "UIFontMetrics" must appear ZERO times anywhere — it reads the trait collection
 *  directly and ignores an injected .dynamicTypeSize(...), which this codebase's own tests rely on. */
function checkA2_noUIFontMetrics(errors) {
  const matches = scanFiles(getAllFiles(), /\bUIFontMetrics\b/);
  for (const m of matches) {
    errors.push(
      `A2: banned "UIFontMetrics" found at ${relFile(m.file)}:${m.line} — use @ScaledMetric instead ` +
      `(see MentoraTypography.swift's header comment for why).`
    );
  }
}

/** A3 (ADDITIVE, beyond the master plan's literal text): "Font.custom(" must appear ZERO times
 *  anywhere — protects the H8 "no bundled font" requirement (a bundled font would break Apple's
 *  automatic SF Arabic substitution). */
function checkA3_noFontCustom(errors) {
  const matches = scanFiles(getAllFiles(), /Font\s*\.\s*custom\s*\(/);
  for (const m of matches) {
    errors.push(
      `A3 (additive): banned "Font.custom(" found at ${relFile(m.file)}:${m.line} — bundling a custom ` +
      `font breaks automatic SF Arabic substitution (H8).`
    );
  }
}

/** A4 (ADDITIVE): ".font(" over PROD must appear ONLY in Theme/MentoraTypography.swift — forces all
 *  font application through .mentoraFont(_:). */
function checkA4_fontModifierOnlyInTypography(errors) {
  const matches = scanFiles(getProdFiles(), /\.font\(/);
  for (const m of matches) {
    if (!isSameFile(m.file, TYPOGRAPHY_FILE)) {
      errors.push(
        `A4 (additive): ".font(" found outside ${relFile(TYPOGRAPHY_FILE)} at ${relFile(m.file)}:` +
        `${m.line} — apply fonts via .mentoraFont(_:) instead.`
      );
    }
  }
}

// ---------- Check Group B — raw system color (plan-mandated: B1-B3; additive: B4) ----------

/** Forbidden color-name palette. `clear` is deliberately EXCLUDED — see
 *  SANCTIONED_EXCEPTIONS.colorClearExcluded. Applies to PROD INCLUDING Theme/ — a deliberate
 *  strengthening beyond the master plan's literal "outside Theme/" wording, since
 *  platform-contract.json's systemColorRule has no Theme/ carve-out and Theme/ is clean today. */
const FORBIDDEN_COLOR_NAMES = [
  'red', 'orange', 'yellow', 'green', 'mint', 'teal', 'cyan', 'blue', 'indigo', 'purple', 'pink',
  'brown', 'white', 'black', 'gray', 'grey', 'primary', 'secondary', 'accentColor',
];
const COLOR_NAME_ALTERNATION = FORBIDDEN_COLOR_NAMES.join('|');

/** B1 (plan-mandated): "Color.<forbiddenName>" must appear ZERO times over PROD. */
function checkB1_noColorDotForbiddenName(errors) {
  const re = new RegExp(`\\bColor\\.(${COLOR_NAME_ALTERNATION})\\b`);
  const matches = scanFiles(getProdFiles(), re);
  for (const m of matches) {
    errors.push(
      `B1: raw system color "${m.match}" found at ${relFile(m.file)}:${m.line} — substitute the ` +
      `matching mentora* semantic token (platform-contract.json's systemColorRule).`
    );
  }
}

/** SwiftUI modifiers that take a Color argument — B2 checks each for a forbidden raw color argument. */
const COLOR_MODIFIERS = [
  'foregroundColor', 'foregroundStyle', 'background', 'backgroundStyle', 'tint', 'accentColor', 'fill',
  'stroke', 'strokeBorder', 'border', 'overlay', 'shadow', 'underline', 'strikethrough',
  'listRowBackground', 'toolbarBackground', 'containerBackground',
];

/** B2 (plan-mandated): a color-bearing modifier call (e.g. ".foregroundColor(.blue)",
 *  ".shadow(color: .black, ...)") taking a forbidden raw color must appear ZERO times over PROD. */
function checkB2_noRawColorInModifierCall(errors) {
  const modAlt = COLOR_MODIFIERS.join('|');
  const re = new RegExp(`\\.(${modAlt})\\(\\s*(color:\\s*)?\\.(${COLOR_NAME_ALTERNATION})\\b`);
  const matches = scanFiles(getProdFiles(), re);
  for (const m of matches) {
    errors.push(
      `B2: raw system color passed to a color-bearing modifier at ${relFile(m.file)}:${m.line} ` +
      `(match: "${m.match}") — use a mentora* semantic token instead.`
    );
  }
}

/** B3 (plan-mandated): UIKit/raw-construction bridging must appear ZERO times over PROD:
 *   - "Color(." (a leading-dot member access, e.g. Color(.systemBackground)) — verified NOT to match
 *     Color("mentoraX") (string-literal, legitimate/generated) or Color(shadowColorAssetName) (a
 *     variable-argument call in MentoraElevation.swift, legitimate) since both start with a character
 *     other than a literal ".".
 *   - "Color(uiColor:" / "Color(nsColor:" / "Color(cgColor:" (bridging constructors).
 *   - "Color(red:" / "Color(hue:" / "Color(white:" (literal RGB/HSB construction).
 *   - bare "UIColor" (word boundary). */
function checkB3_noUikitOrLiteralColorConstruction(errors) {
  const patterns = [
    { re: /\bColor\(\s*\./, label: 'Color(.<member>) leading-dot construction' },
    { re: /\bColor\(\s*(uiColor|nsColor|cgColor)\s*:/, label: 'Color(uiColor:/nsColor:/cgColor:) bridging construction' },
    { re: /\bColor\(\s*(red|hue|white)\s*:/, label: 'Color(red:/hue:/white:) literal RGB/HSB construction' },
    { re: /\bUIColor\b/, label: 'bare UIColor' },
  ];
  for (const { re, label } of patterns) {
    const matches = scanFiles(getProdFiles(), re);
    for (const m of matches) {
      errors.push(
        `B3: ${label} found at ${relFile(m.file)}:${m.line} (match: "${m.match}") — use a mentora* ` +
        `semantic Color accessor instead.`
      );
    }
  }
}

/** B4 (ADDITIVE): "Color(\"" (string-literal-argument construction) over PROD must appear ONLY in the
 *  generated Theme/Color+Mentora.swift — see SANCTIONED_EXCEPTIONS.colorStringLiteralGeneratedOnly. */
function checkB4_colorStringLiteralOnlyInGenerated(errors) {
  const matches = scanFiles(getProdFiles(), /\bColor\(\s*"/);
  for (const m of matches) {
    if (!isSameFile(m.file, COLOR_MENTORA_FILE)) {
      errors.push(
        `B4 (additive): 'Color("...")' string-literal construction found outside ` +
        `${relFile(COLOR_MENTORA_FILE)} at ${relFile(m.file)}:${m.line} — add a new accessor there ` +
        `instead of constructing a Color from a raw asset-name string.`
      );
    }
  }
}

// ---------- Check Group C — theme root (D121's 5 patterns, plan-mandated, PROD only) ----------

/** C1: "preferredColorScheme(" must appear ONLY in Theme/MentoraTheme.swift over PROD. */
function checkC1_preferredColorSchemeOnlyInThemeRoot(errors) {
  const matches = scanFiles(getProdFiles(), /\bpreferredColorScheme\(/);
  for (const m of matches) {
    if (!isSameFile(m.file, THEME_ROOT_FILE)) {
      errors.push(
        `C1: "preferredColorScheme(" found outside ${relFile(THEME_ROOT_FILE)} at ${relFile(m.file)}:` +
        `${m.line} — this must stay the single theme-root call site (D121).`
      );
    }
  }
}

/** C2: ".environment(\\.locale" / ".transformEnvironment(\\.locale" must appear ONLY in
 *  Theme/MentoraTheme.swift over PROD. Passes on ZERO matches too — a forward-looking guard per D121,
 *  since production code currently uses transformEnvironment exclusively (already scoped correctly). */
function checkC2_localeEnvironmentOnlyInThemeRoot(errors) {
  const matches = scanFiles(getProdFiles(), /\b(environment|transformEnvironment)\(\\\.locale\b/);
  for (const m of matches) {
    if (!isSameFile(m.file, THEME_ROOT_FILE)) {
      errors.push(
        `C2: "${m.match}" (locale environment write) found outside ${relFile(THEME_ROOT_FILE)} at ` +
        `${relFile(m.file)}:${m.line}.`
      );
    }
  }
}

/** C3: same two patterns as C2, for "\\.layoutDirection". Also passes on zero. */
function checkC3_layoutDirectionEnvironmentOnlyInThemeRoot(errors) {
  const matches = scanFiles(getProdFiles(), /\b(environment|transformEnvironment)\(\\\.layoutDirection\b/);
  for (const m of matches) {
    if (!isSameFile(m.file, THEME_ROOT_FILE)) {
      errors.push(
        `C3: "${m.match}" (layoutDirection environment write) found outside ${relFile(THEME_ROOT_FILE)} ` +
        `at ${relFile(m.file)}:${m.line}.`
      );
    }
  }
}

/** C4: ".mentoraTheme(" must appear EXACTLY ONCE outside Theme/MentoraTokenGallery.swift (across all
 *  of PROD except that one file — see SANCTIONED_EXCEPTIONS.galleryMentoraThemeCallSites), and that one
 *  occurrence must be in MentoraApp.swift. Occurrences inside the gallery are unlimited/unchecked. */
function checkC4_mentoraThemeCallSite(errors) {
  const matches = scanFiles(getProdFiles(), /\.mentoraTheme\(/);
  const outsideGallery = matches.filter((m) => !isSameFile(m.file, GALLERY_FILE));
  if (outsideGallery.length !== 1) {
    errors.push(
      `C4: expected exactly 1 ".mentoraTheme(" call site outside ${relFile(GALLERY_FILE)}, found ` +
      `${outsideGallery.length}` +
      (outsideGallery.length
        ? `: ${outsideGallery.map((m) => `${relFile(m.file)}:${m.line}`).join(', ')}`
        : '.')
    );
    return;
  }
  const [only] = outsideGallery;
  if (!isSameFile(only.file, APP_FILE)) {
    errors.push(
      `C4: the one ".mentoraTheme(" call site outside the gallery is at ${relFile(only.file)}:` +
      `${only.line}, expected it in ${relFile(APP_FILE)} (MentoraRootView).`
    );
  }
}

/** C5: 'Locale(identifier: "ar' (literal string prefix) must appear ONLY in Theme/MentoraTheme.swift
 *  over PROD. Passes on zero matches (currently zero — production code uses the
 *  MentoraThemeRules.arabicLocaleIdentifier constant, not an inline literal). */
function checkC5_arabicLocaleLiteralOnlyInThemeRoot(errors) {
  const matches = scanFiles(getProdFiles(), /Locale\(identifier:\s*"ar/);
  for (const m of matches) {
    if (!isSameFile(m.file, THEME_ROOT_FILE)) {
      errors.push(
        `C5: literal '${m.match}' found outside ${relFile(THEME_ROOT_FILE)} at ${relFile(m.file)}:` +
        `${m.line} — use MentoraThemeRules.arabicLocaleIdentifier instead of an inline literal.`
      );
    }
  }
}

// ---------- Check Group D — layout resilience (ADDITIVE, PROD only) ----------

/** D1 (ADDITIVE): ".minimumScaleFactor(" must appear ZERO times anywhere in PROD — the layout must
 *  give way at accessibility sizes, per PHASE_5_IOS_SYSTEM_DESIGN.md §§ 11, 21 / CONTENT_RESILIENCE.md
 *  § 8, never shrink text to fit. */
function checkD1_noMinimumScaleFactor(errors) {
  const matches = scanFiles(getProdFiles(), /\.minimumScaleFactor\(/);
  for (const m of matches) {
    errors.push(
      `D1 (additive): ".minimumScaleFactor(" found at ${relFile(m.file)}:${m.line} — the layout must ` +
      `give way, not shrink text, at accessibility sizes.`
    );
  }
}

/** D2 (ADDITIVE): ".dynamicTypeSize(" called with a RANGE argument (contains "..." inside the parens,
 *  e.g. ".dynamicTypeSize(...DynamicTypeSize.accessibility3)") must appear ZERO times in PROD. An
 *  EXACT-VALUE call (e.g. ".dynamicTypeSize(.accessibility5)", no "...") is NOT flagged — the gallery
 *  file uses this legitimately to pin a single preview's Dynamic Type size. */
function checkD2_noDynamicTypeSizeRange(errors) {
  for (const file of getProdFiles()) {
    const raw = fs.readFileSync(file, 'utf8');
    const stripped = stripSwiftComments(raw);
    const re = /\.dynamicTypeSize\(/g;
    let m;
    while ((m = re.exec(stripped)) !== null) {
      const argsStart = m.index + m[0].length;
      const { text: argText, endIndex } = captureBalancedParens(stripped, argsStart);
      if (argText.includes('...')) {
        errors.push(
          `D2 (additive): ".dynamicTypeSize(...)" called with a RANGE argument at ${relFile(file)}:` +
          `${lineAt(stripped, m.index)} (".dynamicTypeSize(${argText.trim()})") — use an exact-value ` +
          `call instead.`
        );
      }
      re.lastIndex = endIndex;
    }
  }
}

// ---------- Check Group E — gallery completeness (depends on Step 4's gallery file existing) ----------

/** E1: Theme/MentoraTokenGallery.swift exists. */
function checkE1_galleryExists(errors) {
  if (!fs.existsSync(GALLERY_FILE)) {
    errors.push(
      `E1: ${relFile(GALLERY_FILE)} does not exist — Task T6's completion gate requires this token ` +
      `gallery file (PHASE_5_IOS_IMPLEMENTATION_PLAN.md T6's Manual verification / MC-2 item).`
    );
  }
}

/** E2: the gallery file's structure — exactly one "#if DEBUG" and one "#endif", with "#if DEBUG"
 *  appearing before "#endif" AND "#endif" being the last non-blank line of the file, so the whole
 *  body (all 46 colors, all 8 preview registries, every sample string) is provably inside the
 *  DEBUG-only block, not just SOME prefix of it. Runs against comment-stripped text like every other
 *  check in this file except F1 (see the file header) — "#if DEBUG"/"#endif"/"#Preview" are
 *  directives/macros, not comments, so stripSwiftComments() preserves them verbatim; scanning
 *  stripped text just means a commented-out "#endif" or "#Preview(...)" elsewhere in the file can
 *  never desync this check (or E3/E4) from what actually compiles. A simple, pragmatic structural
 *  check, not a full parser. */
function checkE2_galleryStructure(errors) {
  if (!fs.existsSync(GALLERY_FILE)) return; // E1 already reports this
  const raw = fs.readFileSync(GALLERY_FILE, 'utf8');
  const stripped = stripSwiftComments(raw);
  const ifDebugMatches = [...stripped.matchAll(/#if\s+DEBUG\b/g)];
  const endifMatches = [...stripped.matchAll(/#endif\b/g)];
  if (ifDebugMatches.length !== 1) {
    errors.push(
      `E2: expected exactly one "#if DEBUG" in ${relFile(GALLERY_FILE)}, found ${ifDebugMatches.length}.`
    );
  }
  if (endifMatches.length !== 1) {
    errors.push(
      `E2: expected exactly one "#endif" in ${relFile(GALLERY_FILE)}, found ${endifMatches.length}.`
    );
  }
  if (
    ifDebugMatches.length === 1 &&
    endifMatches.length === 1 &&
    ifDebugMatches[0].index >= endifMatches[0].index
  ) {
    errors.push(`E2: "#if DEBUG" must appear before "#endif" in ${relFile(GALLERY_FILE)}.`);
  }
  if (endifMatches.length === 1) {
    const afterEndif = stripped.slice(endifMatches[0].index + endifMatches[0][0].length);
    if (afterEndif.trim().length > 0) {
      errors.push(
        `E2: "#endif" must be the last non-blank line of ${relFile(GALLERY_FILE)} — found real ` +
        `content after it, meaning some of the gallery body is provably OUTSIDE the "#if DEBUG" ` +
        `block and would ship into a Release build.`
      );
    }
  }
}

/** E3: color-list completeness. Parses `GalleryColor(name: "X", color: .Y)` entries out of the gallery
 *  file and `static var (mentora...): Color` entries out of Color+Mentora.swift (excluding any name
 *  starting with "mentoraShadowElevation" — shadow colorsets, deliberately not in the gallery's
 *  color-swatch section). Asserts: (a) every gallery row's name matches its color literally; (b) the
 *  gallery name set exactly equals the non-shadow generated accessor name set. */
function checkE3_galleryColorListCompleteness(errors) {
  if (!fs.existsSync(GALLERY_FILE) || !fs.existsSync(COLOR_MENTORA_FILE)) return; // E1 covers this

  const galleryStripped = stripSwiftComments(fs.readFileSync(GALLERY_FILE, 'utf8'));
  const colorStripped = stripSwiftComments(fs.readFileSync(COLOR_MENTORA_FILE, 'utf8'));

  const galleryEntries = [
    ...galleryStripped.matchAll(/GalleryColor\(name:\s*"([A-Za-z0-9]+)",\s*color:\s*\.([A-Za-z0-9]+)\)/g),
  ].map((m) => ({ name: m[1], colorRef: m[2] }));

  if (galleryEntries.length === 0) {
    errors.push(
      `E3: found zero "GalleryColor(name: ..., color: ...)" entries in ${relFile(GALLERY_FILE)} — ` +
      `expected one per non-shadow generated accessor.`
    );
  }

  for (const { name, colorRef } of galleryEntries) {
    if (name !== colorRef) {
      errors.push(
        `E3: GalleryColor entry name/color mismatch in ${relFile(GALLERY_FILE)}: name "${name}" vs ` +
        `color ".${colorRef}" — the string literal must match the accessor name exactly.`
      );
    }
  }

  const generatedNames = [...colorStripped.matchAll(/static var (mentora[A-Za-z0-9]+):\s*Color/g)]
    .map((m) => m[1])
    .filter((n) => !n.startsWith('mentoraShadowElevation'));

  const gallerySet = new Set(galleryEntries.map((e) => e.name));
  const generatedSet = new Set(generatedNames);

  const missing = generatedNames.filter((n) => !gallerySet.has(n));
  const extra = [...gallerySet].filter((n) => !generatedSet.has(n));

  if (missing.length > 0) {
    errors.push(
      `E3: accessor(s) present in ${relFile(COLOR_MENTORA_FILE)} but missing from ` +
      `${relFile(GALLERY_FILE)}'s color list: ${missing.join(', ')}`
    );
  }
  if (extra.length > 0) {
    errors.push(
      `E3: ${relFile(GALLERY_FILE)} color name(s) not present as a generated (non-shadow) accessor: ` +
      `${extra.join(', ')}`
    );
  }
}

/** E4: preview-name completeness. Parses `#Preview("X")` entries out of the gallery file and asserts
 *  the set is EXACTLY the required 8 strings — the full 2x2x2 light/dark x en/ar x default/AX5 matrix
 *  — no more, no fewer, no duplicates. */
const REQUIRED_PREVIEW_NAMES = [
  'Light / en / default',
  'Dark / en / default',
  'Light / ar / default',
  'Dark / ar / default',
  'Light / en / AX5',
  'Dark / en / AX5',
  'Light / ar / AX5',
  'Dark / ar / AX5',
];

function checkE4_galleryPreviewNames(errors) {
  if (!fs.existsSync(GALLERY_FILE)) return; // E1 covers this

  const stripped = stripSwiftComments(fs.readFileSync(GALLERY_FILE, 'utf8'));
  const found = [...stripped.matchAll(/#Preview\(\s*"([^"]+)"/g)].map((m) => m[1]);
  const foundSet = new Set(found);
  const requiredSet = new Set(REQUIRED_PREVIEW_NAMES);

  const missing = REQUIRED_PREVIEW_NAMES.filter((n) => !foundSet.has(n));
  const extra = [...new Set(found.filter((n) => !requiredSet.has(n)))];
  const dupes = [...new Set(found.filter((n, i) => found.indexOf(n) !== i))];

  if (missing.length > 0) {
    errors.push(`E4: missing #Preview name(s) in ${relFile(GALLERY_FILE)}: ${missing.join(', ')}`);
  }
  if (extra.length > 0) {
    errors.push(`E4: unexpected #Preview name(s) in ${relFile(GALLERY_FILE)}: ${extra.join(', ')}`);
  }
  if (dupes.length > 0) {
    errors.push(`E4: duplicate #Preview name(s) in ${relFile(GALLERY_FILE)}: ${dupes.join(', ')}`);
  }
}

// ---------- Check Group F — stripper self-guard ----------

/** F1: asserts zero occurrences of the two-character sequence '#"' anywhere in ALL — checked against
 *  RAW (not stripped) text deliberately, since this check is specifically about stripSwiftComments()'s
 *  own blind spot (Swift raw string literals). If found, fails loudly naming the file/line, rather than
 *  letting every other check in this file silently mis-scan that file. */
function checkF1_noUnsupportedRawStringLiterals(errors) {
  for (const file of getAllFiles()) {
    const raw = fs.readFileSync(file, 'utf8');
    const idx = raw.indexOf('#"');
    if (idx !== -1) {
      errors.push(
        `F1: found '#"' (Swift raw string literal syntax) at ${relFile(file)}:${lineAt(raw, idx)} — ` +
        `stripSwiftComments() does not support raw string literals and would mis-scan this file. ` +
        `Extend the stripper before this file can be safely checked by any other rule in this script.`
      );
    }
  }
}

// ---------- CHECKS array ----------

const CHECKS = [
  { name: 'A1: .system(size: exactly once, in MentoraTypography.swift', run: checkA1_systemSizeExactlyOnce },
  { name: 'A2: no UIFontMetrics anywhere', run: checkA2_noUIFontMetrics },
  { name: 'A3 (additive): no Font.custom( anywhere', run: checkA3_noFontCustom },
  { name: 'A4 (additive): .font( only in MentoraTypography.swift (PROD)', run: checkA4_fontModifierOnlyInTypography },
  { name: 'B1: no Color.<forbiddenName> (PROD)', run: checkB1_noColorDotForbiddenName },
  { name: 'B2: no raw color in color-bearing modifier call (PROD)', run: checkB2_noRawColorInModifierCall },
  { name: 'B3: no UIKit/raw-construction color bridging (PROD)', run: checkB3_noUikitOrLiteralColorConstruction },
  { name: 'B4 (additive): Color("...") only in Color+Mentora.swift (PROD)', run: checkB4_colorStringLiteralOnlyInGenerated },
  { name: 'C1: preferredColorScheme( only in MentoraTheme.swift (PROD)', run: checkC1_preferredColorSchemeOnlyInThemeRoot },
  { name: 'C2: locale environment writes only in MentoraTheme.swift (PROD)', run: checkC2_localeEnvironmentOnlyInThemeRoot },
  { name: 'C3: layoutDirection environment writes only in MentoraTheme.swift (PROD)', run: checkC3_layoutDirectionEnvironmentOnlyInThemeRoot },
  { name: 'C4: .mentoraTheme( exactly one call site outside the gallery, in MentoraApp.swift', run: checkC4_mentoraThemeCallSite },
  { name: 'C5: Locale(identifier: "ar literal only in MentoraTheme.swift (PROD)', run: checkC5_arabicLocaleLiteralOnlyInThemeRoot },
  { name: 'D1 (additive): no .minimumScaleFactor( anywhere (PROD)', run: checkD1_noMinimumScaleFactor },
  { name: 'D2 (additive): no .dynamicTypeSize( range argument (PROD)', run: checkD2_noDynamicTypeSizeRange },
  { name: 'E1: MentoraTokenGallery.swift exists', run: checkE1_galleryExists },
  { name: 'E2: gallery #if DEBUG / #endif structure', run: checkE2_galleryStructure },
  { name: 'E3: gallery color-list completeness vs Color+Mentora.swift', run: checkE3_galleryColorListCompleteness },
  { name: 'E4: gallery preview-name completeness (8 required)', run: checkE4_galleryPreviewNames },
  { name: 'F1: no unsupported raw string literals (#") in scanned files', run: checkF1_noUnsupportedRawStringLiterals },
];

function runAllChecks() {
  const errors = [];
  for (const check of CHECKS) check.run(errors);
  return errors;
}

module.exports = {
  stripSwiftComments,
  SANCTIONED_EXCEPTIONS,
  FORBIDDEN_COLOR_NAMES,
  REQUIRED_PREVIEW_NAMES,
  checkA1_systemSizeExactlyOnce,
  checkA2_noUIFontMetrics,
  checkA3_noFontCustom,
  checkA4_fontModifierOnlyInTypography,
  checkB1_noColorDotForbiddenName,
  checkB2_noRawColorInModifierCall,
  checkB3_noUikitOrLiteralColorConstruction,
  checkB4_colorStringLiteralOnlyInGenerated,
  checkC1_preferredColorSchemeOnlyInThemeRoot,
  checkC2_localeEnvironmentOnlyInThemeRoot,
  checkC3_layoutDirectionEnvironmentOnlyInThemeRoot,
  checkC4_mentoraThemeCallSite,
  checkC5_arabicLocaleLiteralOnlyInThemeRoot,
  checkD1_noMinimumScaleFactor,
  checkD2_noDynamicTypeSizeRange,
  checkE1_galleryExists,
  checkE2_galleryStructure,
  checkE3_galleryColorListCompleteness,
  checkE4_galleryPreviewNames,
  checkF1_noUnsupportedRawStringLiterals,
  runAllChecks,
  CHECKS,
};

if (require.main === module) {
  const errors = runAllChecks();
  console.log(`iOS theme/token completion-gate check: ${CHECKS.length} check group(s) run.`);
  if (errors.length) {
    console.error(`\n${errors.length} error(s):`);
    for (const e of errors) console.error(`  - ${e}`);
    console.error('\nCheck FAILED.');
    process.exit(1);
  }
  console.log('\nCheck PASSED.');
}
