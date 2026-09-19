#!/usr/bin/env node
'use strict';

/**
 * Mentora iOS Component Kit A Completion-Gate Checker (Phase 5 Task T8, final slice 6+7)
 *
 * Plain Node, no external dependencies -- same precedent as `theme-checks.js`/`assets-check.js`/
 * `catalog-parity.js`/`localization-checks.js` (execution/DECISIONS_LOG.md D35): this checker's whole
 * job is source-TEXT policy over plain `.swift` files, which a hand-rolled Node script can prove real
 * things about without a Swift compiler on this Windows host.
 *
 * This is a NEW file, closing out Task T8's own "Component Kit A" atom set: it asserts every one of the
 * 14 real atom types this task built (across 7 slices) is still declared somewhere in the app target --
 * a regression guard against a future rename/removal silently breaking the kit's own completion
 * criterion, the same role `theme-checks.js`'s Check Group E plays for the token gallery.
 *
 * Reuses `theme-checks.js`'s exported `stripSwiftComments` directly (matching the reuse convention
 * `catalog-parity.js`/`localization-checks.js` already established for that same function) -- every
 * atom is searched for over COMMENT-STRIPPED text, so a commented-out `struct MentoraFoo: View` can
 * never produce a false PASS. `theme-checks.js` does not export its own file-walking helper
 * (`listSwiftFiles`/`getProdFiles` are private to that module), so this file has its own small,
 * equivalent walker below -- the same "no external glob dependency" shape every checker in this family
 * already uses.
 *
 * 13 of the 14 atoms live under `mobile/iosApp/iosApp/Components/` (this task's own file scope). The
 * 14th, `MentoraIcon`, was built in an earlier task (T3) and lives under `mobile/iosApp/iosApp/Theme/`
 * (`Theme/MentoraIcon.swift`) -- a real, already-existing location, not a Components/ atom this task
 * relocated. This checker therefore requires the first 13 specifically under Components/, and
 * `MentoraIcon` anywhere under the app target (confirmed by direct file read before writing this script
 * to be `Theme/MentoraIcon.swift`'s `struct MentoraIcon: View`).
 *
 * Exit code 0 = all 14 atoms confirmed present. Non-zero = at least one missing, named on stderr.
 */

const fs = require('fs');
const path = require('path');
const { stripSwiftComments } = require('./theme-checks');

const ROOT = path.resolve(__dirname, '..', '..');
const PROD_DIR = path.join(ROOT, 'mobile', 'iosApp', 'iosApp');
const COMPONENTS_DIR = path.join(PROD_DIR, 'Components');

/**
 * The 14 real atom types Task T8's "Component Kit A" comprises, across its 7 slices. `requireUnder`
 * names the directory (relative to `PROD_DIR`) the type's declaration must be found under -- `null`
 * means "anywhere under the app target" (see this file's header for why `MentoraIcon` alone gets that
 * looser requirement).
 */
const REQUIRED_ATOMS = [
  { name: 'MentoraButton', requireUnder: 'Components' },
  { name: 'MentoraIconButton', requireUnder: 'Components' },
  { name: 'MentoraTextField', requireUnder: 'Components' },
  { name: 'PasswordField', requireUnder: 'Components' },
  { name: 'SearchField', requireUnder: 'Components' },
  { name: 'MentoraToggle', requireUnder: 'Components' },
  { name: 'MentoraSelect', requireUnder: 'Components' },
  { name: 'Badge', requireUnder: 'Components' },
  { name: 'CategoryChip', requireUnder: 'Components' },
  { name: 'MentoraProgressBar', requireUnder: 'Components' },
  { name: 'Avatar', requireUnder: 'Components' },
  { name: 'MentoraTabs', requireUnder: 'Components' },
  { name: 'MentoraSnackbar', requireUnder: 'Components' },
  { name: 'MentoraIcon', requireUnder: null },
];

/** Recursive `.swift` file walker -- no external glob dependency, matching this repo's zero-external-
 *  deps convention (same as every other `tools/ios-checks/*.js` file). Tolerates a missing root
 *  directory (returns []). */
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

function relFile(file) {
  return path.relative(ROOT, file).split(path.sep).join('/');
}

/**
 * Builds the regex for one atom's declaration: `struct <Name>` or `enum <Name>` (a bare type name
 * boundary), optionally followed by a generic clause (`<...>`, e.g. `MentoraSelect<Value: Hashable>`),
 * then `: View` before the opening brace. All 14 real atoms here are `struct ... : View` (confirmed by
 * reading each file's real declaration directly before writing this script, including the two generic
 * cases -- `MentoraSelect<Value: Hashable>: View` and `MentoraTabs<Value: Hashable>: View`) -- `enum` is
 * accepted too for robustness, though unused by any of the 14 today.
 */
function declarationRegex(name) {
  return new RegExp(`\\b(struct|enum)\\s+${name}\\b[^\\n{]*:\\s*View\\b`);
}

/** Scans `files` (already comment-stripped per file) for `regex`; returns the list of files with a hit. */
function filesMatching(files, regex) {
  const hits = [];
  for (const file of files) {
    const raw = fs.readFileSync(file, 'utf8');
    const stripped = stripSwiftComments(raw);
    if (regex.test(stripped)) hits.push(file);
  }
  return hits;
}

function runAllChecks() {
  const errors = [];
  const confirmed = [];

  const componentsFiles = listSwiftFiles(COMPONENTS_DIR);
  const allProdFiles = listSwiftFiles(PROD_DIR);

  for (const atom of REQUIRED_ATOMS) {
    const regex = declarationRegex(atom.name);
    const searchSet = atom.requireUnder === 'Components' ? componentsFiles : allProdFiles;
    const hits = filesMatching(searchSet, regex);

    if (hits.length === 0) {
      const where =
        atom.requireUnder === 'Components'
          ? `${relFile(COMPONENTS_DIR)}/`
          : `${relFile(PROD_DIR)}/ (anywhere in the app target)`;
      errors.push(
        `Missing atom "${atom.name}": expected to find "struct ${atom.name}...: View" (or ` +
        `"enum ${atom.name}...: View") somewhere under ${where}, found zero matches.`
      );
      continue;
    }

    confirmed.push({ name: atom.name, files: hits.map(relFile) });
  }

  return { errors, confirmed };
}

module.exports = {
  REQUIRED_ATOMS,
  listSwiftFiles,
  declarationRegex,
  runAllChecks,
};

if (require.main === module) {
  const { errors, confirmed } = runAllChecks();

  console.log(`iOS Component Kit A completion-gate check: ${REQUIRED_ATOMS.length} required atom(s).`);

  if (errors.length) {
    console.error(`\n${errors.length} error(s):`);
    for (const e of errors) console.error(`  - ${e}`);
    console.error('\nCheck FAILED. Task T8 is not complete -- one or more of its 14 atoms is missing.');
    process.exit(1);
  }

  console.log(`\nAll ${confirmed.length} atoms confirmed present:`);
  for (const atom of confirmed) {
    console.log(`  - ${atom.name} (${atom.files.join(', ')})`);
  }
  console.log('\nCheck PASSED. Task T8 (Component Kit A, 14 atoms) is complete.');
}
