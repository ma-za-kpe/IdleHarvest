#!/usr/bin/env bash
#
# IdleHarvest pre-commit hook
# Runs the full validation pipeline before every commit.
# Install: cp scripts/pre-commit.sh .git/hooks/pre-commit && chmod +x .git/hooks/pre-commit
#
set -euo pipefail

echo "========================================"
echo "  IdleHarvest Pre-Commit Validation"
echo "========================================"

PASS=0
FAIL=0

run_check() {
  local name="$1"
  shift
  echo ""
  echo "▶ $name"
  if "$@"; then
    echo "  ✓ $name passed"
    PASS=$((PASS + 1))
  else
    echo "  ✗ $name FAILED"
    FAIL=$((FAIL + 1))
  fi
}

run_check "Spotless formatting" ./gradlew spotlessCheck --quiet
run_check "Detekt static analysis" ./gradlew detekt --quiet
run_check "Unit tests" ./gradlew :shared:testAndroidHostTest --quiet

echo ""
echo "========================================"
echo "  Results: $PASS passed, $FAIL failed"
echo "========================================"

if [ "$FAIL" -gt 0 ]; then
  echo ""
  echo "❌ Commit blocked: $FAIL quality gate(s) failed."
  echo "   Fix the issues above, then re-run: git commit"
  exit 1
fi

echo "✅ All quality gates passed. Proceeding with commit."
exit 0
