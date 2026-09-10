#!/bin/sh
# What the service image is called, which is what it was built from.
#
# Not the commit. A commit names when something was built, not what — so every commit
# would produce a new image, a new tag, and a new revision of a service whose bytes
# nobody had changed. Naming it after its sources means a commit that leaves them
# alone produces a name that already exists, and there is nothing to build or roll.
#
# It also makes "staging and production run the same bytes" a fact about the name
# rather than a claim about the process.
#
# The committed trees, not the working ones: an image is built from what was merged.
set -eu

cd "$(dirname "$0")/.."

# Everything the build reads. `gradle/` covers the wrapper and the version catalogue,
# so a dependency bump lands here too.
for path in \
  core \
  api \
  backend \
  gradle \
  gradlew \
  settings.gradle.kts \
  build.gradle.kts \
  gradle.properties \
  detekt.yml
do
  git rev-parse "HEAD:$path"
done | git hash-object --stdin
