package com.example.item;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;

/**
 * 문항 검색 정렬 기준(search.php:275-327). 동순위는 항상 {@code id} 오름차순이다(BR-16).
 */
enum ItemSearchSort {

    ID("id"),
    TITLE("title"),
    UNIT("unit"),
    LEVEL("level"),
    CREATED("created"),
    /** 기본 정렬 — 난이도 내림차순, 같은 난이도면 id 오름차순(BR-13). */
    DEFAULT("");

    static final String DIR_ASC = "asc";
    static final String DIR_DESC = "desc";

    private final String key;

    ItemSearchSort(String key) {
        this.key = key;
    }

    /** 소문자 · trim 을 마친 정렬 기준. 모르는 값은 기본 정렬(BR-14). */
    static ItemSearchSort parse(String normalized) {
        for (ItemSearchSort sort : values()) {
            if (sort.key.equals(normalized)) {
                return sort;
            }
        }
        return DEFAULT;
    }

    /**
     * 정렬 방향을 적용한 {@link Sort}. {@code dir} 은 {@code asc} · {@code desc} · 빈 문자열 중 하나다.
     * id · title · unit 은 {@code desc} 일 때만 내림차순, level · created 는 {@code asc} 일 때만 오름차순(BR-15).
     */
    Sort toSort(String dir) {
        boolean desc = DIR_DESC.equals(dir);
        boolean asc = DIR_ASC.equals(dir);
        return switch (this) {
            case ID -> Sort.by(desc ? Order.desc("id") : Order.asc("id"));
            case TITLE -> Sort.by(desc ? Order.desc("title") : Order.asc("title"), Order.asc("id"));
            // 단원 안에서는 방향과 무관하게 난이도 내림차순(BR-17, search.php:295, :297)
            case UNIT -> Sort.by(
                desc ? Order.desc("unit.code") : Order.asc("unit.code"), Order.desc("level"), Order.asc("id"));
            case LEVEL -> Sort.by(asc ? Order.asc("level") : Order.desc("level"), Order.asc("id"));
            case CREATED -> Sort.by(asc ? Order.asc("createdAt") : Order.desc("createdAt"), Order.asc("id"));
            case DEFAULT -> Sort.by(Order.desc("level"), Order.asc("id"));
        };
    }
}
