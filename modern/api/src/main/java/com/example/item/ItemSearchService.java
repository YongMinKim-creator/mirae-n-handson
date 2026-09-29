package com.example.item;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문항 검색 — 레거시 {@code legacy/item-bank-php/search.php} 의 동작을 그대로 옮긴다.
 * 입력이 이상하거나 없는 단원 · 태그여도 오류가 아니라 0건으로 응답한다.
 */
@Service
@Transactional(readOnly = true)
public class ItemSearchService {

    /** 전체 건수가 0 일 때의 안내 문구(search.php:657-658). */
    static final String NO_RESULT_MESSAGE = "검색 결과가 없습니다";

    private static final Logger log = LoggerFactory.getLogger(ItemSearchService.class);

    private final ItemRepository itemRepository;

    public ItemSearchService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /**
     * 조건에 맞는 공개 문항 한 페이지와 전체 건수.
     * 정렬 · 페이지 자르기는 DB 에서 한다(문자열 정렬이 DB 콜레이션을 따라야 레거시와 같다).
     */
    public ItemSearchResponse search(ItemSearchParams params) {
        ItemSearchCondition condition = ItemSearchCondition.from(params);
        Pageable pageable = PageRequest.of(
            condition.page() - 1, ItemSearchCondition.PAGE_SIZE, condition.sort().toSort(condition.dir()));

        Page<Item> page = itemRepository.findAll(ItemSpecifications.matching(condition), pageable);
        List<ItemSearchRow> rows = loadRows(page.getContent());
        long total = page.getTotalElements();

        log.debug("item search: page={}, sort={}, rows={}, total={}",
            condition.page(), condition.sort(), rows.size(), total);
        // BR-21: 판정 기준은 현재 페이지가 아니라 전체 건수(search.php:655-659)
        return new ItemSearchResponse(rows, total, total == 0 ? NO_RESULT_MESSAGE : null);
    }

    /** 페이지의 문항을 단원 · 태그와 함께 다시 읽고, 페이지 순서대로 행을 만든다. */
    private List<ItemSearchRow> loadRows(List<Item> pageItems) {
        if (pageItems.isEmpty()) {
            return List.of();
        }
        List<Integer> ids = pageItems.stream().map(Item::getId).toList();
        Map<Integer, Item> byId = itemRepository.findWithDetailsByIdIn(ids).stream()
            .collect(Collectors.toMap(Item::getId, Function.identity()));
        return ids.stream()
            .map(byId::get)
            .filter(Objects::nonNull)
            .map(ItemSearchRow::from)
            .toList();
    }
}
