#!/usr/bin/env bash
# Judges an ecosystem adapter by real builds. usage: corpus.sh <tool.conf> <name> <https url> <reference tool version> [commit]
# The conf defines REFERENCE_IMAGE, CACHE_MOUNT, DOWNLOADED, reference_setup, build_command <checkout> <stage> and
# known_gap <log> <container>; everything else is the tool's own.
# On an account kept for it: it clears the projects it makes, and fills the tool's cache.
# Each project: plain git clone, a reference build without Porelo, Porelo's real first start and its yes, a task with
# the stub, the build by podman exec as the session has it, what the firewall refused. Stops at the first stage that fails.
set -euo pipefail
export LC_ALL=C
[ $# -ge 4 ] || {
    echo "usage: $0 <tool.conf> <name> <url> <version> [commit]" >&2
    exit 2
}
CONF=$1
NAME=$2
URL=$3
VERSION=$4
COMMIT=${5:-}
[[ $NAME =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || {
    echo "not a name: $NAME" >&2
    exit 2
}
[[ $VERSION =~ ^[0-9][0-9.]*$ ]] || {
    echo "not a version: $VERSION" >&2
    exit 2
}
# shellcheck source=/dev/null
. "$(realpath "$CONF")"
ROOT=${CORPUS_ROOT:-$HOME/corpus}
RUN=$ROOT/results/$NAME-$(date -u +%Y%m%dT%H%M%SZ)
WORK=$ROOT/work/$NAME
SOCKET=${XDG_RUNTIME_DIR:-/run/user/$(id -u)}/porelo/porelod.sock
mkdir -p "$(dirname "$RUN")"
mkdir "$RUN"
say() { echo "[$NAME] $*" | tee -a "$RUN/summary.txt"; }
call() { varlinkctl call --timeout=300 "$SOCKET" "org.fuin.porelo.Tasks1.$1" "$2"; }
PROJECT=""
CONTAINER=""
IMAGE=""
cleanup() {
    local status=$?
    trap - EXIT
    if [ -n "$CONTAINER" ] && podman container exists "$CONTAINER" 2>/dev/null; then
        porelo task remove --force --no-input "$CONTAINER" >/dev/null 2>&1 || say "LEFT: task $CONTAINER"
    fi
    if [ -n "$PROJECT" ]; then
        porelo project clear "$PROJECT" --yes --force >/dev/null 2>&1 || say "LEFT: project $PROJECT"
    fi
    if [ -n "$IMAGE" ] && podman image exists "$IMAGE" 2>/dev/null; then podman rmi -f "$IMAGE" >/dev/null 2>&1 || say "LEFT: image $IMAGE"; fi
    exit "$status"
}
trap cleanup EXIT

rm -rf "$WORK"
mkdir -p "$(dirname "$WORK")"
git clone -q "$URL" "$WORK"
[ -z "$COMMIT" ] || git -C "$WORK" checkout -q "$COMMIT"
COMMIT=$(git -C "$WORK" rev-parse HEAD)
say "clone $URL at $COMMIT"

# The reference: this project's build at this commit with no Porelo, which also fills the cache as a person's build would.
COMMAND=$(build_command "$WORK" skip-tests)
START=$(date +%s)
RC=0
podman run --rm --user 0 -e LANG=C.UTF-8 -v "$WORK:/src" -v "$CACHE_MOUNT" -w /src "$REFERENCE_IMAGE" \
    sh -c "$(reference_setup "$VERSION") && $COMMAND" >"$RUN/reference.log" 2>&1 || RC=$?
say "reference: $COMMAND with $VERSION: exit $RC after $(($(date +%s) - START)) s"
# CORPUS_REFERENCE_MAY_FAIL: a build known not to run anywhere without what a task lacks, to show its failure there.
[ "$RC" = 0 ] || [ -n "${CORPUS_REFERENCE_MAY_FAIL:-}" ] || {
    say "RESULT: not counted, the project does not build by itself this way"
    exit 3
}
git -C "$WORK" reset -q --hard
git -C "$WORK" clean -qfdx

# The file by Porelo's real first start: its question, then a yes.
call FirstStart "$(jq -cn --arg c "$WORK" '{checkout:$c}')" >"$RUN/first-start.json" 2>"$RUN/first-start.err" || {
    cat "$RUN/first-start.err" | tee -a "$RUN/summary.txt"
    say "RESULT: FirstStart refused"
    exit 1
}
OUTCOME=$(jq -r .outcome "$RUN/first-start.json")
say "first start: $OUTCOME; hosts: $(jq -r '.hosts|join(" ")' "$RUN/first-start.json"); marked: $(jq -r '.marked|join(" ")' "$RUN/first-start.json")"
[ "$OUTCOME" = ASK_NEW ] || {
    say "RESULT: the first start asks nothing new: $OUTCOME"
    exit 1
}
jq -r '.found[]|select(.unknown)|"  unknown \(.key): \(.note)"' "$RUN/first-start.json" | cut -c1-200 | tee -a "$RUN/summary.txt"
jq -r '.file' "$RUN/first-start.json" >"$RUN/question-file.txt"
HASH=$(jq -r .hash "$RUN/first-start.json")
call ConfirmFirstStart "$(jq -cn --arg c "$WORK" --arg h "$HASH" '{checkout:$c,hash:$h}')" >"$RUN/confirm.json" 2>"$RUN/confirm.err" || {
    cat "$RUN/confirm.err" | tee -a "$RUN/summary.txt"
    say "RESULT: the yes was refused"
    exit 1
}
PROJECT=$(jq -r .project "$RUN/confirm.json")
cp "$WORK/project.yml" "$RUN/project.yml"
say "project: $PROJECT"
grep -E 'install |domains|^ *- "' "$RUN/project.yml" | cut -c1-140 | tee -a "$RUN/summary.txt" || true

# The task with the stub.
TASK=c
CONTAINER=porelo-$PROJECT-$TASK
REPOSITORY=$(sed -n '/^repositories:/,$p' "$RUN/project.yml" | sed -n 's/^  "\{0,1\}\([^":# ]*\)"\{0,1\}:.*/\1/p' | head -1)
[ -n "$REPOSITORY" ] || {
    say "RESULT: the file names no repository"
    exit 1
}
START=$(date +%s)
RC=0
(cd "$WORK" && timeout 30m porelo task start "$TASK" -p "$PROJECT" -r "$REPOSITORY" --agent stub --attach shell --clearance=deny --detach --no-input --yes) >"$RUN/start.log" 2>&1 || RC=$?
IMAGE=$(sed -n 's/^task image  *//p' "$RUN/start.log" | head -1)
say "task start: exit $RC after $(($(date +%s) - START)) s; image ${IMAGE:-none}"
[ "$RC" = 0 ] || {
    grep -E '^(failed|porelo:)' "$RUN/start.log" | cut -c1-300 | tee -a "$RUN/summary.txt" || true
    say "RESULT: the task did not start"
    exit 1
}

# The session's environment against podman exec's.
podman exec "$CONTAINER" env | sort >"$RUN/exec.env"
{
    sleep 4
    echo 'env | sort > /tmp/session.env'
    sleep 2
    printf '\002d'
    sleep 1
} | script -qfc "porelo task attach $CONTAINER" /dev/null >/dev/null 2>&1 || true
podman exec "$CONTAINER" cat /tmp/session.env >"$RUN/session.env" 2>/dev/null || {
    say "RESULT: the session's environment could not be read"
    exit 1
}
diff "$RUN/exec.env" "$RUN/session.env" | grep -E '^[<>]' | grep -vE '^[<>] (TMUX|TMUX_PANE|TERM|TERM_PROGRAM|TERM_PROGRAM_VERSION|COLORTERM|SHLVL|_|OLDPWD|PWD|LS_COLORS|container)=' | cut -c1-200 >"$RUN/env.diff" || true
say "environment, exec < > session: $(wc -l <"$RUN/env.diff") difference(s)"
sed 's/^/  /' "$RUN/env.diff" | tee -a "$RUN/summary.txt"
# As the session has it: each build runs in the environment read from it.
grep -E '^[A-Za-z_][A-Za-z0-9_]*=' "$RUN/session.env" | grep -vE '^(TMUX|TMUX_PANE|_|SHLVL|OLDPWD|PWD)=' >"$RUN/session.env.clean" || true

# A build stopped only by what the conf names as a known gap runs on past it, to see what follows;
# said in the result, so it never reads as the adapter having built it.
PAST_GAP=()
run_stage() {
    COMMAND=$(build_command "$WORK" "$1")
    START=$(date +%s)
    RC=0
    timeout "${CORPUS_MINUTES:-60}m" podman exec -w /workspace --env-file "$RUN/session.env.clean" "${PAST_GAP[@]}" "$CONTAINER" sh -c "$COMMAND" >"$RUN/build-$1.log" 2>&1 || RC=$?
    say "build $1${PAST_GAP:+ (past the known gap)}: $COMMAND: exit $RC after $(($(date +%s) - START)) s"
    grep -E 'ERROR|BUILD|not found|Tests run:.*Fail' "$RUN/build-$1.log" | tail -6 | cut -c1-170 | sed 's/^/  /' | tee -a "$RUN/summary.txt" || true
}
for stage in skip-tests tests; do
    run_stage "$stage"
    if [ "$RC" != 0 ] && [ "${#PAST_GAP[@]}" = 0 ]; then
        GAP=$(known_gap "$RUN/build-$stage.log" "$CONTAINER")
        if [ -n "$GAP" ]; then
            say "GAP: stopped on $GAP, which the draft does not set"
            cp "$RUN/build-$stage.log" "$RUN/build-$stage-gap.log"
            PAST_GAP=(--env "$GAP")
            run_stage "$stage"
        fi
    fi
    porelo task logs "$CONTAINER" dnsmasq.log >"$RUN/dnsmasq.log" 2>/dev/null || true
    porelo task logs "$CONTAINER" events.jsonl >"$RUN/events.jsonl" 2>/dev/null || true
    [ "$RC" = 0 ] || break
done
python3 - "$RUN" "$DOWNLOADED" >>"$RUN/hosts.txt" <<'PY'
import glob, os, re, sys
run = sys.argv[1]
read = lambda n: open(os.path.join(run, n), errors="replace").read() if os.path.exists(os.path.join(run, n)) else ""
domains = re.findall(r'^\s+- "([^"]+)"', read("project.yml"), re.M)
stub = {"example.com", "example.net", "api.anthropic.com"}
asked = set(re.findall(r"query\[[A-Z]+\] (\S+)", read("dnsmasq.log")))
print("refused: " + (", ".join(sorted(h for h in asked if h not in domains and h not in stub and "." in h)) or "none"))
print("firewall stopped: %d" % len([l for l in read("events.jsonl").splitlines() if l.strip()]))
log = "".join(open(f, errors="replace").read() for f in glob.glob(os.path.join(run, "build-*.log")))
fetched = set(re.findall(sys.argv[2], log))
for d in domains:
    print("opened %s: %s" % (d, "downloaded from" if d in fetched else "resolved" if d in asked else "unused"))
PY
tee -a "$RUN/summary.txt" <"$RUN/hosts.txt"
# The reference runs no tests, so a stop in them is sorted against the reference by whoever reads this.
if [ "$RC" != 0 ]; then
    say "RESULT: did not build${PAST_GAP:+, past the known gap}"
elif [ "${#PAST_GAP[@]}" != 0 ]; then
    say "RESULT: built only past the known gap, so not by the adapter's file"
    RC=4
else
    say "RESULT: built"
fi
exit "$RC"
