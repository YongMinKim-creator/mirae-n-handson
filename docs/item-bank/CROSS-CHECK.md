# item-bank 비즈니스 규칙 교차 검증용 추출 (코드 기준)

- 읽은 파일: `legacy/item-bank-php/search.php`, `register.php`, `units.php` (`inc/`, `vendor/`, `docs/` 는 읽지 않음)
- 원칙: 코드에 있는 것만 적는다. 주석과 코드가 다르면 코드를 따른다.
- 줄번호는 `legacy/item-bank-php/` 기준이다.

## A. 검색 조건 조합 (`search.php`)

| ID | 규칙 | 근거 |
|---|---|---|
| CX-01 | 조건은 모두 `AND` 로 결합한다. 시작이 `WHERE 1=1` 이고 키워드 · 단원 · 난이도 · 태그가 각각 `AND` 로 붙는다. | `search.php:44` |
| CX-02 | 키워드 `q` 는 앞뒤 공백을 제거한 뒤 제목 또는 지문에 부분 일치(`LIKE %q%`)하는 문항을 찾는다. | `search.php:85`, `:97-98` |
| CX-03 | 키워드가 100자를 넘으면 앞 100자까지만 쓰고 경고를 낸다(요청은 거부하지 않는다). | `search.php:86-89` |
| CX-04 | 단원 `unit` 은 `unit_code` 와 정확히 일치하는 문항만 찾는다. 형식이 틀리거나 소문자여도 경고만 내고 입력값 그대로 조회한다. | `search.php:111-120` |
| CX-05 | 난이도가 비어 있으면 `level < 5` 조건이 붙어 **난이도 5 문항이 제외된다.** | `search.php:214-216` |
| CX-06 | 난이도가 `1`~`5` 의 한 자리 숫자면 `level = ?` 로 정확히 일치시킨다. 이때는 5도 조회된다. | `search.php:217-221` |
| CX-07 | 난이도가 그 밖의 값이면 경고를 내고 `(int)` 로 바꾼 값으로 `level = ?` 조회한다. 거부하지 않는다. | `search.php:223-229` |
| CX-08 | 태그 `tag` 는 이름이 정확히 일치하는 태그가 붙은 문항만 찾는다(`EXISTS` + `t.name = ?`). 부분 일치는 없다. | `search.php:244-245` |
| CX-09 | 태그 이름이 50자를 넘으면 50자까지만 쓰고 경고를 낸다. 등록되지 않은 태그도 조회는 하며(결과 0건) 경고만 낸다. | `search.php:240-243`, `:258-260` |

근거 코드 인용

- CX-01 `$where = " WHERE 1=1";`
- CX-02 `$where .= " AND (title LIKE ? OR stem LIKE ?)";`
- CX-03 `if (mb_strlen($q, 'UTF-8') > 100) { $q = mb_substr($q, 0, 100, 'UTF-8');`
- CX-04 `$where .= " AND unit_code = ?";`
- CX-05 `if ($level == '') {` / `$where .= " AND level < 5";`
- CX-06 `else if (preg_match('/^[1-5]$/', $level)) {` / `$where .= " AND level = ?";`
- CX-07 `$warnings[] = '난이도는 1~5 사이여야 합니다.';` / `$values[] = (int)$level;`
- CX-08 `$where .= " AND EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id"` / `. " WHERE it.item_id = v_item_public.id AND t.name = ?)";`
- CX-09 `if (mb_strlen($tag, 'UTF-8') > 50) { $tag = mb_substr($tag, 0, 50, 'UTF-8');`

## B. 목록의 기본 정렬 · 제외 조건 · 페이지

| ID | 규칙 | 근거 |
|---|---|---|
| CX-10 | `sort` 를 주지 않으면 기본 정렬은 `level DESC, id ASC` (어려운 문항 먼저, 같으면 번호순). | `search.php:317-319` |
| CX-11 | 허용 정렬 기준은 `id · title · unit · level · created` 뿐이다. 그 밖의 값은 경고 후 기본 정렬(CX-10)로 되돌린다. | `search.php:276`, `:322-326` |
| CX-12 | `dir` 은 `asc`/`desc` 만 인정한다. 그 밖의 값은 비우고 경고를 낸다(빈 문자열은 경고 없이 비움). | `search.php:268-273` |
| CX-13 | 방향을 안 주면 `level` · `created` 는 내림차순, `id` · `title` · `unit` 은 오름차순이다. | `search.php:302-314`, `:277-299` |
| CX-14 | `unit` 정렬은 `unit_code` 다음에 `level DESC`, 그다음 `id ASC` 를 붙인다. `dir=desc` 여도 `level` 은 `DESC` 그대로다. | `search.php:293-299` |
| CX-15 | 검색은 테이블이 아니라 뷰 `v_item_public` 에서 조회한다. 뷰의 제외 조건은 이 폴더의 코드에 없어 여기서는 확정하지 않는다. | `search.php:522` |
| CX-16 | 등록 직후 문항은 상태 `R` 로 저장된다. 검색 화면 노출 여부는 CX-15 의 뷰 정의에 달려 있어 코드(이 폴더)로는 확인되지 않는다. | `register.php:93-94` |
| CX-17 | 단원 목록의 "공개 문항 수"는 `status = 'A'` 문항만 센다. | `units.php:17` |
| CX-18 | 한 페이지는 20건이다. 페이지 번호는 1 미만이면 1, 999 초과면 999 로 고정한다. 숫자가 아니면 경고하고 1페이지를 쓴다. | `search.php:336-349`, `:523` |

근거 코드 인용

- CX-10 `$orderBy = " ORDER BY level DESC, id ASC";` (`case '':`)
- CX-11 `$warnings[] = '알 수 없는 정렬 기준입니다. 기본 정렬을 사용합니다.';`
- CX-12 `if ($dir !== 'asc' && $dir !== 'desc') {`
- CX-13 `$dir = ($sort === 'level' || $sort === 'created') ? 'desc' : 'asc';` (`search.php:330`, 화면 표시용) — 실제 SQL 은 `level` · `created` 가 `dir=asc` 일 때만 오름차순, 그 외 내림차순 (`:302`, `:310`). `id` · `title` · `unit` 은 `dir=desc` 일 때만 내림차순.
- CX-14 `$orderBy = " ORDER BY unit_code DESC, level DESC, id ASC";`
- CX-15 `$from   = " FROM v_item_public";`
- CX-16 `VALUES (?, ?, ?, ?, ?, 'R', NOW(), NOW())`
- CX-17 `(SELECT COUNT(*) FROM item i WHERE i.unit_id = u.id AND i.status = 'A') AS item_count`
- CX-18 `$limit  = " LIMIT 20 OFFSET " . (int)$offset;` / `if ($page > 999) { $page = 999;`

## C. 등록 시 검증 (`register.php`)

| ID | 규칙 | 근거 |
|---|---|---|
| CX-19 | 제목은 앞뒤 공백 제거 후 5자 이상이어야 한다(공백만 있는 경우는 제거되므로 0자). | `register.php:41`, `:49-51` |
| CX-20 | 제목은 200자를 넘을 수 없다. | `register.php:52-54` |
| CX-21 | 지문(`stem`)은 앞뒤 공백 제거 후 비어 있으면 안 된다. 길이 상한은 코드에 없다. | `register.php:42`, `:56-58` |
| CX-22 | 단원은 `unit` 테이블에 존재하는 `id` 여야 한다. 아니면 "단원을 선택해야 합니다." | `register.php:60-68` |
| CX-23 | 난이도는 `1`~`5` 한 자리 숫자여야 한다. 아니면 오류로 저장하지 않는다(검색 CX-07 과 달리 거부). | `register.php:70-72` |
| CX-24 | 태그는 선택 사항이며, `tag` 테이블에 없는 id 는 오류 없이 조용히 버린다. | `register.php:74-81` |
| CX-25 | 검증 오류가 하나라도 있으면 저장하지 않고 모든 오류 문구를 함께 보여 준다. | `register.php:83`, `:133-138` |
| CX-26 | 저장 실패 시 트랜잭션을 롤백하고 "저장 중 오류가 발생했습니다."만 보여 준다. | `register.php:119-123` |

근거 코드 인용

- CX-19 `if (mb_strlen($title, 'UTF-8') < 5) {`
- CX-20 `if (mb_strlen($title, 'UTF-8') > 200) {`
- CX-21 `if ($stem === '') {`
- CX-22 `if ((string)$u['id'] === $unitId) { $unitOk = true;`
- CX-23 `if (!preg_match('/^[1-5]$/', $level)) {`
- CX-24 `if ((string)$t['id'] === (string)$tid) { $validTagIds[] = (int)$tid;`
- CX-25 `if (empty($errors)) {`
- CX-26 `$conn->rollback();` / `$errors[] = '저장 중 오류가 발생했습니다.';`

## D. 주석과 코드가 다른 곳 · 범위 밖 메모

1. **난이도 빈값 (CX-05)**: 주석은 "전체 난이도 검색 (1~5 모두 포함)" 이지만(`search.php:213`) 코드는 `level < 5` 라 5가 빠진다(`search.php:215`). 이 문서는 코드를 따른다.
2. **화면 안내 vs 코드**: 검색 폼의 난이도 선택 목록에는 `5 (최상)` 이 있어(`search.php:198`) 사용자가 5를 직접 고르면 조회된다. 빈값(전체)일 때만 5가 빠진다.
3. **수정 검증**: `legacy/item-bank-php/` 에 문항 수정 처리 파일이 없다(`index.php`, `register.php`, `search.php`, `units.php` 뿐). 따라서 "수정 시 검증"에 해당하는 규칙은 추출하지 못했다.
4. **`v_item_public` 정의 · `status` 값의 의미**: 이 폴더 코드에 정의가 없다. `A`(units.php:17 주석 "공개")와 `R`(register.php:84 주석 "검수중")의 뜻은 주석으로만 확인되며 뷰의 실제 `WHERE` 는 확인하지 못했다. 스키마 파일을 대조해야 CX-15 · CX-16 이 완성된다.
5. 검색은 난이도 · 페이지를 비정상 값이어도 거부하지 않고 경고만 낸다(CX-07, CX-18). 등록은 거부한다(CX-23). 이관 시 두 화면의 동작 차이를 유지할지 결정이 필요하다.
