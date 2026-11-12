"""Check that unverified local media cannot enter source or default build artifacts."""
import argparse
import csv
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MEDIA = {'.png', '.jpg', '.jpeg', '.webp', '.svg', '.mp4', '.ico'}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dist', action='store_true', help='also check the default frontend build')
    args = parser.parse_args()
    with (ROOT / 'docs/assets-manifest.csv').open(encoding='utf-8', newline='') as source:
        rows = {row['path']: row for row in csv.DictReader(source)}
    tracked = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode('utf-8').split('\0')
    errors = []
    for name in tracked:
        if Path(name).suffix.lower() not in MEDIA:
            continue
        if not (name.startswith('frontend/public/') or name.startswith('docs/img/')):
            continue
        row = rows.get(name)
        if not row or row['status'] == 'unverified-local-only' or not row['permission_evidence']:
            errors.append(f'No verified distribution record: {name}')
    if args.dist:
        dist = ROOT / 'frontend/dist'
        if not (dist / 'index.html').is_file():
            errors.append('Build frontend before checking --dist')
        for path in dist.rglob('*'):
            if path.suffix.lower() in MEDIA:
                errors.append(f'Default artifact contains media: {path.relative_to(ROOT)}')
    if errors:
        raise SystemExit('\n'.join(errors))
    print('Asset policy check passed.')

if __name__ == '__main__':
    main()
