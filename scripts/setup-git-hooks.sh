#!/bin/bash
#
# Setup Git Hooks
# Copies pre-commit hook to .git/hooks/
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
HOOKS_DIR="$PROJECT_ROOT/.git/hooks"
SOURCE_HOOK="$SCRIPT_DIR/hooks/pre-commit"
TARGET_HOOK="$HOOKS_DIR/pre-commit"

echo "🔧 Setting up Git hooks..."

if [ ! -d "$HOOKS_DIR" ]; then
    echo "❌ .git/hooks directory not found. Are you in a git repository?"
    exit 1
fi

if [ ! -f "$SOURCE_HOOK" ]; then
    echo "❌ Source hook not found: $SOURCE_HOOK"
    exit 1
fi

cp "$SOURCE_HOOK" "$TARGET_HOOK"
chmod +x "$TARGET_HOOK"

echo "✅ Pre-commit hook installed: $TARGET_HOOK"
echo ""
echo "💡 The hook will check migration-entity consistency before each commit."
echo "💡 To skip (not recommended): git commit --no-verify"

