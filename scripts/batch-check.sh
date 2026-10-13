#!/usr/bin/env bash
# 일자별 배치 로그와 datamart-counts.csv 를 점검해 이상을 TSV(날짜 job 항목 근거)로 출력한다.
# 사용: scripts/batch-check.sh [로그디렉터리] [counts.csv]
set -euo pipefail

LOG_DIR="${1:-pipeline-samples/batch-logs}"
CSV="${2:-pipeline-samples/datamart-counts.csv}"
COUNT_WINDOW=7      # 직전 N일 평균
COUNT_LIMIT=30      # 평균 대비 허용 편차(%)
SLOW_FACTOR=2       # 평소 소요 시간 대비 허용 배수

# 1·2·4 번 항목: 로그 (START/SUCCESS 수, 소요 시간)
awk -v slow="$SLOW_FACTOR" '
function median(arr, n,   i, j, t, a) {
    for (i = 1; i <= n; i++) a[i] = arr[i]
    for (i = 2; i <= n; i++) { t = a[i]; for (j = i - 1; j >= 1 && a[j] > t; j--) a[j + 1] = a[j]; a[j + 1] = t }
    return (n % 2) ? a[(n + 1) / 2] : (a[n / 2] + a[n / 2 + 1]) / 2
}
{
    if (match($0, /job=[a-z_]+/) == 0) next
    job = substr($0, RSTART + 4, RLENGTH - 4)
    if (match(FILENAME, /[0-9]{4}-[0-9]{2}-[0-9]{2}/) == 0) next
    day = substr(FILENAME, RSTART, RLENGTH)
    ref = FILENAME ":" FNR
    if ($0 ~ /status=START/)   { st[day, job]++; stref[day, job] = stref[day, job] " " ref; days[day] = 1; jobs[job] = 1 }
    if ($0 ~ /status=SUCCESS/) {
        ok[day, job]++; okref[day, job] = okref[day, job] " " ref; days[day] = 1; jobs[job] = 1
        if (match($0, /elapsed=[0-9]+/)) {
            e = substr($0, RSTART + 8, RLENGTH - 8) + 0
            n[job]++; el[job, n[job]] = e
            ne[day, job]++; ev[day, job, ne[day, job]] = e; eref[day, job, ne[day, job]] = ref
        }
    }
}
END {
    for (j in jobs) { for (i = 1; i <= n[j]; i++) tmp[i] = el[j, i]; base[j] = n[j] ? median(tmp, n[j]) : 0 }
    for (d in days) for (j in jobs) {
        s = st[d, j] + 0; o = ok[d, j] + 0
        if (s == 0)                 printf "%s\t%s\t1.실행\tSTART 기록 없음 (%s)\n", d, j, d
        else if (o == 0)            printf "%s\t%s\t1.실행\t종료 기록 없음, START:%s\n", d, j, stref[d, j]
        if (o >= 2)                 printf "%s\t%s\t2.중복\tSUCCESS %d회:%s\n", d, j, o, okref[d, j]
        for (k = 1; k <= ne[d, j]; k++) if (base[j] > 0 && ev[d, j, k] > slow * base[j])
            printf "%s\t%s\t4.지연\telapsed=%ds, 평소(중앙값) %ds 의 %.1f배: %s\n", d, j, ev[d, j, k], base[j], ev[d, j, k] / base[j], eref[d, j, k]
    }
}' "$LOG_DIR"/*.log

# 3 번 항목: CSV (직전 N일 평균 대비 편차, 0건)
awk -F, -v w="$COUNT_WINDOW" -v lim="$COUNT_LIMIT" '
NR > 1 {
    j = $2; c[j]++; d[j, c[j]] = $1; v[j, c[j]] = $3 + 0; r[j, c[j]] = NR
}
END {
    for (j in c) for (i = 1; i <= c[j]; i++) {
        if (v[j, i] == 0) { printf "%s\t%s\t3.건수\t0건: CSV %d행\n", d[j, i], j, r[j, i]; continue }
        s = 0; k = 0
        for (p = i - w; p < i; p++) if (p >= 1) { s += v[j, p]; k++ }
        if (k == 0) continue
        a = s / k; dev = (v[j, i] - a) / a * 100
        if (dev > lim || dev < -lim)
            printf "%s\t%s\t3.건수\t%d건, 직전 %d일 평균 %.1f 대비 %+.1f%%: CSV %d행\n", d[j, i], j, v[j, i], k, a, dev, r[j, i]
    }
}' "$CSV"
