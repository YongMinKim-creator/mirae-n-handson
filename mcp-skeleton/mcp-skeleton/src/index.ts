import * as z from 'zod/v4';
import { searchItems } from './itemApi.js';

server.registerTool(
  'search_items',
  {
    description: '문항 은행에서 키워드 · 단원 · 난이도로 문항을 검색한다. 문항 내용이나 태그를 확인해야 할 때 사용한다.',
    inputSchema: z.object({
      keyword: z.string().describe('검색할 키워드'),
      unit: z.string().optional().describe('단원 이름 또는 코드'),
      difficulty: z.string().optional().describe('난이도'),
      limit: z.number().int().min(1).max(20).default(5).describe('최대 건수')
    })
  },
  async ({ keyword, unit, difficulty, limit }) => {
    const items = await searchItems({ keyword, unit, difficulty, limit });
    const text = items.length > 0
      ? JSON.stringify(items, null, 2)
      : `검색 결과 없음: ${JSON.stringify({ keyword, unit, difficulty })}`;
    return { content: [{ type: 'text', text }] };
  }
);