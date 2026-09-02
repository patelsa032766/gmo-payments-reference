#!/usr/bin/env bash
set -euo pipefail

# Spring Boot's Maven goal runs from the bootstrap module and therefore needs
# sibling reactor artifacts in the local Maven repository. Installing the
# reactor first makes this command reliable on a brand-new clone.
repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repository_root}/backend"

./mvnw -pl bootstrap -am install -DskipTests
exec ./mvnw -pl bootstrap spring-boot:run
