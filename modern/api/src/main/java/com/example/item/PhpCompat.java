package com.example.item;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 레거시 PHP(7.4) 문항 검색({@code legacy/item-bank-php/search.php})의 언어 동작을 그대로 흉내 낸다.
 * 동작 보존이 목적이므로 이상해 보이는 동작도 고치지 않는다. 어떤 입력에도 예외를 던지지 않는다.
 */
final class PhpCompat {

    /** PHP {@code trim()} 기본 문자 집합. */
    private static final String TRIM_CHARS = " \t\n\r\0\u000B";

    /** PHP 숫자 문자열 앞부분(앞 공백 · 부호 · 소수 · 지수). {@code (int)} 변환은 이 부분만 본다. */
    private static final Pattern NUMERIC_PREFIX = Pattern.compile(
        "^[ \\t\\n\\r\\u000B\\f]*([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)");

    /** PCRE {@code /^[0-9]+$/} — D 수식자가 없어 {@code $} 가 끝의 줄바꿈 하나 앞에서도 맞는다. */
    private static final Pattern DIGITS = Pattern.compile("[0-9]+\n?");

    /** PHP 배열 키 중 정수로 바뀌는 문자열. */
    private static final Pattern INTEGER_KEY = Pattern.compile("0|-?[1-9][0-9]*");

    private static final int LONG_BITS = 63;

    private PhpCompat() {
    }

    /** PHP {@code trim($s)}. */
    static String trim(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && TRIM_CHARS.indexOf(s.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && TRIM_CHARS.indexOf(s.charAt(end - 1)) >= 0) {
            end--;
        }
        return s.substring(start, end);
    }

    /** PHP {@code mb_strlen($s, 'UTF-8')} — 코드포인트 수. */
    static int mbLength(String s) {
        return s.codePointCount(0, s.length());
    }

    /** PHP {@code mb_substr($s, 0, $length, 'UTF-8')}. */
    static String mbSubstr(String s, int length) {
        if (mbLength(s) <= length) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, length));
    }

    /** PHP {@code strtolower()} — 기본(C) 로캘이라 ASCII 만 바꾼다. */
    static String asciiLower(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            out.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c);
        }
        return out.toString();
    }

    /** PHP {@code preg_match('/^[0-9]+$/', $s)}. */
    static boolean isDigits(String s) {
        return DIGITS.matcher(s).matches();
    }

    /**
     * PHP 7.4 {@code (int)$s}. 숫자 앞부분만 읽고 나머지는 버린다({@code "5abc"} → 5, {@code "abc"} → 0).
     * 소수 · 지수는 double 로 읽어 0 쪽으로 자르고, long 범위를 넘으면 끝값으로 포화, 무한대는 0.
     */
    static long intCast(String s) {
        Matcher m = NUMERIC_PREFIX.matcher(s);
        if (!m.find()) {
            return 0L;
        }
        String number = m.group(1);
        boolean integral = number.indexOf('.') < 0 && number.indexOf('e') < 0 && number.indexOf('E') < 0;
        if (integral) {
            BigInteger value = new BigInteger(number);
            if (value.bitLength() <= LONG_BITS) {
                return value.longValue();
            }
        }
        double d = Double.parseDouble(number);
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return 0L;
        }
        return (long) d;
    }

    /**
     * 원문 쿼리 문자열을 PHP {@code $_GET} 규칙으로 해석한 뒤, 이름마다 문자열 하나로 줄인다.
     * <ul>
     *   <li>같은 이름이 여러 번 오면 마지막 값이 남는다.</li>
     *   <li>{@code x[]=a&x[]=b} 는 배열이 되고, 레거시처럼 첫 값({@code reset()})을 쓴다(search.php:53-80).</li>
     *   <li>일반 값과 배열 값이 섞이면 뒤에 온 쪽이 남는다. 중첩 배열의 첫 값은 문자열 {@code "Array"} 다.</li>
     *   <li>이름 · 값은 {@code urldecode} 규칙({@code +} → 공백, 잘못된 {@code %} 는 그대로)으로 푼다.</li>
     * </ul>
     *
     * @param rawQuery 디코딩하지 않은 쿼리 문자열. {@code null} 이면 빈 결과
     * @return 이름 → 값. 없는 이름은 들어 있지 않다
     */
    static Map<String, String> queryParams(String rawQuery) {
        Map<String, Object> vars = new LinkedHashMap<>();
        if (rawQuery != null) {
            for (String pair : rawQuery.split("&")) {
                if (pair.isEmpty()) {
                    continue;
                }
                int eq = pair.indexOf('=');
                String name = urlDecode(eq < 0 ? pair : pair.substring(0, eq));
                String value = eq < 0 ? "" : urlDecode(pair.substring(eq + 1));
                register(vars, name, value);
            }
        }
        Map<String, String> result = new LinkedHashMap<>();
        vars.forEach((name, value) -> result.put(name, firstScalar(value)));
        return result;
    }

    /** PHP {@code php_url_decode}. */
    static String urlDecode(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            if (b == '+') {
                out.write(' ');
            } else if (b == '%' && i + 2 < bytes.length && isHex(bytes[i + 1]) && isHex(bytes[i + 2])) {
                out.write(Character.digit(bytes[i + 1], 16) * 16 + Character.digit(bytes[i + 2], 16));
                i += 2;
            } else {
                out.write(b);
            }
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static boolean isHex(byte b) {
        return Character.digit(b, 16) >= 0;
    }

    /** PHP {@code php_register_variable_ex} 의 이름 해석(앞 공백 제거, 공백 · 점 → 밑줄, 대괄호 → 배열). */
    private static void register(Map<String, Object> vars, String rawName, String value) {
        int start = 0;
        while (start < rawName.length() && rawName.charAt(start) == ' ') {
            start++;
        }
        String name = rawName.substring(start);
        StringBuilder base = new StringBuilder();
        int bracket = -1;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '[') {
                bracket = i;
                break;
            }
            base.append(c == ' ' || c == '.' ? '_' : c);
        }
        if (base.isEmpty()) {
            return;
        }
        if (bracket < 0) {
            vars.put(base.toString(), value);
            return;
        }

        List<String> indices = new ArrayList<>();
        int open = bracket;
        while (true) {
            int close = name.indexOf(']', open + 1);
            if (close < 0) {
                break;
            }
            indices.add(name.substring(open + 1, close));
            if (close + 1 < name.length() && name.charAt(close + 1) == '[') {
                open = close + 1;
            } else {
                break;
            }
        }
        if (indices.isEmpty()) {
            // 닫는 ']' 가 없으면 배열이 아니다. 여는 '[' 만 '_' 로 바뀐 이름이 된다.
            vars.put(base + "_" + name.substring(bracket + 1), value);
            return;
        }

        PhpArray array = vars.get(base.toString()) instanceof PhpArray existing ? existing : new PhpArray();
        vars.put(base.toString(), array);
        for (int i = 0; i < indices.size() - 1; i++) {
            array = array.childArray(indices.get(i));
        }
        array.put(indices.get(indices.size() - 1), value);
    }

    /** 레거시의 {@code is_array($v) ? (string)reset($v) : (string)$v}. */
    private static String firstScalar(Object value) {
        if (value instanceof PhpArray array) {
            Object first = array.first();
            if (first == null) {
                return "";
            }
            return first instanceof PhpArray ? "Array" : (String) first;
        }
        return (String) value;
    }

    /** 삽입 순서를 지키는 PHP 배열. 정수 모양 키는 정수로 바꾸고, 빈 인덱스는 다음 정수 키로 붙인다. */
    private static final class PhpArray {

        private final Map<Object, Object> entries = new LinkedHashMap<>();
        private long nextFree;

        void put(String index, Object value) {
            entries.put(key(index), value);
        }

        PhpArray childArray(String index) {
            Object key = key(index);
            if (entries.get(key) instanceof PhpArray child) {
                return child;
            }
            PhpArray child = new PhpArray();
            entries.put(key, child);
            return child;
        }

        Object first() {
            return entries.isEmpty() ? null : entries.values().iterator().next();
        }

        private Object key(String index) {
            if (index.isEmpty()) {
                return nextFree++;
            }
            if (INTEGER_KEY.matcher(index).matches()) {
                BigInteger value = new BigInteger(index);
                if (value.bitLength() <= LONG_BITS) {
                    long k = value.longValue();
                    if (k >= nextFree) {
                        nextFree = k + 1;
                    }
                    return k;
                }
            }
            return index;
        }
    }
}
