#!/usr/bin/env node
'use strict';

/**
 * Mentora iOS Navigation Shell Completion-Gate Checker (Phase 5 Task T9)
 *
 * Plain Node, no external dependencies -- same precedent as `theme-checks.js`/`assets-check.js`/
 * `catalog-parity.js`/`localization-checks.js`/`component-checks.js` (execution/DECISIONS_LOG.md D35):
 * this checker's whole job is source-TEXT policy over plain `.swift` files, which a hand-rolled Node
 * script can prove real things about without a Swift compiler on this Windows host.
 *
 * Reuses `theme-checks.js`'s exported `stripSwiftComments` directly (same reuse convention
 * `localization-checks.js`/`component-checks.js` already established) -- every check below runs against
 * COMMENT-STRIPPED text unless explicitly noted otherwise (Check E1's marker-comment search and Check F1
 * both need RAW text -- see each check's own comment for why).
 *
 * Named in / enforces:
 *   - `PHASE_5_IOS_SYSTEM_DESIGN.md` § 3.1 -- TabRouter's five-stored-properties rule, never a
 *     `[Tab: [Route]]` dictionary (Checks B1/B2).
 *   - `PHASE_5_IOS_SYSTEM_DESIGN.md` § 10 / `PHASE_5_ACCEPTANCE_CRITERIA.md` D1-D8 -- the one shared
 *     destination table (Check C1), D5's tab-bar-hiding wiring (Check C2).
 *   - Acceptance criterion A3 -- no `.shared` singleton access, single-construction-site guarantee for
 *     `MentoraClient`/`AppEnvironment` (Check D1).
 *   - This task's own scope discipline -- every placeholder view built in slice 2 lives in ONE file with
 *     a `// TEMPORARY (T9)` marker, so a future task can grep exactly what remains to replace (Check E1).
 *
 * Exit code 0 = all checks passed. Non-zero = at least one failure, printed to stderr.
 */

const fs = require('fs');
const path = require('path');
const { stripSwiftComments } = require('./theme-checks');

const ROOT = path.resolve(__dirname, '..', '..');
const PROD_DIR = path.join(ROOT, 'mobile', 'iosApp', 'iosApp');
const TESTS_DIRS = [
  path.join(ROOT, 'mobile', 'iosApp', 'iosAppTests'),
  path.join(ROOT, 'mobile', 'iosApp', 'iosAppUITests'),
];
const NAVIGATION_DIR = path.join(PROD_DIR, 'Navigation');
const SUPPORT_DIR = path.join(PROD_DIR, 'Support');
const APP_FILE = path.join(PROD_DIR, 'MentoraApp.swift');
const APP_ENVIRONMENT_FILE = path.join(SUPPORT_DIR, 'AppEnvironment.swift');
const TAB_ROUTER_FILE = path.join(NAVIGATION_DIR, 'TabRouter.swift');
const PLACEHOLDERS_FILE = path.join(NAVIGATION_DIR, 'TabRootPlaceholders.swift');

/** The 6 `Navigation/*.swift` files this task creates (Check A1). */
const REQUIRED_NAVIGATION_FILES = [
  'Route.swift',
  'TabRouter.swift',
  'TabShell.swift',
  'RootView.swift',
  'TabRootPlaceholders.swift',
  'AuthGate.swift',
];

/** The 5 stored per-tab path properties `TabRouter` must declare (Check B1). */
const REQUIRED_PATH_PROPERTIES = ['home', 'explore', 'myLearning', 'aiTutor', 'profile'];

/**
 * The 8 slice-2 placeholder view types (5 tab roots + 3 pushed destinations) -- required to live
 * together in ONE file (`Navigation/TabRootPlaceholders.swift`), each marked `// TEMPORARY (T9)`
 * (Check E1). `LoginSheetPlaceholderView` (slice 3, `Navigation/AuthGate.swift`) is deliberately NOT in
 * this list -- the plan explicitly allows it to live in either file, since it was not built in slice 2.
 */
const REQUIRED_PLACEHOLDER_VIEWS = [
  'HomePlaceholderView',
  'ExplorePlaceholderView',
  'MyLearningPlaceholderView',
  'AITutorPlaceholderView',
  'ProfilePlaceholderView',
  'CourseDetailsPlaceholderView',
  'CoursePlayerPlaceholderView',
  'QuizPlaceholderView',
];

const TEMPORARY_MARKER = '// TEMPORARY (T9)';

// ---------- file-set helpers (same recursive-walk pattern as the sibling checkers) ----------

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

function getProdFiles() {
  return listSwiftFiles(PROD_DIR);
}

function getTestsFiles() {
  let out = [];
  for (const dir of TESTS_DIRS) out = out.concat(listSwiftFiles(dir));
  return out;
}

function getAllFiles() {
  return getProdFiles().concat(getTestsFiles());
}

function relFile(file) {
  return path.relative(ROOT, file).split(path.sep).join('/');
}

function isSameFile(file, target) {
  return path.resolve(file) === path.resolve(target);
}

function lineAt(text, index) {
  let line = 1;
  for (let i = 0; i < index && i < text.length; i++) {
    if (text[i] === '\n') line++;
  }
  return line;
}

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

/** Scans every file in `files` for `regex`, over comment-STRIPPED text (unless `useRaw` is set). */
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

// ---------- Check A1 -- the 6 Navigation/*.swift files exist ----------

function checkA1_navigationFilesExist(errors) {
  for (const name of REQUIRED_NAVIGATION_FILES) {
    const full = path.join(NAVIGATION_DIR, name);
    if (!fs.existsSync(full)) {
      errors.push(`A1: expected ${relFile(full)} to exist (Task T9's navigation shell) -- not found.`);
    }
  }
}

// ---------- Check B1/B2 -- the five-stored-properties rule (§ 3.1) ----------

/** B1: TabRouter.swift declares all five `var home/explore/myLearning/aiTutor/profile: [Route]`
 *  (an optional `private(set)`/other prefix before `var` is tolerated -- only the property's own name
 *  and `[Route]` type are asserted). */
function checkB1_tabRouterDeclaresFiveStoredPaths(errors) {
  if (!fs.existsSync(TAB_ROUTER_FILE)) return; // A1 already reports this
  const stripped = stripSwiftComments(fs.readFileSync(TAB_ROUTER_FILE, 'utf8'));
  for (const name of REQUIRED_PATH_PROPERTIES) {
    const re = new RegExp(`\\bvar\\s+${name}\\s*:\\s*\\[\\s*Route\\s*\\]`);
    if (!re.test(stripped)) {
      errors.push(
        `B1: ${relFile(TAB_ROUTER_FILE)} is missing a stored "var ${name}: [Route]" property -- ` +
        `System Design § 3.1 requires exactly these 5 stored properties, never a dictionary.`
      );
    }
  }
}

/** B2: NO "[Tab: [Route]]" dictionary type anywhere in the app target -- § 3.1's central prohibition,
 *  made a real gate instead of only a code comment. */
function checkB2_noTabToRouteDictionary(errors) {
  const re = /\[\s*Tab\s*:\s*\[\s*Route\s*\]\s*\]/;
  const matches = scanFiles(getProdFiles(), re);
  for (const m of matches) {
    errors.push(
      `B2: banned "[Tab: [Route]]" dictionary type found at ${relFile(m.file)}:${m.line} -- ` +
      `TabRouter must use its 5 stored properties instead (System Design § 3.1); a dictionary subscript ` +
      `cannot produce the real Binding<[Route]> NavigationStack(path:) needs.`
    );
  }
}

/** B3: "func path(for" appears ONLY inside TabRouter.swift -- the one permitted indirection over the
 *  five stored properties must not be reimplemented or duplicated elsewhere. */
function checkB3_pathForOnlyInTabRouter(errors) {
  const matches = scanFiles(getProdFiles(), /\bfunc\s+path\(for\b/);
  for (const m of matches) {
    if (!isSameFile(m.file, TAB_ROUTER_FILE)) {
      errors.push(
        `B3: "func path(for" found outside ${relFile(TAB_ROUTER_FILE)} at ${relFile(m.file)}:${m.line} ` +
        `-- this must stay TabRouter's own single indirection point.`
      );
    }
  }
}

// ---------- Check C1/C2 -- the one shared destination table (D4/D5) ----------

/** C1: ".navigationDestination(for:" appears EXACTLY ONCE across the whole app target -- the structural
 *  guarantee behind D4's "one shared destination table." */
function checkC1_navigationDestinationExactlyOnce(errors) {
  const matches = scanFiles(getProdFiles(), /\.navigationDestination\(for:/);
  if (matches.length !== 1) {
    errors.push(
      `C1: expected exactly 1 ".navigationDestination(for:" call across the app target, found ` +
      `${matches.length}` +
      (matches.length ? `: ${matches.map((m) => `${relFile(m.file)}:${m.line}`).join(', ')}` : '.')
    );
  }
}

/** C2: ".toolbar(.hidden, for: .tabBar)" appears at least once -- D5's tab-bar-hiding wiring is present. */
function checkC2_tabBarHidingWiringPresent(errors) {
  const matches = scanFiles(getProdFiles(), /\.toolbar\(\s*\.hidden\s*,\s*for:\s*\.tabBar\s*\)/);
  if (matches.length === 0) {
    errors.push(
      `C2: expected at least one ".toolbar(.hidden, for: .tabBar)" call somewhere in the app target ` +
      `(D5) -- none found.`
    );
  }
}

// ---------- Check D1 -- A3's single-construction-site guarantee ----------

/** D1a: no ".shared" static access anywhere in Navigation/*.swift (A3). */
function checkD1a_noSharedStaticInNavigation(errors) {
  const matches = scanFiles(listSwiftFiles(NAVIGATION_DIR), /\.shared\b/);
  for (const m of matches) {
    errors.push(
      `D1: banned ".shared" singleton access found at ${relFile(m.file)}:${m.line} -- Navigation types ` +
      `must receive every dependency explicitly (A3), never reach a static singleton themselves.`
    );
  }
}

/** D1b: "MentoraClient(" / "AppEnvironment(" real CONSTRUCTOR CALLS (an open paren immediately after
 *  the type name -- a plain type annotation like "let client: MentoraClient" never matches this) appear
 *  ONLY in Support/AppEnvironment.swift or MentoraApp.swift -- the single-instance guarantee (A3), not
 *  previously an automated check in any prior gate file. */
function checkD1b_singleConstructionSite(errors) {
  const patterns = [
    { re: /\bMentoraClient\(/, label: 'MentoraClient(' },
    { re: /\bAppEnvironment\(/, label: 'AppEnvironment(' },
  ];
  for (const { re, label } of patterns) {
    const matches = scanFiles(getProdFiles(), re);
    for (const m of matches) {
      if (isSameFile(m.file, APP_ENVIRONMENT_FILE) || isSameFile(m.file, APP_FILE)) continue;
      errors.push(
        `D1: "${label}" construction found outside ${relFile(APP_ENVIRONMENT_FILE)}/${relFile(APP_FILE)} ` +
        `at ${relFile(m.file)}:${m.line} -- A3 requires exactly one construction site.`
      );
    }
  }
}

// ---------- Check E1 -- slice-2 placeholders: one file, each marked TEMPORARY (T9) ----------

/** E1: every one of the 8 required placeholder view types (a) is declared as "struct <Name>" ONLY in
 *  Navigation/TabRootPlaceholders.swift (never duplicated/relocated elsewhere in PROD), and (b) carries
 *  a "// TEMPORARY (T9)" marker comment within the 5 raw lines immediately above its declaration line --
 *  checked against RAW (unstripped) text for the marker search specifically, since the marker IS a
 *  comment and would vanish under stripSwiftComments(). Declaration line numbers themselves are found
 *  against stripped text first (so a commented-out "struct FooPlaceholderView" elsewhere can never
 *  produce a false PASS or a wrong line number), then mapped back to the identical raw line (stripping
 *  preserves every newline verbatim, so line numbers agree between the two texts). */
function checkE1_placeholdersInOneFileMarkedTemporary(errors) {
  const allProdFiles = getProdFiles();

  for (const name of REQUIRED_PLACEHOLDER_VIEWS) {
    const declRe = new RegExp(`\\bstruct\\s+${name}\\b[^\\n{]*:\\s*View\\b`);
    const hits = [];
    for (const file of allProdFiles) {
      const stripped = stripSwiftComments(fs.readFileSync(file, 'utf8'));
      const m = declRe.exec(stripped);
      if (m) hits.push({ file, line: lineAt(stripped, m.index) });
    }

    if (hits.length === 0) {
      errors.push(`E1: no "struct ${name}: View" declaration found anywhere in the app target.`);
      continue;
    }
    if (hits.length > 1) {
      errors.push(
        `E1: "${name}" is declared more than once (${hits.map((h) => `${relFile(h.file)}:${h.line}`).join(', ')}) ` +
        `-- every slice-2 placeholder must live in exactly one file, ${relFile(PLACEHOLDERS_FILE)}.`
      );
      continue;
    }

    const [{ file, line }] = hits;
    if (!isSameFile(file, PLACEHOLDERS_FILE)) {
      errors.push(
        `E1: "${name}" is declared in ${relFile(file)}:${line}, expected ${relFile(PLACEHOLDERS_FILE)} ` +
        `-- every slice-2 placeholder must live together in that one file.`
      );
      continue;
    }

    const rawLines = fs.readFileSync(file, 'utf8').split('\n');
    const searchStart = Math.max(0, line - 1 - 5);
    const nearbyRaw = rawLines.slice(searchStart, line).join('\n');
    if (!nearbyRaw.includes(TEMPORARY_MARKER)) {
      errors.push(
        `E1: "${name}" at ${relFile(file)}:${line} has no "${TEMPORARY_MARKER}" marker comment within ` +
        `the 5 lines immediately above its declaration.`
      );
    }
  }
}

// ---------- Check F1 -- stripper self-guard (same precedent as theme-checks.js's F1) ----------

function checkF1_noUnsupportedRawStringLiterals(errors) {
  for (const file of getAllFiles()) {
    const raw = fs.readFileSync(file, 'utf8');
    const idx = raw.indexOf('#"');
    if (idx !== -1) {
      errors.push(
        `F1: found '#"' (Swift raw string literal syntax) at ${relFile(file)}:${lineAt(raw, idx)} -- ` +
        `stripSwiftComments() does not support raw string literals and would mis-scan this file. Extend ` +
        `theme-checks.js's stripper before this file can be safely checked by any check above.`
      );
    }
  }
}

// ---------- CHECKS array ----------

const CHECKS = [
  { name: 'A1: the 6 Navigation/*.swift files exist', run: checkA1_navigationFilesExist },
  { name: 'B1: TabRouter declares all 5 stored [Route] path properties', run: checkB1_tabRouterDeclaresFiveStoredPaths },
  { name: 'B2: no [Tab: [Route]] dictionary anywhere (PROD)', run: checkB2_noTabToRouteDictionary },
  { name: 'B3: func path(for only in TabRouter.swift (PROD)', run: checkB3_pathForOnlyInTabRouter },
  { name: 'C1: .navigationDestination(for: exactly once (PROD)', run: checkC1_navigationDestinationExactlyOnce },
  { name: 'C2: .toolbar(.hidden, for: .tabBar) present at least once (PROD)', run: checkC2_tabBarHidingWiringPresent },
  { name: 'D1a: no .shared singleton access in Navigation/*.swift', run: checkD1a_noSharedStaticInNavigation },
  { name: 'D1b: MentoraClient(/AppEnvironment( constructed only in AppEnvironment.swift/MentoraApp.swift', run: checkD1b_singleConstructionSite },
  { name: 'E1: slice-2 placeholders live in one file, each marked // TEMPORARY (T9)', run: checkE1_placeholdersInOneFileMarkedTemporary },
  { name: 'F1: no unsupported raw string literals (#") in scanned files', run: checkF1_noUnsupportedRawStringLiterals },
];

function runAllChecks() {
  const errors = [];
  for (const check of CHECKS) check.run(errors);
  return errors;
}

module.exports = {
  REQUIRED_NAVIGATION_FILES,
  REQUIRED_PATH_PROPERTIES,
  REQUIRED_PLACEHOLDER_VIEWS,
  checkA1_navigationFilesExist,
  checkB1_tabRouterDeclaresFiveStoredPaths,
  checkB2_noTabToRouteDictionary,
  checkB3_pathForOnlyInTabRouter,
  checkC1_navigationDestinationExactlyOnce,
  checkC2_tabBarHidingWiringPresent,
  checkD1a_noSharedStaticInNavigation,
  checkD1b_singleConstructionSite,
  checkE1_placeholdersInOneFileMarkedTemporary,
  checkF1_noUnsupportedRawStringLiterals,
  runAllChecks,
  CHECKS,
};

if (require.main === module) {
  const errors = runAllChecks();
  console.log(`iOS navigation shell completion-gate check: ${CHECKS.length} check group(s) run.`);
  if (errors.length) {
    console.error(`\n${errors.length} error(s):`);
    for (const e of errors) console.error(`  - ${e}`);
    console.error('\nCheck FAILED.');
    process.exit(1);
  }
  console.log('\nCheck PASSED.');
}
