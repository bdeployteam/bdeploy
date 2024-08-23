package io.bdeploy.minion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestMinion.class)
class EncryptDescryptTest {

    private static final class TestPayload {

        public String testString;
        public byte[] testBytes;
    }

    @Test
    void testEncryptDecrypt(MinionRoot minion) {
        TestPayload pl = new TestPayload();
        pl.testString = "Test String";
        pl.testBytes = new byte[] { 0xC, 0xA, 0xF, 0xE, 0xB, 0xA, 0xB, 0xE };

        String enc = minion.getEncryptedPayload(pl);

        assertNotNull(enc);
        assertFalse(enc.isEmpty());

        TestPayload dec = minion.getDecryptedPayload(enc, TestPayload.class);

        assertEquals(pl.testString, dec.testString);
        assertArrayEquals(pl.testBytes, dec.testBytes);
    }

}
