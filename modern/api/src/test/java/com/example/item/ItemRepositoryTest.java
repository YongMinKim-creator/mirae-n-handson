package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/** 저장소 슬라이스 — H2(MariaDB 모드)에 실제 SQL 을 날린다. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ItemRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private UnitRepository unitRepository;

    private Unit fraction;

    @BeforeEach
    void seed() {
        fraction = entityManager.persist(ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5));
        Unit decimal = entityManager.persist(ItemFixtures.unit(3, "M5-3", "소수의 곱셈", 5));
        Tag calc = entityManager.persist(ItemFixtures.tag(1, "계산"));
        Tag word = entityManager.persist(ItemFixtures.tag(2, "문장제"));

        entityManager.persist(ItemFixtures.item(null, fraction, "분모가 같은 분수의 덧셈", 2, ItemStatus.ACTIVE, calc));
        entityManager.persist(ItemFixtures.item(null, fraction, "분수 문장제", 4, ItemStatus.ACTIVE, calc, word));
        entityManager.persist(ItemFixtures.item(null, fraction, "검수 중 문항", 3, ItemStatus.REVIEWING));
        entityManager.persist(ItemFixtures.item(null, fraction, "삭제된 문항", 5, ItemStatus.DELETED));
        entityManager.persist(ItemFixtures.item(null, fraction, "난이도 5 분수", 5, ItemStatus.ACTIVE, word));
        entityManager.persist(ItemFixtures.item(null, decimal, "소수 곱셈", 1, ItemStatus.ACTIVE));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("findByUnitCodeAndStatus: 공개 문항만, level DESC · id ASC, 태그 함께 로드")
    void findsActiveItemsOrderedWithTags() {
        List<Item> items = itemRepository.findByUnitCodeAndStatus("M5-1", ItemStatus.ACTIVE);

        assertThat(items).hasSize(3);
        assertThat(items).extracting(Item::getTitle)
            .containsExactly("난이도 5 분수", "분수 문장제", "분모가 같은 분수의 덧셈");
        assertThat(items).allSatisfy(item -> assertThat(item.getStatus()).isEqualTo("A"));
        assertThat(items.get(1).getTags()).extracting(Tag::getName).containsExactly("계산", "문장제");
    }

    @Test
    @DisplayName("findWithDetailsById: 단원 · 태그가 한 번에 로드된다(지연 로딩 아님)")
    void findWithDetailsLoadsUnitAndTags() {
        Integer id = itemRepository.findByUnitCodeAndStatus("M5-1", ItemStatus.ACTIVE).get(1).getId();
        entityManager.clear();

        Item item = itemRepository.findWithDetailsById(id).orElseThrow();

        assertThat(Hibernate.isInitialized(item.getUnit())).isTrue();
        assertThat(Hibernate.isInitialized(item.getTags())).isTrue();
        assertThat(item.getUnit().getCode()).isEqualTo("M5-1");
        assertThat(item.getTags()).hasSize(2);
    }

    @Test
    @DisplayName("countByUnitIdAndStatus: 상태별 문항 수")
    void countsByStatus() {
        assertThat(itemRepository.countByUnitIdAndStatus(fraction.getId(), ItemStatus.ACTIVE)).isEqualTo(3);
        assertThat(itemRepository.countByUnitIdAndStatus(fraction.getId(), ItemStatus.DELETED)).isEqualTo(1);
        assertThat(unitRepository.findByCode("M5-3")).isPresent();
    }

    private Page<Item> search(String query, int pageSize) {
        ItemSearchCondition condition = ItemSearchCondition.from(ItemSearchParams.fromQueryString(query));
        return itemRepository.findAll(ItemSpecifications.matching(condition),
            PageRequest.of(condition.page() - 1, pageSize, condition.sort().toSort(condition.dir())));
    }

    @Test
    @DisplayName("검색 기본: 공개만 · 난이도 5 제외 · 난이도 내림차순 · 전체 건수")
    void searchDefaultExcludesLevelMaxAndInactive() {
        Page<Item> page = search(null, ItemSearchCondition.PAGE_SIZE);

        assertThat(page.getContent()).extracting(Item::getTitle)
            .containsExactly("분수 문장제", "분모가 같은 분수의 덧셈", "소수 곱셈");
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("검색: 난이도 5 를 직접 주면 조회되고, 태그는 이름 정확 일치로 AND 결합")
    void searchByExplicitLevelAndTag() {
        assertThat(search("level=5&tag=%EB%AC%B8%EC%9E%A5%EC%A0%9C", ItemSearchCondition.PAGE_SIZE).getContent())
            .extracting(Item::getTitle).containsExactly("난이도 5 분수");
        assertThat(search("tag=%EB%AC%B8%EC%9E%A5", ItemSearchCondition.PAGE_SIZE).getTotalElements()).isZero();
        // 페이지 크기 1 로 COUNT 쿼리를 따로 타게 해 태그 EXISTS 가 건수 쿼리에서도 도는지 본다
        assertThat(search("tag=%EB%AC%B8%EC%9E%A5%EC%A0%9C", 1).getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("검색: 없는 단원 · 이상한 난이도 · 범위 밖 난이도는 예외 없이 0건")
    void searchWithUnknownValuesReturnsZero() {
        assertThat(search("unit=Z9-99", ItemSearchCondition.PAGE_SIZE).getTotalElements()).isZero();
        assertThat(search("level=abc", ItemSearchCondition.PAGE_SIZE).getTotalElements()).isZero();
        assertThat(search("level=99999999999999999999", ItemSearchCondition.PAGE_SIZE).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("검색: 키워드는 지문에도 걸리고, 단원 정렬 desc 여도 단원 안은 난이도 내림차순")
    void searchByKeywordInStemSortedByUnit() {
        assertThat(search("q=%EB%B3%B8%EB%AC%B8&sort=unit&dir=desc", ItemSearchCondition.PAGE_SIZE).getContent())
            .extracting(Item::getTitle)
            .containsExactly("소수 곱셈", "분수 문장제", "분모가 같은 분수의 덧셈");
    }

    @Test
    @DisplayName("검색: 페이지를 넘기면 다음 행과 전체 건수, 범위 밖 페이지는 빈 목록에 건수 유지")
    void searchPagesInDatabase() {
        Page<Item> second = search("page=2", 2);
        assertThat(second.getContent()).extracting(Item::getTitle).containsExactly("소수 곱셈");
        assertThat(second.getTotalElements()).isEqualTo(3);

        Page<Item> beyond = search("page=5", 2);
        assertThat(beyond.getContent()).isEmpty();
        assertThat(beyond.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("findWithDetailsByIdIn: 여러 건을 단원 · 태그와 함께 로드")
    void findWithDetailsByIdInLoadsTags() {
        List<Integer> ids = search(null, ItemSearchCondition.PAGE_SIZE).getContent().stream()
            .map(Item::getId).toList();
        entityManager.clear();

        List<Item> items = itemRepository.findWithDetailsByIdIn(ids);

        assertThat(items).hasSize(3);
        assertThat(items).allSatisfy(item -> {
            assertThat(Hibernate.isInitialized(item.getUnit())).isTrue();
            assertThat(Hibernate.isInitialized(item.getTags())).isTrue();
        });
    }
}
