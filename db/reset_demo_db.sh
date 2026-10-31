#!/bin/bash
# 시연 DB를 최신 스키마로 다시 만든다. 기존 데이터는 전부 지워진다.

read -p "테스트 DB를 전부 새로 만듭니다. 계속하려면 yes 입력: " ans
[ "$ans" = "yes" ] || { echo "취소했습니다."; exit 1; }
read -s -p "DB 사용자(ojj) 비밀번호: " APP_PW
echo

cd ~/db
run() {
  echo ">>> $1 실행"
  docker exec -i -e NLS_LANG=AMERICAN_AMERICA.AL32UTF8 oracle \
    sqlplus -s ojj/"$APP_PW"@//localhost:1521/FREEPDB1 < "$1"
}

sudo systemctl stop sujuro
run 01_schema.sql
run 02_code_data.sql
run 03_test_data.sql
run 03_sample_data.sql
run 05_test_notifications.sql
run 06_test_team.sql
run 06_test_stats.sql
sudo systemctl start sujuro

echo "완료. http://3.35.131.20 에서 확인하세요."