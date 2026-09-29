package com.example.item;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 문항 API. 컨트롤러는 서비스만 호출하고, 예외는 {@code GlobalExceptionHandler} 가 처리한다.
 */
@RestController
@RequestMapping("/api")
public class ItemController {

    private final ItemService itemService;
    private final ItemSearchService itemSearchService;

    public ItemController(ItemService itemService, ItemSearchService itemSearchService) {
        this.itemService = itemService;
        this.itemSearchService = itemSearchService;
    }

    /**
     * {@code GET /api/items/search} — 문항 검색(레거시 {@code /search.php}).
     * 파라미터(q, unit, level, tag, sort, dir, page)는 모두 문자열 원문으로 받는다. 레거시처럼 같은 이름의
     * 마지막 값 · 배열의 첫 값을 쓰려면 쿼리 문자열 순서가 필요해 원문을 넘긴다. 이상한 값도 400 이 아니다.
     */
    @GetMapping("/items/search")
    public ItemSearchResponse searchItems(HttpServletRequest request) {
        return itemSearchService.search(ItemSearchParams.fromQueryString(request.getQueryString()));
    }

    /** {@code GET /api/items/{id}} — 문항 단건. */
    @GetMapping("/items/{id}")
    public ItemResponse getItem(@PathVariable Integer id) {
        return itemService.getItem(id);
    }

    /** {@code GET /api/units/{code}/items} — 단원의 공개 문항 목록. */
    @GetMapping("/units/{code}/items")
    public List<ItemResponse> listItemsByUnit(@PathVariable String code) {
        return itemService.listActiveItemsByUnit(code);
    }
}
