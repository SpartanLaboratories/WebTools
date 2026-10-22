#!/usr/bin/env node
// Check a plan folder: plan.json keeps the rules of the plan-dashboard skill, and dashboard.html embeds it verbatim.
// usage: node check.js <plan-folder>     exit 0 when every rule holds, 1 otherwise. Warnings never fail the run.
"use strict";
const fs = require("fs"), path = require("path");
const { OPEN, CLOSE } = require("./embed.js");

const STATUS = ["planned", "building", "built", "abandoned"];
const STATES = ["pending", "as-planned", "amended", "deviated", "dropped", "deferred"];
const VERDICTS = ["accepted", "declined", "deferred"];
const KINDS = ["bug", "missing", "debt", "risk"];
const COLUMNS = ["gap", "considered", "verdict", "built"];
const DATE = /^\d{4}-\d{2}-\d{2}$/, ID = /^[a-z0-9][a-z0-9-]*$/, SHA = /^[0-9a-f]{7,40}$/;

const out = { fail: [], warn: [], ok: [] };
const fail = m => out.fail.push(m), warn = m => out.warn.push(m), ok = m => out.ok.push(m);
const str = v => typeof v === "string" && v.trim().length > 0;

function placeholders(v, at, found) {
  if (typeof v === "string") { if (/^<[^>]*>$/.test(v.trim()) || /<(issue|slug|YYYY-MM-DD|sha)[^>]*>/.test(v)) found.push(at); }
  else if (Array.isArray(v)) v.forEach((x, i) => placeholders(x, `${at}[${i}]`, found));
  else if (v && typeof v === "object") Object.keys(v).forEach(k => placeholders(v[k], at ? `${at}.${k}` : k, found));
  return found;
}

function checkPlan(plan, folderName) {
  if (plan.schema !== "plan-dashboard/1") fail(`schema must be "plan-dashboard/1" (got ${JSON.stringify(plan.schema)})`);
  if (!str(plan.repo) || !/^[^/\s]+\/[^/\s]+$/.test(plan.repo)) fail("repo must be owner/name");
  if (!Number.isInteger(plan.issue) || plan.issue <= 0) fail("issue must be a positive integer");
  if (!str(plan.slug)) fail("slug is required"); else if (folderName && plan.slug !== folderName) fail(`slug "${plan.slug}" must equal the folder name "${folderName}"`);
  if (Number.isInteger(plan.issue) && str(plan.slug) && !plan.slug.startsWith(plan.issue + "-")) fail(`slug must start with "${plan.issue}-"`);
  ["title", "function"].forEach(k => { if (!str(plan[k])) fail(`${k} is required`); });
  if (!STATUS.includes(plan.status)) fail(`status must be one of ${STATUS.join(", ")}`);
  const b = plan.baseline || {};
  if (!str(b.ref) || !str(b.sha) || !str(b.date)) fail("baseline needs ref, sha and date");
  if (str(b.sha) && !SHA.test(b.sha)) fail(`baseline.sha "${b.sha}" is not a commit sha`);
  if (str(b.date) && !DATE.test(b.date)) fail(`baseline.date "${b.date}" is not YYYY-MM-DD`);
  if (plan.status === "built") {
    const a = plan.asBuilt || {};
    if (!str(a.sha) || !str(a.date)) fail("a built plan needs asBuilt.sha and asBuilt.date");
    if (str(a.sha) && !SHA.test(a.sha)) fail(`asBuilt.sha "${a.sha}" is not a commit sha`);
    if (str(a.date) && !DATE.test(a.date)) fail(`asBuilt.date "${a.date}" is not YYYY-MM-DD`);
    if (!(plan.milestones || []).some(m => m.column === "built")) warn("a built plan usually carries a \"built\" milestone (the merge or the release)");
  } else if (plan.status === "planned" && plan.asBuilt) fail("a planned plan has asBuilt: null");

  const systems = Array.isArray(plan.systems) ? plan.systems : [];
  systems.forEach((s, i) => { if (!str(s.slug) || !str(s.name)) fail(`systems[${i}] needs slug and name`); });
  if (systems.length > 6) warn("more than six systems: the seventh and later fold into the neutral colour");

  (plan.milestones || []).forEach((m, i) => {
    if (!COLUMNS.includes(m.column)) fail(`milestones[${i}].column must be one of ${COLUMNS.join(", ")}`);
    if (!str(m.label)) fail(`milestones[${i}] needs a label`);
    if (m.date && !DATE.test(m.date)) fail(`milestones[${i}].date "${m.date}" is not YYYY-MM-DD`);
  });
  ["gap", "considered", "verdict"].forEach(c => { if (!(plan.milestones || []).some(m => m.column === c)) warn(`no "${c}" milestone: the column header will carry no date`); });
  (plan.trail || []).forEach((t, i) => { if (t.date && !DATE.test(t.date)) fail(`trail[${i}].date "${t.date}" is not YYYY-MM-DD`); if (t.column && !COLUMNS.includes(t.column)) fail(`trail[${i}].column must be one of ${COLUMNS.join(", ")}`); });

  const rows = Array.isArray(plan.rows) ? plan.rows : [];
  if (!rows.length) fail("rows is empty: a plan needs at least one part");
  const ids = new Set();
  rows.forEach((r, i) => {
    const at = `rows[${i}]${r.id ? ` (${r.id})` : ""}`;
    if (!str(r.id) || !ID.test(r.id)) fail(`${at}: id must be kebab-case`); else if (ids.has(r.id)) fail(`${at}: duplicate id`); else ids.add(r.id);
    if (!str(r.name)) fail(`${at}: name is required`);
    if (!str(r.system)) fail(`${at}: system is required`); else if (systems.length && !systems.some(s => s.slug === r.system)) warn(`${at}: system "${r.system}" is not in plan.systems; it gets the next colour slot`);
    const g = r.gap || {};
    if (!str(g.summary)) fail(`${at}: gap.summary is required (the hole, never the fix)`);
    if (g.kind && !KINDS.includes(g.kind)) fail(`${at}: gap.kind must be one of ${KINDS.join(", ")}`);
    if (!Array.isArray(g.evidence) || !g.evidence.length) warn(`${at}: gap.evidence is empty; cite path:line at the baseline`);
    const os = Array.isArray(r.options) ? r.options : [];
    if (!os.length) fail(`${at}: options is empty; record every option weighed, the one taken included`);
    const oids = new Set(); let accepted = 0;
    os.forEach((o, j) => {
      const oat = `${at}.options[${j}]`;
      if (!str(o.id) || !ID.test(o.id)) fail(`${oat}: id must be kebab-case`); else if (oids.has(o.id)) fail(`${oat}: duplicate option id`); else oids.add(o.id);
      if (!str(o.name)) fail(`${oat}: name is required`);
      if (!VERDICTS.includes(o.verdict)) fail(`${oat}: verdict must be one of ${VERDICTS.join(", ")}`);
      if (o.verdict === "accepted") accepted++;
      if (o.verdict !== "accepted" && !str(o.why)) fail(`${oat}: a ${o.verdict} option needs "why"`);
    });
    if (accepted !== 1) fail(`${at}: exactly one option must be accepted (found ${accepted}); for a part left alone, the accepted option is the one that leaves it`);
    const v = r.verdict || {};
    if (!str(v.summary)) fail(`${at}: verdict.summary is required`);
    if (v.kind && !["build", "leave"].includes(v.kind)) fail(`${at}: verdict.kind must be build or leave`);
    (v.steps || []).forEach((s, j) => { if (!str(s.what)) fail(`${at}.verdict.steps[${j}]: "what" is required`); });
    if ((v.kind || "build") === "build" && !(v.steps || []).length) warn(`${at}: a build verdict with no steps gives the implementer nothing to follow`);
    const bld = r.asBuilt || {};
    const st = bld.state;
    if (!STATES.includes(st)) fail(`${at}: asBuilt.state must be one of ${STATES.join(", ")}`);
    if (plan.status === "planned" && st !== "pending") fail(`${at}: a planned plan has every part pending (found ${st})`);
    if (plan.status === "built" && st === "pending") fail(`${at}: a built plan has no pending part`);
    if (["amended", "deviated"].includes(st)) {
      if (!Array.isArray(bld.changes) || !bld.changes.length) fail(`${at}: an ${st} part lists its changes`);
      (bld.changes || []).forEach((c, j) => { if (!str(c.what) || !str(c.why)) fail(`${at}.asBuilt.changes[${j}]: "what" and "why" are required`); });
    }
    if (st === "dropped" && !str(bld.summary)) fail(`${at}: a dropped part states why in asBuilt.summary`);
    if (st === "deferred" && !(Number.isInteger(bld.issue) && bld.issue > 0)) fail(`${at}: a deferred part names the issue it moved to (asBuilt.issue)`);
    if (st !== "pending" && !str(bld.summary)) fail(`${at}: a ${st} part describes what exists in asBuilt.summary`);
    (bld.commits || []).forEach((c, j) => { if (!SHA.test(String(c))) fail(`${at}.asBuilt.commits[${j}] "${c}" is not a commit sha`); });
  });
  (plan.followUps || []).forEach((f, i) => { if (!(Number.isInteger(f.issue) && f.issue > 0)) fail(`followUps[${i}].issue must be an issue number`); });
  (plan.documents || []).forEach((d, i) => { if (!str(d.label) && !str(d.path)) fail(`documents[${i}] needs a label or a path`); });

  const left = placeholders(plan, "", []);
  if (left.length) fail(`placeholders left from the template: ${left.slice(0, 8).join(", ")}${left.length > 8 ? ` and ${left.length - 8} more` : ""}`);
  if (!out.fail.length) ok(`plan.json: ${rows.length} parts, ${rows.reduce((n, r) => n + (r.options || []).length, 0)} options, status ${plan.status}`);
}

function checkPage(dir, plan) {
  const htmlPath = path.join(dir, "dashboard.html");
  if (!fs.existsSync(htmlPath)) { fail("dashboard.html is missing (run new.js, or copy the template)"); return; }
  const html = fs.readFileSync(htmlPath, "utf8");
  const a = html.indexOf(OPEN), b = a < 0 ? -1 : html.indexOf(CLOSE, a + OPEN.length);
  if (a < 0 || b < 0) { fail('dashboard.html has no <script type="application/json" id="plan"> block'); return; }
  let embedded;
  try { embedded = JSON.parse(html.slice(a + OPEN.length, b)); } catch (e) { fail("the embedded plan does not parse: " + e.message); return; }
  if (JSON.stringify(embedded) !== JSON.stringify(plan)) fail("the embedded plan differs from plan.json: run embed.js"); else ok("dashboard.html embeds plan.json verbatim");
  const panels = html.match(/<section[^>]*role="tabpanel"[\s\S]*?<\/section>/g) || [];
  if (!panels.length) fail("dashboard.html has no tab panels");
  panels.forEach(p => { const id = (p.match(/id="([^"]+)"/) || [])[1]; if (!/class="key"/.test(p)) fail(`tab panel ${id} has no legend (.key)`); });
  if (panels.length && !out.fail.some(m => m.includes("legend"))) ok(`${panels.length} tab panels, each with a legend`);
  if (!/<title>/.test(html)) fail("dashboard.html has no <title>");
  if (/href="#"/.test(html)) fail('dashboard.html has a dead href="#" link');
  if (!/prefers-color-scheme:dark/.test(html)) warn("dashboard.html has no dark-mode block");
}

function run(dir) {
  const jsonPath = path.join(dir, "plan.json");
  if (!fs.existsSync(jsonPath)) { fail(`${jsonPath} is missing`); return report(); }
  let plan;
  try { plan = JSON.parse(fs.readFileSync(jsonPath, "utf8")); } catch (e) { fail("plan.json does not parse: " + e.message); return report(); }
  checkPlan(plan, path.basename(path.resolve(dir)));
  checkPage(dir, plan);
  return report();
}
function report() {
  out.ok.forEach(m => console.log("ok    " + m));
  out.warn.forEach(m => console.log("warn  " + m));
  out.fail.forEach(m => console.log("FAIL  " + m));
  console.log(out.fail.length ? `\n${out.fail.length} failure${out.fail.length === 1 ? "" : "s"}` : "\nall checks pass");
  return out.fail.length ? 1 : 0;
}
if (require.main === module) {
  const dir = process.argv[2];
  if (!dir) { console.error("usage: node check.js <plan-folder>"); process.exit(2); }
  process.exit(run(dir));
}
module.exports = { run };
