package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

/** PHP 7.4 동작 흉내 — 레거시에서 직접 확인한 값(8081)을 기준으로 적었다. */
@ActiveProfiles("test")
class PhpCompatTest {

    @Test
    @DisplayName("trim: PHP 기본 문자(공백 · 탭 · 줄바꿈 · NUL · 수직 탭)만 앞뒤에서 지운다")
    void trimRemovesPhpDefaultCharacters() {
        assertThat(PhpCompat.trim(" \t\n\r\0\u000B분수 \u000B")).isEqualTo("분수");
        assertThat(PhpCompat.trim("\f분수")).isEqualTo("\f분수");
        assertThat(PhpCompat.trim("   ")).isEmpty();
    }

    @Test
    @DisplayName("mbSubstr: 코드포인트 단위로 자른다(UTF-16 단위가 아니다)")
    void mbSubstrCountsCodePoints() {
        assertThat(PhpCompat.mbSubstr("가".repeat(101), 100)).isEqualTo("가".repeat(100));
        assertThat(PhpCompat.mbSubstr("😀😀😀", 2)).isEqualTo("😀😀");
        assertThat(PhpCompat.mbLength("😀😀")).isEqualTo(2);
        assertThat(PhpCompat.mbSubstr("짧음", 100)).isEqualTo("짧음");
    }

    @Test
    @DisplayName("intCast: PHP (int) 변환 — 숫자 앞부분만 읽고 소수 · 지수는 0 쪽으로 자른다")
    void intCastFollowsPhpNumericPrefix() {
        assertThat(PhpCompat.intCast("05")).isEqualTo(5);
        assertThat(PhpCompat.intCast("5.9")).isEqualTo(5);
        assertThat(PhpCompat.intCast("+5")).isEqualTo(5);
        assertThat(PhpCompat.intCast("1e0")).isEqualTo(1);
        assertThat(PhpCompat.intCast("1e1")).isEqualTo(10);
        assertThat(PhpCompat.intCast("5abc")).isEqualTo(5);
        assertThat(PhpCompat.intCast("-1")).isEqualTo(-1);
        assertThat(PhpCompat.intCast("abc")).isZero();
        assertThat(PhpCompat.intCast(".5")).isZero();
        assertThat(PhpCompat.intCast("0x1A")).isZero();
        assertThat(PhpCompat.intCast(" \f3")).isEqualTo(3);
    }

    @Test
    @DisplayName("intCast: long 범위를 넘으면 끝값으로 포화, 무한대는 0 — 예외를 던지지 않는다")
    void intCastSaturatesWithoutThrowing() {
        assertThat(PhpCompat.intCast("99999999999999999999")).isEqualTo(Long.MAX_VALUE);
        assertThat(PhpCompat.intCast("-99999999999999999999")).isEqualTo(Long.MIN_VALUE);
        assertThat(PhpCompat.intCast("1e999")).isZero();
    }

    @Test
    @DisplayName("isDigits: /^[0-9]+$/ — 끝의 줄바꿈 하나는 허용, 부호 · 공백 · 빈 문자열은 거부")
    void isDigitsMatchesPcreDollar() {
        assertThat(PhpCompat.isDigits("2")).isTrue();
        assertThat(PhpCompat.isDigits("2\n")).isTrue();
        assertThat(PhpCompat.isDigits("2\n\n")).isFalse();
        assertThat(PhpCompat.isDigits("-1")).isFalse();
        assertThat(PhpCompat.isDigits(" 2")).isFalse();
        assertThat(PhpCompat.isDigits("")).isFalse();
    }

    @Test
    @DisplayName("asciiLower: ASCII 대문자만 소문자로 바꾼다")
    void asciiLowerChangesAsciiOnly() {
        assertThat(PhpCompat.asciiLower(" DROP ")).isEqualTo(" drop ");
        assertThat(PhpCompat.asciiLower("ÀTitle")).isEqualTo("Àtitle");
    }

    @Test
    @DisplayName("queryParams: 같은 이름은 마지막 값, 배열은 첫 값, 섞이면 뒤에 온 쪽")
    void queryParamsFollowsPhpGetRules() {
        assertThat(PhpCompat.queryParams("level=3&level=4")).containsEntry("level", "4");
        assertThat(PhpCompat.queryParams("level%5B%5D=5&level%5B%5D=1")).containsEntry("level", "5");
        assertThat(PhpCompat.queryParams("level=3&level%5B%5D=5")).containsEntry("level", "5");
        assertThat(PhpCompat.queryParams("level%5B%5D=5&level=3")).containsEntry("level", "3");
        assertThat(PhpCompat.queryParams("level[1]=a&level[0]=b")).containsEntry("level", "a");
        assertThat(PhpCompat.queryParams("level[a][b]=5")).containsEntry("level", "Array");
    }

    @Test
    @DisplayName("queryParams: urldecode 규칙 — + 는 공백, 잘못된 % 는 그대로, 값 없는 이름은 빈 문자열")
    void queryParamsDecodesLikePhp() {
        Map<String, String> params = PhpCompat.queryParams("q=+%EB%B6%84%EC%88%98+&tag=%zz&unit&&sort=%20DROP%20");

        assertThat(params)
            .containsEntry("q", " 분수 ")
            .containsEntry("tag", "%zz")
            .containsEntry("unit", "")
            .containsEntry("sort", " DROP ");
        assertThat(PhpCompat.queryParams(null)).isEmpty();
    }

    @Test
    @DisplayName("queryParams: 이름의 점 · 공백은 밑줄, 닫히지 않은 [ 는 밑줄 이름")
    void queryParamsManglesNamesLikePhp() {
        assertThat(PhpCompat.queryParams("q.x=1")).containsEntry("q_x", "1").doesNotContainKey("q");
        assertThat(PhpCompat.queryParams("level[=5")).containsEntry("level_", "5").doesNotContainKey("level");
        assertThat(PhpCompat.queryParams("%20page=2")).containsEntry("page", "2");
    }
}
