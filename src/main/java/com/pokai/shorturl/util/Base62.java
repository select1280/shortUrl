package com.pokai.shorturl.util;

/**
 * Base62 編解碼工具。
 * 字元表刻意用 0-9 A-Z a-z 的順序，讓「數值大小」與「字典序」在等長字串下一致，
 * 之後若要做 range 查詢或排序比較好處理。
 */
public final class Base62 {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    private Base62() {
    }

    /**
     * 把非負整數轉成 Base62 字串。
     *
     * @param value     要編碼的數值（通常是 DB 的自增 id）
     * @param minLength 最小長度，不足時左補 '0'（值為 0，不影響解碼結果）
     */
    public static String encode(long value, int minLength) {
        if (value < 0) {
            throw new IllegalArgumentException("Base62 只接受非負整數: " + value);
        }

        StringBuilder sb = new StringBuilder();
        long remaining = value;
        do {
            sb.append(ALPHABET.charAt((int) (remaining % BASE)));
            remaining /= BASE;
        } while (remaining > 0);

        // 上面是由低位往高位取，所以要反轉
        sb.reverse();

        while (sb.length() < minLength) {
            sb.insert(0, ALPHABET.charAt(0));
        }
        return sb.toString();
    }

    public static String encode(long value) {
        return encode(value, 1);
    }

    /**
     * 把 Base62 字串轉回數值。左補的 '0' 會自然被忽略。
     */
    public static long decode(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("短碼不可為空");
        }

        long result = 0L;
        for (int i = 0; i < code.length(); i++) {
            int digit = ALPHABET.indexOf(code.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException("短碼含有非 Base62 字元: " + code.charAt(i));
            }
            result = result * BASE + digit;
        }
        return result;
    }
}
