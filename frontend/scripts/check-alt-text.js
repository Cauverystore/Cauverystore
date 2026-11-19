#!/usr/bin/env node
/**
 * Alt text check: runs before every build (npm "prebuild") and fails it when
 *   1. an <img> in src/ has no alt text, or a fixed English one instead of
 *      a translated description from the useAltText helper, or
 *   2. a language file is missing one of the image descriptions in en.json
 *      (the "alt" section), or a translation dropped a {{placeholder}}.
 *
 * A purely decorative image can opt out with alt="" plus aria-hidden="true".
 * Run it on its own with: npm run check:alt
 */
const fs = require("fs");
const path = require("path");

const SRC = path.join(__dirname, "..", "src");
const I18N = path.join(SRC, "i18n");
const LANGS = ["en", "ta", "hi", "te", "kn", "ml"];
const problems = [];

// ---- 1. Every <img> has translated alt text ---------------------------------
function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) return walk(p);
    return /\.(jsx?|tsx?)$/.test(e.name) ? [p] : [];
  });
}

// Reads one JSX tag starting at "<img", skipping over {...} expressions and quoted strings.
function readTag(text, start) {
  let depth = 0;
  let quote = null;
  for (let i = start + 1; i < text.length; i++) {
    const c = text[i];
    if (quote) { if (c === quote && text[i - 1] !== "\\") quote = null; continue; }
    if (c === "{") depth++;
    else if (c === "}") depth--;
    else if (depth > 0 && (c === '"' || c === "'" || c === "`")) quote = c;
    else if (depth === 0 && c === '"') quote = c;
    else if (depth === 0 && c === ">") return text.slice(start, i + 1);
  }
  return text.slice(start);
}

const imgCount = { total: 0 };
for (const file of walk(SRC)) {
  const text = fs.readFileSync(file, "utf8");
  const re = /<img\b/g;
  let m;
  while ((m = re.exec(text))) {
    imgCount.total++;
    const tag = readTag(text, m.index);
    const line = text.slice(0, m.index).split("\n").length;
    const where = `${path.relative(path.join(__dirname, ".."), file)}:${line}`;
    const alt = tag.match(/\balt=(\{|"([^"]*)"|'([^']*)')/);
    if (!alt) { problems.push(`${where}  image has no alt text`); continue; }
    if (alt[1] === "{") continue; // comes from code (the useAltText helper)
    const value = alt[2] ?? alt[3];
    if (value === "" && /aria-hidden=("true"|\{true\})/.test(tag)) continue; // decorative, on purpose
    problems.push(value === ""
      ? `${where}  alt="" (empty). Use the useAltText helper, or add aria-hidden="true" if the image is purely decorative`
      : `${where}  alt="${value}" is fixed English text. Use the useAltText helper so it is translated`);
  }
}

// ---- 2. Every language has every image description ---------------------------
const placeholders = (s) => (String(s).match(/\{\{\s*\w+\s*\}\}/g) || []).map((p) => p.replace(/\s/g, "")).sort().join(",");
const load = (lang) => JSON.parse(fs.readFileSync(path.join(I18N, `${lang}.json`), "utf8"));
const en = load("en").alt || {};
if (!Object.keys(en).length) problems.push('src/i18n/en.json has no "alt" section');
for (const lang of LANGS.slice(1)) {
  const alt = load(lang).alt || {};
  for (const [key, value] of Object.entries(en)) {
    const v = alt[key];
    if (typeof v !== "string" || !v.trim()) problems.push(`src/i18n/${lang}.json  missing image description alt.${key}`);
    else if (placeholders(v) !== placeholders(value)) problems.push(`src/i18n/${lang}.json  alt.${key} must keep ${placeholders(value) || "no placeholders"}`);
  }
}

if (problems.length) {
  console.error(`\nAlt text check failed (${problems.length}):\n  ` + problems.join("\n  ") + "\n");
  process.exit(1);
}
console.log(`Alt text check passed: ${imgCount.total} images, ${Object.keys(en).length} descriptions in ${LANGS.length} languages.`);
