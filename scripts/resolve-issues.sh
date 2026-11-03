#!/usr/bin/env bash
# Runs the resolve-issue pipeline for several issues in ONE Claude Code session,
# compacting the conversation between issues.
#
#   scripts/resolve-issues.sh [options] ISSUE... [-- CLAUDE_FLAGS...]
#
#   scripts/resolve-issues.sh 65 66 67
#     /resolve-issue 65 -> /compact -> /resolve-issue 66 -> /compact -> /resolve-issue 67
#
# Every step is a headless `claude -p` call. The first starts a session with a fixed id
# and the rest `--resume` it, so /compact runs on the pipeline's own conversation, exactly
# as typing it between runs would. The script stops at the first step that fails. At the
# end (or on failure or Ctrl-C) it prints `claude --resume <id>` to open the session.
#
# Run it from the repository the pipeline works on: a session is stored per directory.
#
# Options:
#   -c, --command CMD     the pipeline command (default: /resolve-issue); each ISSUE is
#                         appended to it, so quote an ISSUE to pass more ("65 --skip-plan")
#   -i, --compact-instructions TEXT
#                         focus for the summary, sent as `/compact TEXT`
#   -r, --resume ID       continue an existing session: compact it first, then run the issues
#   -m, --permission-mode MODE
#                         passed to claude (default: auto). Headless runs cannot answer a
#                         permission prompt, so anything that would prompt is denied.
#                         Use -m '' to leave it to your settings.
#   -l, --log-dir DIR     where step logs go (default: ${TMPDIR:-/tmp}/resolve-issues)
#   -n, --dry-run         print the claude commands without running them
#   -h, --help            show this help
#   -- FLAGS...           passed to every claude call, e.g. -- --model opus --max-budget-usd 20
#
# With jq installed, progress (the pipeline's messages and tool calls) streams to the
# terminal and the raw stream-json goes to the log; without it, each step prints its
# final message when it ends.

set -uo pipefail

usage() { sed -n '2,/^$/s/^# \{0,1\}//p' "$0"; }
die() { echo "resolve-issues: $*" >&2; exit 2; }
need_value() { [[ $# -ge 2 ]] || die "$1 needs a value"; }

pipeline_cmd="/resolve-issue"
compact_instructions=""
session_id=""
permission_mode="auto"
log_root="${TMPDIR:-/tmp}/resolve-issues"
dry_run=false
issues=()
extra=()

while (($#)); do
  case "$1" in
    -c|--command)              need_value "$@"; pipeline_cmd=$2; shift 2 ;;
    -i|--compact-instructions) need_value "$@"; compact_instructions=$2; shift 2 ;;
    -r|--resume)               need_value "$@"; session_id=$2; shift 2 ;;
    -m|--permission-mode)      need_value "$@"; permission_mode=$2; shift 2 ;;
    -l|--log-dir)              need_value "$@"; log_root=$2; shift 2 ;;
    -n|--dry-run)              dry_run=true; shift ;;
    -h|--help)                 usage; exit 0 ;;
    --)                        shift; extra=("$@"); break ;;
    -*)                        die "unknown option $1 (see --help)" ;;
    *)                         issues+=("$1"); shift ;;
  esac
done

((${#issues[@]})) || { usage >&2; exit 2; }
$dry_run || command -v claude >/dev/null 2>&1 || die "claude is not on PATH"

new_uuid() {
  if command -v uuidgen >/dev/null 2>&1; then
    uuidgen | tr 'A-F' 'a-f'
  elif [[ -r /proc/sys/kernel/random/uuid ]]; then
    cat /proc/sys/kernel/random/uuid
  else # version 4 from 16 random bytes
    local h; h=$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')
    printf '%s-%s-4%s-%x%s-%s\n' "${h:0:8}" "${h:8:4}" "${h:13:3}" \
      $(((16#${h:16:1} & 3) | 8)) "${h:17:3}" "${h:20:12}"
  fi
}

# A session given with --resume already has a conversation; a new one starts on the first step.
if [[ -n $session_id ]]; then started=true; else started=false; session_id=$(new_uuid); fi

log_dir="$log_root/$(date +%Y%m%d-%H%M%S)-${session_id:0:8}"
$dry_run || mkdir -p "$log_dir" || die "cannot create $log_dir"
have_jq=false; command -v jq >/dev/null 2>&1 && have_jq=true

# One line per tool call, the assistant's own text in full, and a footer per step.
# Subagent traffic is left out. Lines that are not JSON are printed as they are, so the
# filter never fails and never breaks the pipe that claude writes into.
digest='
def clip($n): if length > $n then .[0:$n] + "..." else . end;
. as $line | (try fromjson catch {type: "raw", line: $line}) |
if .type == "raw" then .line + "\n"
elif .type == "assistant" and .parent_tool_use_id == null then
  .message.content[]? |
  if .type == "text" then "\n" + .text + "\n\n"
  elif .type == "tool_use" then
    "  > \(.name) " + ((.input | .description // .command // .file_path // .pattern // .skill // .url // "")
      | tostring | gsub("\\s+"; " ") | clip(110)) + "\n"
  else empty end
elif .type == "system" and .subtype == "compact_boundary" then
  "  [compacted: \(.compact_metadata.pre_tokens // "?") tokens before]\n"
elif .type == "result" then
  "  [\(.subtype) | \(.num_turns // 0) turns | \((.duration_ms // 0) / 1000 | floor)s | $\((.total_cost_usd // 0) * 100 | round / 100)]\n"
else empty end'

resume_hint() { echo "Open the session with:  claude --resume $session_id"; }
trap 'echo; echo "Interrupted."; resume_hint; exit 130' INT

step=0
# run_step LABEL PROMPT: one headless turn on the session; returns non-zero if it failed.
run_step() {
  local label=$1 prompt=$2 status=0 is_error=false log
  step=$((step + 1))
  log=$(printf '%s/%02d-%s.log' "$log_dir" "$step" "$(printf '%s' "$label" | tr -c 'A-Za-z0-9._-' '-')")

  local cmd=(claude -p "$prompt")
  if $started; then cmd+=(--resume "$session_id"); else cmd+=(--session-id "$session_id"); fi
  [[ -n $permission_mode ]] && cmd+=(--permission-mode "$permission_mode")
  cmd+=(${extra[@]+"${extra[@]}"})

  printf '\n=== [%d/%d] %s ===\n' "$step" "$total_steps" "$prompt"
  if $dry_run; then
    printf ' %q' "${cmd[@]}"; echo
    started=true
    return 0
  fi

  if $have_jq; then
    "${cmd[@]}" --output-format stream-json --verbose </dev/null | tee "$log" | jq -Rj --unbuffered "$digest"
    status=${PIPESTATUS[0]}
    local result
    result=$(jq -Rc '(try fromjson catch empty) | select(.type == "result")' "$log" | tail -n 1)
    [[ -n $result ]] || { is_error=true; result='{}'; }
    [[ $(jq -r '.is_error' <<<"$result") == true ]] && is_error=true
    local denied
    denied=$(jq -r '[.permission_denials[]?.tool_name] | unique | join(", ")' <<<"$result")
    [[ -n $denied ]] && echo "  warning: permission denied for: $denied (see $log)"
  else
    "${cmd[@]}" </dev/null | tee "$log"
    status=${PIPESTATUS[0]}
  fi
  started=true # the session exists now, even if this turn failed

  if ((status != 0)) || $is_error; then
    echo "Step $step ($prompt) failed (exit $status). Log: $log" >&2
    return 1
  fi
}

compact_prompt="/compact${compact_instructions:+ $compact_instructions}"
total_steps=$((2 * ${#issues[@]} - 1))
if $started; then total_steps=$((total_steps + 1)); fi

echo "Session $session_id in $PWD"
$dry_run || echo "Logs in $log_dir"

for issue in "${issues[@]}"; do
  if $started; then
    run_step "compact" "$compact_prompt" || { resume_hint; exit 1; }
  fi
  run_step "issue-$issue" "$pipeline_cmd $issue" || { resume_hint; exit 1; }
done

echo
echo "Done: ${#issues[@]} issue(s) in one session."
resume_hint
