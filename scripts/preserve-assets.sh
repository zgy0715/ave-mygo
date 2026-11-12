#!/usr/bin/env bash
# Keep existing site media outside Vite's replaceable dist/ directory.
# Only copy missing files. Existing uploaded or preserved files take precedence.
set -euo pipefail
[[ $# -eq 1 && -d "$1/frontend" ]] || { echo 'Usage: preserve-assets.sh APP_DIR' >&2; exit 1; }
asset_root="$(cd "$1" && pwd)"
site_assets="$asset_root/var/site-assets"
hero_assets="$asset_root/var/hero-assets"
install -d -m 0755 "$site_assets/img" "$site_assets/assets" "$hero_assets"
copy_missing() {
  local source_file="$1" target_file="$2"
  [[ -f "$source_file" && ! -L "$source_file" ]] || return 0
  [[ ! -e "$target_file" && ! -L "$target_file" ]] || return 0
  install -D -m 0644 -- "$source_file" "$target_file"
}
# Source files are preferred; a previous release's dist is the fallback after git update.
for source_root in "$asset_root/frontend/public" "$asset_root/frontend/dist"; do
  if [[ -d "$source_root/img" ]]; then
    while IFS= read -r -d '' relative_file; do
      copy_missing "$source_root/img/$relative_file" "$site_assets/img/$relative_file"
    done < <(find "$source_root/img" -type f -printf '%P\0')
  fi
  copy_missing "$source_root/favicon.ico" "$site_assets/favicon.ico"
  for asset_slot in mygo mujica; do
    copy_missing "$source_root/assets/band-$asset_slot-poster.jpg" "$site_assets/assets/band-$asset_slot-poster.jpg"
    copy_missing "$source_root/assets/band-$asset_slot-720p.mp4" "$hero_assets/band-$asset_slot-720p.mp4"
  done
done
echo "Existing site media preserved in $site_assets and $hero_assets (existing files kept)."
