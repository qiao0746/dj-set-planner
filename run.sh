#!/usr/bin/env bash
set -euo pipefail

args=("$@")
arg_line=""
for a in "${args[@]}"; do
  # escape double quotes for exec.args
  escaped=${a//\"/\\\"}
  if [[ "$escaped" == *" "* ]]; then
    arg_line+="\"$escaped\" "
  else
    arg_line+="$escaped "
  fi
done

arg_line="${arg_line%" "}"
mvn -q -DskipTests compile exec:java "-Dexec.args=$arg_line"
