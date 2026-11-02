#!/bin/bash
# 내 컴퓨터(로컬) DB를 01~07 파일로 처음부터 다시 만든다. 기존 데이터는 전부 지워진다.
# 실행: 프로젝트 폴더에서  bash db/reset_local.sh
# 전제: docker 컨테이너 이름이 oracle, 서비스 이름 FREEPDB1 (README 설치 안내와 같음)
# 오류가 나면 그 파일에서 멈추고 오류 줄을 보여 준다. patches/ 폴더의 패치는 실행하지 않는다.
# 도커를 쓰지 않으면 이 스크립트 대신 DBeaver 에서 01~07 을 순서대로 Alt+X 로 실행한다 (db/README.md).

DB_USER=${DB_USER:-ojj}
DB_SERVICE=${DB_SERVICE:-FREEPDB1}
CONTAINER=${CONTAINER:-oracle}
cd "$(dirname "$0")" || exit 1

read -p "로컬 DB(${DB_USER}@${DB_SERVICE})를 전부 새로 만듭니다. 계속하려면 yes 입력: " ans
[ "$ans" = "yes" ] || { echo "취소했습니다."; exit 1; }
read -s -p "DB 사용자(${DB_USER}) 비밀번호: " DB_PW
echo

run() {
  echo ">>> $1"
  out=$( { echo "SET DEFINE OFF"; echo "SET FEEDBACK OFF"; cat "$1"; echo; echo "EXIT"; } | \
    docker exec -i -e NLS_LANG=AMERICAN_AMERICA.AL32UTF8 "$CONTAINER" \
    sqlplus -s "${DB_USER}/${DB_PW}@//localhost:1521/${DB_SERVICE}" 2>&1 )
  # 01번은 기존 테이블을 먼저 지운다. 처음 만드는 DB면 지울 테이블이 없어 ORA-00942 가 나는데 정상이라 오류로 보지 않는다.
  check="$out"
  case "$1" in 01_*) check=$(echo "$out" | grep -v "ORA-00942") ;; esac
  if echo "$check" | grep -E -q "ORA-[0-9]+|SP2-[0-9]+|PLS-[0-9]+"; then
    echo "$check" | grep -E -B2 "ORA-[0-9]+|SP2-[0-9]+|PLS-[0-9]+" | head -30
    echo "!!! $1 에서 오류가 났습니다. 여기서 멈춥니다."
    exit 1
  fi
  echo "    OK"
}

for f in 0[1-7]_*.sql; do
  run "$f"
done

echo ">>> 확인"
{ echo "SET HEADING OFF"; echo "SET FEEDBACK OFF"; cat <<'SQL'
SELECT '테이블 ' || COUNT(*) FROM USER_TABLES WHERE TABLE_NAME LIKE 'TB\_%' ESCAPE '\';
SELECT '컬럼   ' || COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME LIKE 'TB\_%' ESCAPE '\';
SELECT '공통코드 ' || COUNT(*) FROM TB_COM_CD;
SELECT '사용자 ' || COUNT(*) FROM TB_USER;
SELECT '공고   ' || COUNT(*) FROM TB_BID_PBANC;
SELECT '목차   ' || COUNT(*) FROM TB_TOC;
SELECT '요구사항 ' || COUNT(*) FROM TB_RQT_MTTR;
SELECT '알림   ' || COUNT(*) FROM TB_NOTI;
SELECT '제출서류 ' || COUNT(*) FROM TB_SBMSN_DCMNT;
EXIT
SQL
} | docker exec -i -e NLS_LANG=AMERICAN_AMERICA.AL32UTF8 "$CONTAINER" \
    sqlplus -s "${DB_USER}/${DB_PW}@//localhost:1521/${DB_SERVICE}" 2>&1 | sed '/^$/d'
echo "완료. 앱을 다시 실행해서 확인하세요. 테스트 계정 비밀번호: Demo!2026a"
