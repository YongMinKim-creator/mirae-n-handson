package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ItemSearchServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private ItemSearchService itemSearchService;

    private final Unit unit = ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5);

    private static ItemSearchParams params(String query) {
        return ItemSearchParams.fromQueryString(query);
    }

    @Test
    @DisplayName("search: 전체 0건이면 rows 는 비고 message 는 '검색 결과가 없습니다'")
    void searchWithNoMatchReturnsMessage() {
        when(itemRepository.findAll(ArgumentMatchers.<Specification<Item>>any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, ItemSearchCondition.PAGE_SIZE), 0));

        ItemSearchResponse response = itemSearchService.search(params("unit=Z9-99"));

        assertThat(response.rows()).isEmpty();
        assertThat(response.count()).isZero();
        assertThat(response.message()).isEqualTo("검색 결과가 없습니다");
        verify(itemRepository, never()).findWithDetailsByIdIn(anyCollection());
    }

    @Test
    @DisplayName("search: 행은 페이지 순서 그대로, count 는 전체 건수, message 는 null")
    void searchKeepsPageOrder() {
        Item hard = ItemFixtures.item(18, unit, "분수의 뺄셈 문장제", 4, ItemStatus.ACTIVE,
            ItemFixtures.tag(2, "문장제"), ItemFixtures.tag(4, "오답률높음"));
        Item easy = ItemFixtures.item(1, unit, "분모가 같은 분수의 덧셈", 1, ItemStatus.ACTIVE);
        Pageable firstPage = PageRequest.of(0, ItemSearchCondition.PAGE_SIZE);
        when(itemRepository.findAll(ArgumentMatchers.<Specification<Item>>any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(hard, easy), firstPage, 25));
        when(itemRepository.findWithDetailsByIdIn(List.of(18, 1))).thenReturn(List.of(easy, hard));

        ItemSearchResponse response = itemSearchService.search(params(null));

        assertThat(response.rows()).extracting(ItemSearchRow::id).containsExactly(18, 1);
        assertThat(response.rows().get(0))
            .isEqualTo(new ItemSearchRow(18, "분수의 뺄셈 문장제", "M5-1", 4, List.of("문장제", "오답률높음")));
        assertThat(response.rows().get(1).tags()).isEmpty();
        assertThat(response.count()).isEqualTo(25);
        assertThat(response.message()).isNull();
    }

    @Test
    @DisplayName("search: 마지막 페이지를 넘으면 rows 는 비지만 count 는 전체 건수, message 는 null(BR-21)")
    void searchBeyondLastPageKeepsCount() {
        when(itemRepository.findAll(ArgumentMatchers.<Specification<Item>>any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, ItemSearchCondition.PAGE_SIZE), 20));

        ItemSearchResponse response = itemSearchService.search(params("page=2"));

        assertThat(response.rows()).isEmpty();
        assertThat(response.count()).isEqualTo(20);
        assertThat(response.message()).isNull();
    }

    @Test
    @DisplayName("search: 페이지 1000 은 999 페이지(오프셋 998 × 20)로, 정렬은 기본 정렬로 묻는다")
    void searchPassesClampedPageAndSort() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(itemRepository.findAll(ArgumentMatchers.<Specification<Item>>any(), pageable.capture()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(998, ItemSearchCondition.PAGE_SIZE), 20));

        itemSearchService.search(params("page=1000&sort=%20DROP%20"));

        assertThat(pageable.getValue().getPageNumber()).isEqualTo(998);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(ItemSearchCondition.PAGE_SIZE);
        assertThat(pageable.getValue().getSort()).isEqualTo(ItemSearchSort.DEFAULT.toSort(""));
    }
}
