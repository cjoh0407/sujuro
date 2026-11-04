# db/seed: 테스트·시연 데이터 생성기

| 파일 | 하는 일 |
|---|---|
| `gen_files.py` | 견본 원본 파일(제안서·RFP·제출서류·참고자료)을 `db/demo-files/`에 만든다 |
| `gen_sql.py` | `db/03~07_seed_*.sql`, `db/demo-files/manifest.tsv`, 사업별 평가표 엑셀(`demo-files/evaluation/gen/`)을 만든다 |

- SQL을 손으로 고치지 말고 생성기를 고친 뒤 다시 만든다. 사업 목록·점수·이름은 `gen_sql.py` 위쪽 표에 있다.
- 결과 점수는 난수 시드가 고정이라 다시 만들어도 같다.
- 만든 SQL은 DBeaver와 SQL*Plus 둘 다에서 돈다 (SET·/·PL/SQL·& 없음, 문장 안 빈 줄 없음, 문자열 줄바꿈은 CHR(10)).
