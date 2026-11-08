#!/bin/bash
# 테스트·시연 데이터(03~07_seed_*.sql)의 파일 행이 가리키는 실제 파일을 업로드 폴더에 넣는다.
# db/demo-files/manifest.tsv 의 목록대로 db/demo-files 의 원본을 <업로드폴더>/seed/... 로 복사한다. DB 에는 접속하지 않는다.
#
# 사용법
#   로컬: bash db/load_demo_files.sh                 (application-local.properties 의 app.upload-dir 을 읽는다)
#   로컬·서버: bash db/load_demo_files.sh /경로/upload  (업로드 폴더를 직접 준다)
#   배포 서버(S3): bash load_demo_files.sh s3://sujuro-files-s3   (S3 버킷의 seed/ 아래로 올린다. AWS CLI 필요,
#                 권한은 EC2 IAM 역할로 받는다. 서버 설정이 app.storage.type=s3 일 때 쓴다)
# DB 를 다시 만든 뒤(reset_local.sh · reset_demo_db.sh) 한 번 실행한다. 다시 실행해도 된다 (seed 폴더만 새로 채운다).

set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HERE/demo-files"
MANIFEST="$SRC/manifest.tsv"

UP="${1:-}"
# s3://버킷 이면 임시 폴더에 먼저 모은 뒤 마지막에 S3 로 올린다
S3DEST=""
case "$UP" in
  s3://*)
    command -v aws >/dev/null 2>&1 || { echo "aws 명령이 없습니다. AWS CLI v2 를 먼저 설치하세요."; exit 1; }
    S3DEST="${UP%/}"
    UP="$(mktemp -d)"
    trap 'rm -rf "$UP"' EXIT
    ;;
esac
if [ -z "$UP" ]; then
  PROPS="$HERE/../src/main/resources/application-local.properties"
  if [ -f "$PROPS" ]; then
    UP="$(grep -E '^[[:space:]]*app\.upload-dir[[:space:]]*=' "$PROPS" | tail -1 | sed -E 's/^[^=]*=[[:space:]]*//; s/[[:space:]]*$//')"
  fi
fi
if [ -z "$UP" ]; then
  echo "업로드 폴더를 찾지 못했습니다. 예) bash $0 /home/ubuntu/upload"
  echo "(서버는 서비스 설정의 app.upload-dir 값을 넣습니다)"
  exit 1
fi
[ -f "$MANIFEST" ] || { echo "목록 파일이 없습니다: $MANIFEST"; exit 1; }

if [ -n "$S3DEST" ]; then echo "S3 대상:     $S3DEST/seed/ (임시 폴더 $UP 에 모은 뒤 올림)"; else echo "업로드 폴더: $UP"; fi
echo "원본 폴더:   $SRC"
mkdir -p "$UP" || { echo "업로드 폴더를 만들 수 없습니다."; exit 1; }

# 이전에 넣은 시드 파일만 지운다 (사람이 화면에서 올린 파일은 seed 밖에 있어 건드리지 않는다)
if [ -d "$UP/seed" ]; then
  rm -rf "$UP/seed"
fi

total=0; copied=0; missing=0
# 첫 줄(dest src)은 건너뛴다. 윈도우 줄끝(\r)도 지운다
while IFS=$'\t' read -r dest src || [ -n "${dest:-}" ]; do
  dest="${dest%$'\r'}"; src="${src%$'\r'}"
  [ -z "$dest" ] && continue
  [ "$dest" = "dest" ] && continue
  total=$((total + 1))
  if [ ! -f "$SRC/$src" ]; then
    missing=$((missing + 1))
    [ $missing -le 10 ] && echo "!!! 원본 없음: $src"
    continue
  fi
  mkdir -p "$UP/$(dirname "$dest")"
  cp "$SRC/$src" "$UP/$dest" && copied=$((copied + 1))
done < "$MANIFEST"

# 확인: 목록의 파일이 모두 업로드 폴더에 있는지
absent=0
while IFS=$'\t' read -r dest src || [ -n "${dest:-}" ]; do
  dest="${dest%$'\r'}"
  [ -z "$dest" ] || [ "$dest" = "dest" ] && continue
  [ -f "$UP/$dest" ] || absent=$((absent + 1))
done < "$MANIFEST"

echo "목록 ${total}건 · 복사 ${copied}건 · 원본 없음 ${missing}건 · 업로드 폴더에 없는 파일 ${absent}건"

# S3: 이전 seed/ 를 지우고 새로 올린 뒤 개수를 확인한다 (사람이 화면에서 올린 파일은 seed 밖에 있어 건드리지 않는다)
if [ -n "$S3DEST" ] && [ $missing -eq 0 ] && [ $absent -eq 0 ]; then
  echo "S3 로 올리는 중..."
  aws s3 rm "$S3DEST/seed/" --recursive --only-show-errors || { echo "S3 의 이전 seed 를 지우지 못했습니다."; exit 1; }
  aws s3 cp "$UP/seed/" "$S3DEST/seed/" --recursive --only-show-errors || { echo "S3 로 올리지 못했습니다."; exit 1; }
  uploaded=$(aws s3 ls "$S3DEST/seed/" --recursive | wc -l | tr -d ' ')
  echo "S3 에 올라간 파일 ${uploaded}건 (목록 ${total}건)"
  [ "$uploaded" -eq "$total" ] || absent=$((total - uploaded))
fi
if [ $missing -eq 0 ] && [ $absent -eq 0 ]; then
  echo "완료. 화면에서 제안서·제출서류·참고자료·평가표를 내려받아 확인하세요."
else
  echo "빠진 파일이 있습니다. db/demo-files 를 최신으로 받았는지 확인하세요."
  exit 1
fi
