#!/usr/bin/env node
'use strict';

/**
 * Mentora iOS Localization Source-Policy Gate (Phase 5 Task T7, slice 4 of 4 -- the LAST slice)
 *
 * Plain Node, no external dependencies -- same precedent as `tools/ios-checks/theme-checks.js`,
 * `tools/ios-checks/catalog-parity.js` and `tools/ios-checks/assets-check.js`
 * (execution/DECISIONS_LOG.md D35): this checker's whole job is source-TEXT policy over plain `.swift`
 * files under `mobile/iosApp/iosApp/**` (production sources only -- `iosAppTests/`/`iosAppUITests/`
 * are deliberately OUT of scope, since tests legitimately reference `Locale`/`String(localized:)`/
 * `Text(verbatim:)`/etc. for their own verification purposes -- see e.g.
 * `iosAppTests/LocalizationPlumbingTests.swift`, which does exactly that), which a hand-rolled Node
 * script can prove real things about without a Swift compiler on this Windows host.
 *
 * WHAT THIS FILE ENFORCES (the project-level decision this slice mechanizes, already made, not
 * revisited here -- `execution/DECISIONS_LOG.md` D124's closing line and D125's own header):
 * `Support/MentoraStrings.swift` is the ONE sanctioned way to resolve a localized string anywhere in
 * this app target. Plain SwiftUI `Text("key")` catalog lookup, `String(localized:...)`,
 * `NSLocalizedString(...)` and `LocalizedStringResource(...)` are all forbidden everywhere EXCEPT
 * inside `Support/MentoraStrings.swift` itself (which legitimately implements exactly these calls).
 *
 * Named in / enforces:
 *   - `execution/DECISIONS_LOG.md` D124 -- "per the user's explicit decision, MentoraStrings will be
 *     the SOLE sanctioned string-resolution path app-wide (plain Text("key") catalog lookup forbidden
 *     at call sites), enforced by slice 4's localization-checks.js."
 *   - `execution/PHASE_5_ACCEPTANCE_CRITERIA.md` H1 -- "Every user-facing string comes from
 *     Localizable.xcstrings; zero hardcoded user-facing literals, including default parameter values
 *     of reusable components (the exact retryLabel-class gap Phase 4 had to sweep in Task 19, D94)." --
 *     Check Groups A/B (B4 in particular is the D94 default-parameter heuristic).
 *   - `execution/PHASE_5_IOS_IMPLEMENTATION_PLAN.md` T7's own approach section -- "Substitution always
 *     goes through the ported explicit keys plus String(format:)/String(localized:) with explicit
 *     arguments. SwiftUI's auto-generated String Catalog interpolation keys are forbidden -- writing
 *     Text("Hello (name)") makes Xcode silently extract a new key, creating a parallel, un-ported key
 *     space that compiles fine, never appears in the Android set, and breaks H1/H2 without any visible
 *     symptom." and T7's own completion gate -- "no auto-extracted interpolation key anywhere in the
 *     catalog." -- Check B2, its own explicitly-named auto-extraction guard.
 *   - `execution/PHASE_5_IOS_SYSTEM_DESIGN.md § 13` -- "Every layout uses leading/trailing
 *     (.padding(.leading,), HStack alignments, .multilineTextAlignment(.leading)) -- never .left/
 *     .right. A lint-style grep is part of the QA sweep, mirroring web's lint:logical-properties gate."
 *     -- Check Group C. NOTE, confirmed by reading this section directly rather than assumed from the
 *     web checker's naming: SwiftUI's `.leading`/`.trailing` (on `Edge.Set`, `HStack`/`VStack`
 *     `alignment:`, `.multilineTextAlignment(...)`, etc.) ARE the logical/RTL-aware forms and are
 *     therefore NEVER flagged here -- this is the OPPOSITE of CSS's physical-by-default convention,
 *     where `left`/`right` are the (historically) default/physical properties. Only the genuinely
 *     physical `.left`/`.right` spellings are violations.
 *
 * THIS IS A REGRESSION GUARD FOR COMMON, TEXTUALLY-RECOGNIZABLE FORMS OF HARDCODED/BYPASSING STRING
 * RESOLUTION, NOT AN EXHAUSTIVE ONE -- same caveat `theme-checks.js` states for its own raw-color/font
 * checks. A grep gate over source text cannot catch every possible Swift expression shape that
 * ultimately puts an unlocalized string on screen (e.g. a computed property that returns a literal,
 * later passed to `Text(_:)` as a variable -- the variable reference itself is indistinguishable from a
 * legitimate one at this text-scanning layer). Do not read a clean run of this script as proof no
 * hardcoded string exists anywhere in the target -- only that none of the COMMON, textually-
 * recognizable forms do. Check B4 in particular (the D94 default-parameter heuristic) is narrower and
 * harder-to-detect than the others; its own doc comment states its known limitations explicitly rather
 * than overclaiming perfect detection.
 *
 * Reuses `theme-checks.js`'s `stripSwiftComments` (imported, not reimplemented) -- every check below
 * runs against comment-stripped text, for the exact reason `theme-checks.js`'s own header documents:
 * a naive raw-text regex would misreport doc-comment prose (e.g. this very file's own header, which
 * mentions `Text("key")`, `String(localized:...)`, etc. in plain English) as real violations.
 *
 * Exit code 0 = all checks passed. Non-zero = at least one failure, printed to stderr.
 */

const fs = require('fs');
const path = require('path');
const { stripSwiftComments } = require('./theme-checks');

const ROOT = path.resolve(__dirname, '..', '..');
const PROD_DIR = path.join(ROOT, 'mobile', 'iosApp', 'iosApp');
const SUPPORT_DIR = path.join(PROD_DIR, 'Support');
const THEME_DIR = path.join(PROD_DIR, 'Theme');
const MENTORA_STRINGS_FILE = path.join(SUPPORT_DIR, 'MentoraStrings.swift');
const GALLERY_FILE = path.join(THEME_DIR, 'MentoraTokenGallery.swift');

/**
 * Every deliberate exception to a rule below, named once here with its reason -- same convention as
 * `theme-checks.js`'s `SANCTIONED_EXCEPTIONS`. Read this before "fixing" a check that looks too
 * lenient.
 */
const SANCTIONED_EXCEPTIONS = {
  mentoraStringsOwnImplementation:
    `Support/MentoraStrings.swift is the ONE sanctioned place "String(localized:", "NSLocalizedString(" ` +
    `and "LocalizedStringResource(" may appear (Check Group A) -- it is the sole funnel every other ` +
    `file must call through instead.`,
  galleryDebugOnlyPlainLiteralLabels:
    `Theme/MentoraTokenGallery.swift's own "Text(\"literal\")" calls (e.g. Text("Colors"), ` +
    `Text("Typography")) are exempt from Check B1 -- this file is a #if DEBUG-gated internal preview/ ` +
    `gallery, never shipped in a Release build and never shown to a real user; every such literal is a ` +
    `section-header/axis label for the gallery's own debug UI. This carve-out is already recorded, and ` +
    `was anticipated, by that file's own header comment ("NON-USER-FACING TEXT, T7 CARVE-OUT") and ` +
    `execution/DECISIONS_LOG.md D122. Deliberately NOT exempt from Check B2 (interpolation) -- an ` +
    `interpolated Text(...) creates a real, phantom auto-extracted catalog key regardless of which file ` +
    `authored it, so that risk is enforced even inside the gallery.`,
};

// ---------- file-set helpers (same recursive-walk pattern as theme-checks.js's listSwiftFiles) ----------

/** Recursive `.swift` file walker -- identical approach to theme-checks.js's listSwiftFiles (not
 *  imported, since theme-checks.js does not export it, but deliberately the same logic: no external
 *  glob dependency, tolerates a missing root directory). */
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

/** PROD = the app target's own sources -- the ONLY scope for this whole file (per this slice's own
 *  scoping requirement: `iosAppTests`/`iosAppUITests` are excluded since tests legitimately reference
 *  the forbidden APIs for their own verification purposes). */
function getProdFiles() {
  return listSwiftFiles(PROD_DIR);
}

function relFile(file) {
  return path.relative(ROOT, file).split(path.sep).join('/');
}

function isSameFile(file, target) {
  return path.resolve(file) === path.resolve(target);
}

/** Line number (1-based) of `index` within `text`. Safe against comment-stripped text because
 *  stripSwiftComments() preserves every newline verbatim (see theme-checks.js). */
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

/** Scans every file in `files` for `regex`, over comment-STRIPPED text, returning `{ file, line, match
 *  }` per hit. */
function scanFiles(files, regex) {
  const results = [];
  for (const file of files) {
    const raw = fs.readFileSync(file, 'utf8');
    const text = stripSwiftComments(raw);
    for (const hit of findAll(text, regex)) {
      results.push({ file, line: lineAt(text, hit.index), match: hit.text });
    }
  }
  return results;
}

/** Parses a Swift string literal (regular `"..."` or triple-quoted `"""..."""`) starting AT the
 *  opening quote character `text[quoteIndex]`. Mirrors theme-checks.js's stripSwiftComments' own
 *  string-literal handling exactly (backslash-escape aware; does not attempt to balance parens inside
 *  an interpolation `\(...)`, which is a known, documented limitation -- see Check B2's own comment).
 *  Returns `{ content, endIndex }`, where `content` is the literal's raw text between the quotes
 *  (escapes preserved verbatim) and `endIndex` is the index just past the closing quote(s). */
function parseStringLiteral(text, quoteIndex) {
  const n = text.length;
  const isTriple = text[quoteIndex + 1] === '"' && text[quoteIndex + 2] === '"';
  let i = quoteIndex + (isTriple ? 3 : 1);
  let content = '';
  while (i < n) {
    if (text[i] === '\\') {
      content += text[i] + (i + 1 < n ? text[i + 1] : '');
      i += 2;
      continue;
    }
    if (isTriple ? (text[i] === '"' && text[i + 1] === '"' && text[i + 2] === '"') : text[i] === '"') {
      i += isTriple ? 3 : 1;
      return { content, endIndex: i };
    }
    content += text[i];
    i++;
  }
  return { content, endIndex: i };
}

// ---------- Check Group A -- forbidden string-resolution APIs outside MentoraStrings.swift ----------

/** The three forbidden Foundation/SwiftUI string-resolution APIs this checker guards, per D124/D125 --
 *  MentoraStrings.swift's own header names exactly these two mechanisms as sanctioned INSIDE that
 *  file ("LocalizedStringResource(...) + String(localized:)"); NSLocalizedString( is the classic
 *  pre-String-Catalog idiom, forbidden everywhere including MentoraStrings.swift itself (it does not
 *  use it, and there is no reason it ever should on this CI-proven mechanism -- see D123). */
const FORBIDDEN_APIS = [
  { pattern: /String\(\s*localized\s*:/, label: 'String(localized:', allowedInMentoraStrings: true },
  { pattern: /NSLocalizedString\(/, label: 'NSLocalizedString(', allowedInMentoraStrings: false },
  { pattern: /LocalizedStringResource\(/, label: 'LocalizedStringResource(', allowedInMentoraStrings: true },
];

function checkA_forbiddenApisOutsideMentoraStrings(errors) {
  const files = getProdFiles();
  for (const { pattern, label, allowedInMentoraStrings } of FORBIDDEN_APIS) {
    const matches = scanFiles(files, pattern);
    for (const m of matches) {
      if (allowedInMentoraStrings && isSameFile(m.file, MENTORA_STRINGS_FILE)) continue;
      errors.push(
        `A: forbidden "${label}" found at ${relFile(m.file)}:${m.line} -- every localized-string ` +
        `lookup must go through ${relFile(MENTORA_STRINGS_FILE)} (D124/D125), the sole sanctioned ` +
        `resolution path.`
      );
    }
  }
}

// ---------- Check Group B -- no hardcoded/catalog-bypassing Text(...) and friends ----------

/** B1/B2: scans every `Text(` call in `file` whose first argument is a plain (non-`verbatim:`)
 *  string literal, reporting each as either B1 (plain literal -- e.g. Text("Home")) or B2 (the literal
 *  contains a Swift string-interpolation escape `\(...)` -- e.g. Text("Hi \(name)")), per the file
 *  header's H1/H2 auto-extraction note. `Text(someVariable)`, `Text(MentoraStrings.text(...))`, and
 *  `Text(verbatim: ...)` never match this scan at all -- the regex only fires when the character
 *  immediately following `Text(`'s opening paren (skipping whitespace) is a literal `"`, which none of
 *  those three forms are. */
function checkB1B2_textLiterals(errors) {
  for (const file of getProdFiles()) {
    const raw = fs.readFileSync(file, 'utf8');
    const stripped = stripSwiftComments(raw);
    const re = /\bText\(\s*"/g;
    let m;
    while ((m = re.exec(stripped)) !== null) {
      const quoteIndex = m.index + m[0].length - 1;
      const { content, endIndex } = parseStringLiteral(stripped, quoteIndex);
      const line = lineAt(stripped, m.index);
      const isInterpolated = content.includes('\\(');

      if (isInterpolated) {
        errors.push(
          `B2 (auto-extraction guard): Text("...") at ${relFile(file)}:${line} contains a string- ` +
          `interpolation escape ("\\(") -- e.g. Text("Hi \\(name)") makes Xcode silently auto-extract ` +
          `a brand-new, un-ported String Catalog key (PHASE_5_IOS_IMPLEMENTATION_PLAN.md T7: "SwiftUI's ` +
          `auto-generated String Catalog interpolation keys are forbidden"). Use ` +
          `MentoraStrings.text(_:locale:_:) with explicit %N$@ arguments instead.`
        );
      } else if (!isSameFile(file, GALLERY_FILE)) {
        errors.push(
          `B1: hardcoded Text("${content}") found at ${relFile(file)}:${line} -- plain string-literal ` +
          `Text(...) bypasses Localizable.xcstrings entirely (H1). Use MentoraStrings.text(_:locale:) ` +
          `instead (see SANCTIONED_EXCEPTIONS.galleryDebugOnlyPlainLiteralLabels for the one carve-out).`
        );
      }
      re.lastIndex = endIndex;
    }
  }
}

/** B3 (ADDITIVE, beyond H1's literal "Text/accessibility label" wording): other common SwiftUI
 *  string-literal-taking APIs that can equally bypass the catalog if handed a raw literal. Detection is
 *  presence-only (first-argument literal), matching this file's B1 style rather than B1/B2's fuller
 *  interpolation parse, since these APIs are not separately named by an H1/H2 auto-extraction concern
 *  the way Text(...) is. Currently zero real call sites exist anywhere in this tree (no screens are
 *  built yet -- T8+), so this is a forward-looking guard, exactly like theme-checks.js's C2/C3 ("passes
 *  on zero matches too... a forward-looking guard"). */
const OTHER_STRING_LITERAL_APIS = [
  { pattern: /\bLabel\(\s*"/, label: 'Label("...")' },
  { pattern: /\.accessibilityLabel\(\s*"/, label: '.accessibilityLabel("...")' },
  { pattern: /\.navigationTitle\(\s*"/, label: '.navigationTitle("...")' },
  { pattern: /\.confirmationDialog\(\s*"/, label: '.confirmationDialog("...")' },
  { pattern: /\.alert\(\s*"/, label: '.alert("...")' },
  { pattern: /\.help\(\s*"/, label: '.help("...")' },
];

function checkB3_otherStringLiteralApis(errors) {
  const files = getProdFiles();
  for (const { pattern, label } of OTHER_STRING_LITERAL_APIS) {
    const matches = scanFiles(files, pattern);
    for (const m of matches) {
      errors.push(
        `B3 (additive): hardcoded ${label} found at ${relFile(m.file)}:${m.line} -- resolve the ` +
        `string via MentoraStrings.text(_:locale:) and pass the result instead of a raw literal.`
      );
    }
  }
}

/** B4 (ADDITIVE, D94 heuristic): a string-literal DEFAULT VALUE on a function/init parameter typed
 *  `String`/`String?`/`LocalizedStringKey`/`LocalizedStringKey?` -- the exact `retryLabel`-class gap
 *  Phase 4/Android's Task 19 had to sweep after the fact (D94): a reusable component's default
 *  parameter silently hardcodes English, invisible at every call site that doesn't override it.
 *
 * KNOWN LIMITATIONS (a real heuristic, not a parser -- documented rather than overclaimed):
 *   - Only recognizes a parameter whose default appears immediately after an unclaimed `(` or `,`
 *     (i.e. plausibly inside a parameter list) followed by `name: Type = "literal"`. A default value
 *     built from concatenation/interpolation/a nested call (e.g. `= "Retry" + suffix`) is still flagged
 *     (the leading `"` still matches), but a default that is itself an expression with no literal at
 *     all obviously cannot be caught this way.
 *   - Cannot distinguish a genuine function/init parameter list from a tuple-typed stored property or
 *     enum case's associated values that happen to follow the same `(`/`,` + `name: Type = "..."`
 *     shape -- a real but rare false-positive class for this specific narrow pattern.
 *   - Does not understand multi-line default expressions split across an interpolated/concatenated
 *     literal spanning further punctuation.
 */
function checkB4_defaultParameterStringLiteral(errors) {
  const re = /[(,]\s*\w+\s*:\s*(String|LocalizedStringKey)\??\s*=\s*"/g;
  for (const file of getProdFiles()) {
    const raw = fs.readFileSync(file, 'utf8');
    const stripped = stripSwiftComments(raw);
    let m;
    while ((m = re.exec(stripped)) !== null) {
      const quoteIndex = m.index + m[0].length - 1;
      const { content, endIndex } = parseStringLiteral(stripped, quoteIndex);
      errors.push(
        `B4 (additive, D94 heuristic): a string-literal default parameter value ("${content}") found ` +
        `at ${relFile(file)}:${lineAt(stripped, m.index)} -- a reusable component's default must not ` +
        `silently hardcode a user-facing string (the exact retryLabel-class bug D94 found once on ` +
        `Android). Make the parameter required and resolve it via MentoraStrings.text(_:locale:) at ` +
        `every call site instead.`
      );
      re.lastIndex = endIndex;
    }
  }
}

// ---------- Check Group C -- RTL-safe layout (genuinely-physical SwiftUI directions only) ----------

/** C1/C2: genuinely PHYSICAL-direction SwiftUI APIs, confirmed against
 *  PHASE_5_IOS_SYSTEM_DESIGN.md § 13 ("Every layout uses leading/trailing... never .left/.right").
 *  `.leading`/`.trailing` (Edge.Set, HStack/VStack alignment, .multilineTextAlignment, etc.) are
 *  SwiftUI's LOGICAL/RTL-aware forms and are the OPPOSITE of CSS's physical-by-default convention --
 *  they are correct and are never flagged by this checker. Only the genuinely physical `.left`/
 *  `.right` spellings are violations. NOTE: SwiftUI's own `TextAlignment`/`Edge.Set` types have no
 *  `.left`/`.right` case at all (only `.leading`/`.center`/`.trailing`) -- so `.padding(.left`/
 *  `.padding(.right)` and `.multilineTextAlignment(.left)`/`(.right)` cannot be reached via a pure,
 *  correctly-typed SwiftUI call today. They are still worth grepping for defensively (a raw
 *  `CGFloat`/`UIEdgeInsets` offset, a custom `Edge.Set`/`TextAlignment`-bridging extension some future
 *  code could add, or a copy-pasted UIKit `NSTextAlignment.left` migrated carelessly could all
 *  reintroduce the physical spelling under a similar-looking call) -- a permanently-passing,
 *  forward-looking guard today, exactly like Check B3's currently-zero-match APIs, not a claim that any
 *  such call site compiles right now. */
const PHYSICAL_DIRECTION_PATTERNS = [
  { pattern: /\.padding\(\s*\.left\b/, label: '.padding(.left' },
  { pattern: /\.padding\(\s*\.right\b/, label: '.padding(.right' },
  { pattern: /\.multilineTextAlignment\(\s*\.left\s*\)/, label: '.multilineTextAlignment(.left)' },
  { pattern: /\.multilineTextAlignment\(\s*\.right\s*\)/, label: '.multilineTextAlignment(.right)' },
];

function checkC_noPhysicalDirectionApis(errors) {
  const files = getProdFiles();
  for (const { pattern, label } of PHYSICAL_DIRECTION_PATTERNS) {
    const matches = scanFiles(files, pattern);
    for (const m of matches) {
      errors.push(
        `C: physical-direction "${label}" found at ${relFile(m.file)}:${m.line} -- use the RTL-aware ` +
        `.leading/.trailing form instead (PHASE_5_IOS_SYSTEM_DESIGN.md § 13).`
      );
    }
  }
}

// ---------- Check Group D -- stripper self-guard (same precedent as theme-checks.js's F1) ----------

/** D1: asserts zero occurrences of the two-character sequence '#"' anywhere in the PROD files this
 *  checker itself scans -- checked against RAW (not stripped) text deliberately, mirroring
 *  theme-checks.js's F1 exactly (this file reuses that same stripSwiftComments(), which does not
 *  support Swift raw string literals). theme-checks.js's own F1 already guards the wider ALL (PROD +
 *  TESTS) file set at every CI run, so a real violation would already be caught there too -- this is a
 *  narrower, PROD-only re-assertion so THIS file's own checks are never silently mis-scanning a file
 *  theme-checks.js's F1 happens not to run (there is none today, but the two checkers are otherwise
 *  fully independent modules and neither should rely on the other's gate always running first). */
function checkD1_noUnsupportedRawStringLiterals(errors) {
  for (const file of getProdFiles()) {
    const raw = fs.readFileSync(file, 'utf8');
    const idx = raw.indexOf('#"');
    if (idx !== -1) {
      errors.push(
        `D1: found '#"' (Swift raw string literal syntax) at ${relFile(file)}:${lineAt(raw, idx)} -- ` +
        `the shared stripSwiftComments() does not support raw string literals and would mis-scan this ` +
        `file for every check above. Extend theme-checks.js's stripSwiftComments() before this file ` +
        `can be safely checked.`
      );
    }
  }
}

// ---------- CHECKS array ----------

const CHECKS = [
  { name: 'A: no String(localized:/NSLocalizedString(/LocalizedStringResource( outside MentoraStrings.swift', run: checkA_forbiddenApisOutsideMentoraStrings },
  { name: 'B1/B2: no hardcoded/interpolated Text("...") literal (PROD)', run: checkB1B2_textLiterals },
  { name: 'B3 (additive): no other hardcoded string-literal SwiftUI API call (PROD)', run: checkB3_otherStringLiteralApis },
  { name: 'B4 (additive, D94 heuristic): no string-literal default parameter value (PROD)', run: checkB4_defaultParameterStringLiteral },
  { name: 'C: no physical-direction .left/.right layout API (PROD)', run: checkC_noPhysicalDirectionApis },
  { name: 'D1: no unsupported raw string literals (#") in scanned PROD files', run: checkD1_noUnsupportedRawStringLiterals },
];

function runAllChecks() {
  const errors = [];
  for (const check of CHECKS) check.run(errors);
  return errors;
}

module.exports = {
  SANCTIONED_EXCEPTIONS,
  FORBIDDEN_APIS,
  OTHER_STRING_LITERAL_APIS,
  PHYSICAL_DIRECTION_PATTERNS,
  parseStringLiteral,
  checkA_forbiddenApisOutsideMentoraStrings,
  checkB1B2_textLiterals,
  checkB3_otherStringLiteralApis,
  checkB4_defaultParameterStringLiteral,
  checkC_noPhysicalDirectionApis,
  checkD1_noUnsupportedRawStringLiterals,
  runAllChecks,
  CHECKS,
};

if (require.main === module) {
  const files = getProdFiles();
  console.log(
    `iOS localization source-policy check: ${CHECKS.length} check group(s) run over ${files.length} ` +
    `production .swift file(s) under ${relFile(PROD_DIR)}.`
  );
  const errors = runAllChecks();
  if (errors.length) {
    console.error(`\n${errors.length} error(s):`);
    for (const e of errors) console.error(`  - ${e}`);
    console.error('\nCheck FAILED.');
    process.exit(1);
  }
  console.log('\nCheck PASSED.');
}
