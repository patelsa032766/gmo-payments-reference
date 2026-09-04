#!/usr/bin/env bash
set -euo pipefail

# Spring Boot's Maven goal runs from the bootstrap module and therefore needs
# sibling reactor artifacts in the local Maven repository. Installing the
# reactor first makes this command reliable on a brand-new clone.
repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# A developer may keep deployment secrets in the ignored .env.local file or
# point GMO_ENV_FILE at another private file (for example, a companion
# implementation's sandbox environment). The public repository never contains
# that path or those values. Existing exported variables take precedence, so
# callers and secret managers can override values on invocation.
environment_file="${GMO_ENV_FILE:-${repository_root}/.env.local}"
if [[ -f "${environment_file}" ]]; then
  # Parse dotenv as data instead of sourcing it as shell code. Apart from
  # supporting ordinary values that contain spaces, this prevents an external
  # environment file from executing commands in the developer's shell.
  while IFS='=' read -r key value || [[ -n "${key:-}" ]]; do
    [[ "${key:-}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    # Explicit values supplied by the caller or secret manager take priority
    # over the convenience file.
    declare -p "${key}" >/dev/null 2>&1 && continue
    value="${value%$'\r'}"
    if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
      value="${value:1:${#value}-2}"
    fi
    export "${key}=${value}"
  done < "${environment_file}"
fi

# The Flask reference may use a PostgreSQL DATABASE_URL. This Java reference
# intentionally uses SQLite, so never pass an incompatible legacy URL to the
# Xerial driver when a shared environment file is selected.
if [[ "${DATABASE_URL:-}" != "" && "${DATABASE_URL}" != jdbc:sqlite:* ]]; then
  unset DATABASE_URL
fi

cd "${repository_root}/backend"

./mvnw -pl bootstrap -am install -DskipTests
exec ./mvnw -pl bootstrap spring-boot:run
