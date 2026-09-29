package com.example.item;

import java.util.Map;

/**
 * 문항 검색 요청 파라미터 원문. 레거시 {@code search.php} 와 같은 이름을 쓴다.
 * 값이 {@code null} 이면 파라미터가 없었던 것이고, 빈 문자열이면 {@code q=} 처럼 값만 비어 있던 것이다.
 */
public record ItemSearchParams(
    String q,
    String unit,
    String level,
    String tag,
    String sort,
    String dir,
    String page) {

    /** 원문 쿼리 문자열을 PHP {@code $_GET} 규칙으로 읽는다(search.php:53-80). */
    public static ItemSearchParams fromQueryString(String rawQuery) {
        Map<String, String> params = PhpCompat.queryParams(rawQuery);
        return new ItemSearchParams(
            params.get("q"),
            params.get("unit"),
            params.get("level"),
            params.get("tag"),
            params.get("sort"),
            params.get("dir"),
            params.get("page"));
    }
}
