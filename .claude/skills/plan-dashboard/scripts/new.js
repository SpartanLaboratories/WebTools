#!/usr/bin/env node
// Start a plan folder from the template: docs/plans/<issue>-<slug>/{plan.json,dashboard.html}.
// usage: node new.js <issue> <slug> [--title "Short name"] [--issue-title "The issue's title"] [--baseline <sha>] [--date YYYY-MM-DD] [--repo owner/name]
// The planner then fills every remaining <placeholder> in plan.json and runs embed.js, then check.js.
"use strict";
const fs = require("fs"), path = require("path");
const { embed } = require("./embed.js");

const args = process.argv.slice(2);
const issue = parseInt(args[0], 10), slug = args[1];
if (!issue || !slug || !/^[a-z0-9][a-z0-9-]*$/.test(slug)) {
  console.error('usage: node new.js <issue> <slug> [--title "..."] [--issue-title "..."] [--baseline <sha>] [--date YYYY-MM-DD] [--repo owner/name]');
  process.exit(2);
}
const opt = (name, dflt) => { const i = args.indexOf("--" + name); return i >= 0 && args[i + 1] != null ? args[i + 1] : dflt; };
const root = path.resolve(__dirname, "..", "..", "..", "..");           // the repository: .claude/skills/plan-dashboard/scripts → root
const tpl = path.resolve(__dirname, "..", "template");
const folder = `${issue}-${slug}`, dir = path.join(root, "docs", "plans", folder);
if (fs.existsSync(path.join(dir, "dashboard.html")) || fs.existsSync(path.join(dir, "plan.json"))) {
  console.error(`refusing to overwrite: ${dir} already holds a plan.json or dashboard.html`);
  process.exit(1);
}
fs.mkdirSync(dir, { recursive: true });
const plan = JSON.parse(fs.readFileSync(path.join(tpl, "plan.json"), "utf8"));
plan.issue = issue;
plan.slug = folder;
plan.repo = opt("repo", plan.repo);
plan.title = opt("title", plan.title);
plan.issueTitle = opt("issue-title", plan.issueTitle);
plan.baseline.sha = opt("baseline", plan.baseline.sha);
plan.baseline.date = opt("date", plan.baseline.date);
fs.writeFileSync(path.join(dir, "plan.json"), JSON.stringify(plan, null, 2) + "\n");
fs.copyFileSync(path.join(tpl, "dashboard.html"), path.join(dir, "dashboard.html"));
embed(dir);
console.log(`created ${path.relative(root, dir)}/plan.json and dashboard.html from the template`);
console.log(`next: fill every <placeholder> in plan.json, then node ${path.relative(root, path.join(__dirname, "embed.js"))} docs/plans/${folder} && node ${path.relative(root, path.join(__dirname, "check.js"))} docs/plans/${folder}`);
