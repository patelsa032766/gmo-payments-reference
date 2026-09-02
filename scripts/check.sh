#!/usr/bin/env bash
set -euo pipefail

# Run the same deterministic checks expected before publishing a change.
repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "${repository_root}/backend"
./mvnw test

cd "${repository_root}/frontend"
npm test -- --watch=false
npm run build
