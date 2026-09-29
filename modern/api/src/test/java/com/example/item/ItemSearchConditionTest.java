package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.test.context.ActiveProfiles;

/** 검색 조건 보정 — docs/item-bank/BUSINESS-RULES.md BR-05 ~ BR-20. */
@ActiveProfiles("test")
class ItemSearchConditionTest {

    private static ItemSearchCondition from(String query) {
        return ItemSearchCondition.from(ItemSearchParams.fromQueryString(query));
    }

    @Test
    @DisplayName("파라미터 없음: 조건 없음 · 난이도 비어 있음 · 기본 정렬 · 1페이지")
    void emptyQueryUsesDefaults() {
        ItemSearchCondition condition = from(null);

        assertThat(condition.keyword()).isEmpty();
        assertThat(condition.unitCode()).isEmpty();
        assertThat(condition.exactLevel()).isNull();
        assertThat(condition.tag()).isEmpty();
        assertThat(condition.sort()).isEqualTo(ItemSearchSort.DEFAULT);
        assertThat(condition.dir()).isEmpty();
        assertThat(condition.page()).isEqualTo(1);
    }

    @Test
    @DisplayName("BR-05: 키워드는 trim 후 100자까지만 쓴다")
    void keywordIsTrimmedAndTruncated() {
        assertThat(from("q=%20%20%20").keyword()).isEmpty();
        assertThat(from("q=" + "a".repeat(5000)).keyword()).hasSize(ItemSearchCondition.KEYWORD_MAX);
        assertThat(from("q=+%EB%B6%84%EC%88%98+").keyword()).isEqualTo("분수");
    }

    @Test
    @DisplayName("BR-11: 태그는 trim 후 50자까지만 쓴다")
    void tagIsTruncated() {
        assertThat(from("tag=" + "a".repeat(51)).tag()).isEqualTo("a".repeat(ItemSearchCondition.TAG_MAX));
        assertThat(from("tag=" + "a".repeat(50)).tag()).hasSize(ItemSearchCondition.TAG_MAX);
    }

    @Test
    @DisplayName("BR-07 ~ BR-09: 빈 난이도는 null, 그 밖의 값은 PHP (int) 변환 값")
    void levelUsesPhpIntCast() {
        assertThat(from("level=").exactLevel()).isNull();
        assertThat(from("level=%20").exactLevel()).isNull();
        assertThat(from("level=3").exactLevel()).isEqualTo(3L);
        assertThat(from("level=05").exactLevel()).isEqualTo(5L);
        assertThat(from("level=-1").exactLevel()).isEqualTo(-1L);
        assertThat(from("level=abc").exactLevel()).isZero();
        assertThat(from("level%5B%5D=5&level%5B%5D=1").exactLevel()).isEqualTo(5L);
    }

    @Test
    @DisplayName("BR-20: 숫자가 아니면 1, 0 이면 1, 999 초과는 999, 끝 줄바꿈은 허용")
    void pageIsClamped() {
        assertThat(from("page=").page()).isEqualTo(1);
        assertThat(from("page=-1").page()).isEqualTo(1);
        assertThat(from("page=0").page()).isEqualTo(1);
        assertThat(from("page=999").page()).isEqualTo(ItemSearchCondition.MAX_PAGE);
        assertThat(from("page=1000").page()).isEqualTo(ItemSearchCondition.MAX_PAGE);
        assertThat(from("page=99999999999999999999").page()).isEqualTo(ItemSearchCondition.MAX_PAGE);
        assertThat(from("page=2%0A").page()).isEqualTo(2);
        assertThat(from("page=%202").page()).isEqualTo(1);
    }

    @Test
    @DisplayName("BR-14 · BR-15: 정렬 기준 · 방향은 대소문자 · 공백 무시, 모르는 값은 기본 · 빈 방향")
    void sortAndDirAreNormalized() {
        ItemSearchCondition unknown = from("sort=%20DROP%20&dir=up");
        assertThat(unknown.sort()).isEqualTo(ItemSearchSort.DEFAULT);
        assertThat(unknown.dir()).isEmpty();

        ItemSearchCondition title = from("sort=%20Title&dir=DESC%20");
        assertThat(title.sort()).isEqualTo(ItemSearchSort.TITLE);
        assertThat(title.dir()).isEqualTo("desc");
    }

    @Test
    @DisplayName("BR-13 · BR-15 ~ BR-17: 기준별 기본 방향, unit 은 단원 안 난이도 내림차순, 동순위 id 오름차순")
    void sortMapsToLegacyOrderBy() {
        assertThat(ItemSearchSort.DEFAULT.toSort("")).isEqualTo(Sort.by(Order.desc("level"), Order.asc("id")));
        assertThat(ItemSearchSort.LEVEL.toSort("")).isEqualTo(Sort.by(Order.desc("level"), Order.asc("id")));
        assertThat(ItemSearchSort.LEVEL.toSort("asc")).isEqualTo(Sort.by(Order.asc("level"), Order.asc("id")));
        assertThat(ItemSearchSort.ID.toSort("")).isEqualTo(Sort.by(Order.asc("id")));
        assertThat(ItemSearchSort.TITLE.toSort("desc")).isEqualTo(Sort.by(Order.desc("title"), Order.asc("id")));
        assertThat(ItemSearchSort.UNIT.toSort("desc"))
            .isEqualTo(Sort.by(Order.desc("unit.code"), Order.desc("level"), Order.asc("id")));
        assertThat(ItemSearchSort.CREATED.toSort(""))
            .isEqualTo(Sort.by(Order.desc("createdAt"), Order.asc("id")));
    }
}
