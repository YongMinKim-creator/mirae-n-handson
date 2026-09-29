package com.example.item;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * 문항 검색의 WHERE 조건(search.php:44-261). 조건은 모두 AND 로 잇는다(BR-02).
 * 비교는 DB 에서 하므로 대소문자 · 뒤 공백 처리는 레거시와 같은 콜레이션을 따른다.
 */
final class ItemSpecifications {

    private ItemSpecifications() {
    }

    static Specification<Item> matching(ItemSearchCondition condition) {
        Specification<Item> spec = Specification.where(isActive());
        if (!condition.keyword().isEmpty()) {
            spec = spec.and(keywordLike(condition.keyword()));
        }
        if (!condition.unitCode().isEmpty()) {
            spec = spec.and(unitCodeEquals(condition.unitCode()));
        }
        spec = spec.and(condition.exactLevel() == null
            ? levelBelow(ItemSearchCondition.MAX_LEVEL)
            : levelEquals(condition.exactLevel()));
        if (!condition.tag().isEmpty()) {
            spec = spec.and(hasTag(condition.tag()));
        }
        return spec;
    }

    /** BR-01: 공개 문항만 — 레거시 뷰 v_item_public 의 {@code status = 'A'}(01-schema.sql:70). */
    static Specification<Item> isActive() {
        return (root, query, cb) -> cb.equal(root.get("status"), ItemStatus.ACTIVE);
    }

    /** BR-04: 제목 또는 지문 부분 일치. {@code %} · {@code _} 를 이스케이프하지 않는다(search.php:97-98). */
    static Specification<Item> keywordLike(String keyword) {
        String pattern = "%" + keyword + "%";
        return (root, query, cb) -> cb.or(cb.like(root.get("title"), pattern), cb.like(root.get("stem"), pattern));
    }

    /** BR-06: 단원 코드 일치. 없는 코드여도 오류 없이 0건이다. */
    static Specification<Item> unitCodeEquals(String unitCode) {
        return (root, query, cb) -> cb.equal(root.get("unit").get("code"), unitCode);
    }

    /** BR-07: 난이도가 비어 있으면 상한 미만만(search.php:214-215). */
    static Specification<Item> levelBelow(int maxLevel) {
        return (root, query, cb) -> cb.lessThan(root.get("level"), maxLevel);
    }

    /** BR-08 · BR-09: 난이도 일치. 컬럼 범위를 벗어나는 값은 어떤 행과도 같지 않다(0건). */
    static Specification<Item> levelEquals(long level) {
        if (level < Integer.MIN_VALUE || level > Integer.MAX_VALUE) {
            return (root, query, cb) -> cb.disjunction();
        }
        int value = (int) level;
        return (root, query, cb) -> cb.equal(root.get("level"), value);
    }

    /** BR-10: 이름이 정확히 같은 태그가 붙은 문항(search.php:244-245). */
    static Specification<Item> hasTag(String tagName) {
        return (root, query, cb) -> {
            Subquery<Integer> sub = query.subquery(Integer.class);
            Root<Item> tagged = sub.from(Item.class);
            Join<Item, Tag> tag = tagged.join("tags");
            sub.select(tagged.get("id"))
                .where(cb.equal(tagged, root), cb.equal(tag.get("name"), tagName));
            return cb.exists(sub);
        };
    }
}
