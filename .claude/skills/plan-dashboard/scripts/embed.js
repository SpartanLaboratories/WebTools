#!/usr/bin/env node
// Embed a plan folder's plan.json into its dashboard.html, verbatim, so the page renders from one committed file.
// usage: node embed.js <plan-folder>   (the folder holds plan.json and dashboard.html)
"use strict";
const fs = require("fs"), path = require("path");
const OPEN = '<script type="application/json" id="plan">', CLOSE = "</script>";

function embed(dir) {
  const jsonPath = path.join(dir, "plan.json"), htmlPath = path.join(dir, "dashboard.html");
  const json = fs.readFileSync(jsonPath, "utf8");
  JSON.parse(json); // refuse to embed a file that does not parse
  let html = fs.readFileSync(htmlPath, "utf8");
  const a = html.indexOf(OPEN);
  if (a < 0) throw new Error(`no <script id="plan"> block in ${htmlPath}`);
  const b = html.indexOf(CLOSE, a + OPEN.length);
  if (b < 0) throw new Error(`unterminated plan block in ${htmlPath}`);
  // "</script" inside a JSON string would end the block early; "<\/script" is the same string once parsed.
  const body = "\n" + json.replace(/<\/script/gi, "<\\/script").replace(/\s*$/, "") + "\n";
  html = html.slice(0, a + OPEN.length) + body + html.slice(b);
  fs.writeFileSync(htmlPath, html);
  return { jsonPath, htmlPath, bytes: Buffer.byteLength(json) };
}

if (require.main === module) {
  const dir = process.argv[2];
  if (!dir) { console.error("usage: node embed.js <plan-folder>"); process.exit(2); }
  try { const r = embed(dir); console.log(`embedded ${r.jsonPath} into ${r.htmlPath} (${r.bytes} bytes)`); }
  catch (e) { console.error("embed failed: " + e.message); process.exit(1); }
}
module.exports = { embed, OPEN, CLOSE };
