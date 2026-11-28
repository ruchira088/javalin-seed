#!/usr/bin/env bash
#
# One-shot template initializer.
#
# Renames every reference of "javalin-seed" (and the Title-Case form
# "Javalin Seed" used in OpenAPI metadata) to a new project name, then
# deletes itself. Run once after cloning the template.
#
# Usage:
#   ./scripts/init-template.sh <new-project-name> ["New Display Name"]
#
# <new-project-name>     kebab-case identifier; replaces "javalin-seed".
#                        Must match ^[a-z][a-z0-9-]*[a-z0-9]$ so it is
#                        valid for Gradle, Kubernetes (RFC 1123),
#                        Docker tags, and ghcr.io image paths.
#
# "New Display Name"     optional human-readable form; replaces "Javalin Seed".
#                        Defaults to a Title-Cased version of the kebab name.
#
# Portable to macOS: it runs on the system bash 3.2 (no mapfile) and edits files
# with perl rather than sed, whose -i and \U differ between GNU and BSD.

set -euo pipefail

usage() {
    echo "Usage: $0 <new-project-name> [\"New Display Name\"]" >&2
    exit 1
}

[[ $# -ge 1 && $# -le 2 ]] || usage

NEW_NAME="$1"
if [[ ! "$NEW_NAME" =~ ^[a-z][a-z0-9-]*[a-z0-9]$ ]]; then
    echo "Invalid project name: '$NEW_NAME'" >&2
    echo "Must be lowercase kebab-case (letters, digits, dashes), 2+ chars, no leading/trailing dash." >&2
    exit 1
fi

DEFAULT_DISPLAY="$(printf '%s' "$NEW_NAME" | perl -pe 's/(^|-)([a-z])/ \u$2/g; s/^ //')"
NEW_DISPLAY="${2:-$DEFAULT_DISPLAY}"

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

if [[ -n "$(git status --porcelain)" ]]; then
    echo "Working tree is dirty. Commit or stash your changes first so the rename is reviewable as a single diff." >&2
    exit 1
fi

KEBAB_FILES=()
while IFS= read -r f; do KEBAB_FILES+=("$f"); done < <(git grep -lF 'javalin-seed' || true)
TITLE_FILES=()
while IFS= read -r f; do TITLE_FILES+=("$f"); done < <(git grep -lF 'Javalin Seed' || true)

if [[ ${#KEBAB_FILES[@]} -eq 0 && ${#TITLE_FILES[@]} -eq 0 ]]; then
    echo "No occurrences of 'javalin-seed' or 'Javalin Seed' found. Nothing to do." >&2
    exit 0
fi

# The replacement goes through the environment so perl never interprets it as code or a pattern; \Q...\E makes the
# search literal. ${arr[@]+...} keeps bash 3.2 from treating an empty array as unbound under `set -u`.
for f in ${KEBAB_FILES[@]+"${KEBAB_FILES[@]}"}; do
    REPLACEMENT="$NEW_NAME" perl -pi -e 's/\Qjavalin-seed\E/$ENV{REPLACEMENT}/g' "$f"
done

for f in ${TITLE_FILES[@]+"${TITLE_FILES[@]}"}; do
    REPLACEMENT="$NEW_DISPLAY" perl -pi -e 's/\QJavalin Seed\E/$ENV{REPLACEMENT}/g' "$f"
done

SCRIPT_PATH="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"
rm -- "$SCRIPT_PATH"
rmdir "$(dirname "$SCRIPT_PATH")" 2>/dev/null || true

echo "Renamed to '${NEW_NAME}' (display: '${NEW_DISPLAY}')."
echo "Modified ${#KEBAB_FILES[@]} files for the kebab name and ${#TITLE_FILES[@]} for the display name."
echo
echo "Next steps:"
echo "  ./gradlew test"
echo "  git add -A && git commit -m 'Initialize from template'"
