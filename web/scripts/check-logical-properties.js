#!/usr/bin/env node
'use strict';

/**
 * Regex-based CI lint check for physical-direction CSS in source under src/.
 * Per architecture/WEB_ARCHITECTURE.md § 4 and design-system/DESIGN_RULES.md rule 16:
 * every layout property must be logical (start/end), never left/right, so RTL (Arabic)
 * mirrors automatically instead of needing per-locale branches.
 *
 * Two documented, intentional exceptions (design-system/LOCALIZATION.md § 2):
 *   - the video player scrubber/timeline (stays LTR in all locales)
 *   - the Mentora wordmark (never mirrors)
 * A line may opt out of this check with a trailing `// logical-properties-exempt: <reason>`
 * comment — used only for those two documented cases, never as a general escape hatch.
 */

const fs = require('fs');
const path = require('path');

const SRC_DIR = path.join(__dirname, '..', 'src');
const EXEMPT_MARKER = 'logical-properties-exempt:';

// Tailwind utility prefixes that hardcode a physical direction instead of a logical one.
const FORBIDDEN_TAILWIND = [
  /\bpl-\d/, /\bpr-\d/, /\bml-\d/, /\bmr-\d/,
  /\bleft-\d/, /\bright-\d/,
  /\btext-left\b/, /\btext-right\b/,
  /\brounded-l-/, /\brounded-r-/,
  /\bborder-l-/, /\bborder-r-/,
];

// Raw CSS/inline-style physical properties.
const FORBIDDEN_CSS_PROPS = [
  /\bpadding-left\s*:/, /\bpadding-right\s*:/,
  /\bmargin-left\s*:/, /\bmargin-right\s*:/,
  /\btext-align\s*:\s*left\b/, /\btext-align\s*:\s*right\b/,
  /\bborder-left\b/, /\bborder-right\b/,
  /^\s*left\s*:/, /^\s*right\s*:/,
];

const EXTENSIONS = new Set(['.ts', '.tsx', '.css']);

/** @returns {string[]} */
function walk(dir) {
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...walk(full));
    } else if (EXTENSIONS.has(path.extname(entry.name))) {
      out.push(full);
    }
  }
  return out;
}

function checkFile(file) {
  const violations = [];
  const lines = fs.readFileSync(file, 'utf8').split('\n');
  const patterns = file.endsWith('.css') ? FORBIDDEN_CSS_PROPS : FORBIDDEN_TAILWIND.concat(FORBIDDEN_CSS_PROPS);

  lines.forEach((line, i) => {
    if (line.includes(EXEMPT_MARKER)) return;
    for (const pattern of patterns) {
      if (pattern.test(line)) {
        violations.push({ file, line: i + 1, text: line.trim(), pattern: pattern.source });
        break;
      }
    }
  });
  return violations;
}

const files = fs.existsSync(SRC_DIR) ? walk(SRC_DIR) : [];
const allViolations = files.flatMap(checkFile);

if (allViolations.length > 0) {
  console.error('Physical-direction CSS found (use logical start/end properties instead):\n');
  for (const v of allViolations) {
    console.error(`  ${path.relative(process.cwd(), v.file)}:${v.line}  ${v.text}`);
  }
  console.error(`\n${allViolations.length} violation(s). See design-system/LOCALIZATION.md § 1.`);
  process.exit(1);
}

console.log('No physical-direction (left/right) CSS found under src/.');
