`scripts/batch-check.sh` 의 현재 판정(실데이터 7건 · 이상 없는 픽스처 0건 · 중복 주입 픽스처 `2.중복` 1건)을 기대값과 대조해, 판정이 바뀌면 `PASS 3/3` 이 아닌 결과와 종료 코드 1로 실패하게 하는 회귀 테스트다.
bash scripts/tests/batch-check.test.sh
기대값은 `pipeline-samples/` 와 손으로 쓴 2일치 픽스처의 현재 출력을 그대로 고정한 것이고 Terraform · CI 는 검증하지 않으므로, 현업에서는 팀의 실제 로그 · 적재 건수 CSV 로 `scripts/tests/expected/real.tsv` 를 다시 만들고 실행해 본 적 없는 `ci-ports/Jenkinsfile` 연결을 직접 확인해야 한다.
