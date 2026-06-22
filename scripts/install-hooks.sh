#!/bin/sh
# Installs Git hooks for the IdleHarvest project.
# Run this script after cloning the repository.

HOOK_DIR=".git/hooks"
SCRIPT_DIR="scripts"

echo "Installing Git hooks..."

if [ ! -d "$HOOK_DIR" ]; then
    echo "Error: .git/hooks directory not found. Are you in the repo root?"
    exit 1
fi

cp "$SCRIPT_DIR/pre-commit" "$HOOK_DIR/pre-commit"
chmod +x "$HOOK_DIR/pre-commit"

echo "✅ Pre-commit hook installed successfully."
