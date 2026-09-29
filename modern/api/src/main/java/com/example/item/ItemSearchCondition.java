package com.example.item;

/**
 * 레거시 규칙으로 정리한 문항 검색 조건(search.php:82-349).
 * 입력이 이상해도 거부하지 않고 레거시처럼 보정하거나 그대로 조회한다.
 *
 * @param keyword    trim · 100자 자르기를 마친 키워드. 빈 문자열이면 조건 없음
 * @param unitCode   trim 한 단원 코드. 빈 문자열이면 조건 없음
 * @param exactLevel 난이도 {@code (int)} 변환 값. {@code null} 이면 난이도가 비어 있던 것(BR-07)
 * @param tag        trim · 50자 자르기를 마친 태그 이름. 빈 문자열이면 조건 없음
 * @param sort       정렬 기준
 * @param dir        {@code asc} · {@code desc} · 빈 문자열
 * @param page       1 ~ {@link #MAX_PAGE} 로 보정한 페이지 번호
 */
record ItemSearchCondition(
    String keyword,
    String unitCode,
    Long exactLevel,
    String tag,
    ItemSearchSort sort,
    String dir,
    int page) {

    /** 난이도 상한. 난이도를 비우면 이 값 미만만 조회한다(BR-07). */
    static final int MAX_LEVEL = 5;
    /** 한 페이지 건수(BR-19). */
    static final int PAGE_SIZE = 20;
    static final int MIN_PAGE = 1;
    /** 페이지 번호 상한(BR-20). */
    static final int MAX_PAGE = 999;
    /** 키워드 최대 글자 수(BR-05). */
    static final int KEYWORD_MAX = 100;
    /** 태그 이름 최대 글자 수(BR-11). */
    static final int TAG_MAX = 50;

    /** 파라미터가 없을 때 레거시가 쓰는 페이지 원문(search.php:77). */
    private static final String DEFAULT_PAGE_RAW = "1";

    static ItemSearchCondition from(ItemSearchParams params) {
        // BR-05: 앞뒤 공백 제거 후 100자 초과분은 버린다(search.php:85-89)
        String keyword = PhpCompat.mbSubstr(PhpCompat.trim(orEmpty(params.q())), KEYWORD_MAX);

        // BR-06: 형식 · 대소문자 · 등록 여부와 무관하게 그대로 조회한다(search.php:109-120)
        String unitCode = PhpCompat.trim(orEmpty(params.unit()));

        // BR-07 ~ BR-09: 비면 null(level < 5), 아니면 "1"~"5" 든 그 밖의 값이든 (int) 변환 값으로 비교한다
        // (search.php:211-230 — 두 분기 모두 (int)$level 을 바인딩한다)
        String level = PhpCompat.trim(orEmpty(params.level()));
        Long exactLevel = level.isEmpty() ? null : PhpCompat.intCast(level);

        // BR-10 · BR-11: trim 후 50자 초과분은 버린다(search.php:235-243)
        String tag = PhpCompat.mbSubstr(PhpCompat.trim(orEmpty(params.tag())), TAG_MAX);

        // BR-14 · BR-15: 대소문자 · 앞뒤 공백 무시, asc/desc 가 아니면 방향 없음(search.php:266-273)
        ItemSearchSort sort = ItemSearchSort.parse(PhpCompat.asciiLower(PhpCompat.trim(orEmpty(params.sort()))));
        String dir = PhpCompat.asciiLower(PhpCompat.trim(orEmpty(params.dir())));
        if (!ItemSearchSort.DIR_ASC.equals(dir) && !ItemSearchSort.DIR_DESC.equals(dir)) {
            dir = "";
        }

        return new ItemSearchCondition(keyword, unitCode, exactLevel, tag, sort, dir, page(params.page()));
    }

    /** BR-20: 숫자만이면 정수 변환, 아니면 1. 1 미만은 1, 999 초과는 999(search.php:336-348). */
    private static int page(String raw) {
        String pageRaw = raw == null ? DEFAULT_PAGE_RAW : raw;
        long page = PhpCompat.isDigits(pageRaw) ? PhpCompat.intCast(pageRaw) : MIN_PAGE;
        if (page < MIN_PAGE) {
            return MIN_PAGE;
        }
        return (int) Math.min(page, MAX_PAGE);
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
