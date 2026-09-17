#!/usr/bin/env bash
set -euo pipefail

release_commit="${1:-HEAD}"
channel="${2:-stable}"

git rev-parse --verify "${release_commit}^{commit}" >/dev/null

git log -1 --pretty=format:'%B' "$release_commit"
printf '\n'

printf '\n## 下载说明 / Download Instructions\n\n'
if [[ "$channel" == "beta" ]]; then
  printf '> [!WARNING]\n'
  printf '> 这是 Beta 测试版本，可能存在不稳定、性能缺陷以及尚未完成的问题。\n'
  printf '> 仅建议愿意在日常使用中提供调试日志的用户使用；普通用户请使用正式版 Release 包。\n'
  printf '>\n'
  printf '> This is a beta build and may be unstable, have performance defects, or contain unfinished work.\n'
  printf '> It is only recommended for users willing to provide debug logs in daily use; other users should use the stable Release build.\n\n'
fi
printf '日常使用请选择 **Release** 包；**Debug** 包仅用于调试和查找问题。\n'
printf 'For daily use, choose the **Release** build; the **Debug** build is only for debugging and troubleshooting.\n'
