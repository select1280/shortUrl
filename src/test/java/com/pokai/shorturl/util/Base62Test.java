package com.pokai.shorturl.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Base62Test {

    @Test
    void encode_smallNumbers() {
        assertEquals("0", Base62.encode(0));
        assertEquals("9", Base62.encode(9));
        assertEquals("A", Base62.encode(10));
        assertEquals("a", Base62.encode(36));
        assertEquals("10", Base62.encode(62));
    }

    @Test
    void encode_padsToMinLength() {
        assertEquals("000001", Base62.encode(1, 6));
        // 超過最小長度時不截斷
        assertEquals("1000000", Base62.encode(56800235584L, 6));
    }

    @Test
    void decode_isInverseOfEncode() {
        for (long value : new long[]{0, 1, 61, 62, 12345, 999_999_999L}) {
            assertEquals(value, Base62.decode(Base62.encode(value, 6)), "value=" + value);
        }
    }

    @Test
    void encode_rejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> Base62.encode(-1));
    }

    @Test
    void decode_rejectsInvalidCharacter() {
        assertThrows(IllegalArgumentException.class, () -> Base62.decode("abc-def"));
    }
}
