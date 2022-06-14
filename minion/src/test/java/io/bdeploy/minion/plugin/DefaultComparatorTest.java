package io.bdeploy.minion.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class DefaultComparatorTest {

    @Test
    void testComparator() {
        DefaultTagAsVersionComparator c = new DefaultTagAsVersionComparator();

        assertTrue(c.compare("123", null) > 0);
        assertTrue(c.compare(null, "123") < 0);

        assertTrue(c.compare("123", "234") < 0);
        assertTrue(c.compare("234", "123") > 0);
        assertEquals(0, c.compare("123", "123"));

        assertTrue(c.compare("1.2.3", "2.3.4") < 0);
        assertTrue(c.compare("2.3.4", "1.2.3") > 0);

        assertTrue(c.compare("1.2.3", "1.2.3.X") < 0);
        assertTrue(c.compare("1.2.3.X", "1.2.3") > 0);

        assertTrue(c.compare("1.2.3.N", "1.2.3.X") < 0);
        assertTrue(c.compare("1.2.3.X", "1.2.3.N") > 0);

        assertTrue(c.compare("2.2.3.R202112201039", "1.2.3.R202112201040") > 0);
        assertTrue(c.compare("1.2.3.R202112201040", "2.2.3.R202112201039") < 0);
        assertTrue(c.compare("2.2.3.R202112201039", "2.2.3.R202112201039") == 0);
        assertTrue(c.compare("2.2.3", "2.2.3.R202112201039") < 0);
        assertTrue(c.compare("2.2.3.R202112201039", "2.2.3") > 0);
        assertTrue(c.compare("1.2.3", "2.2.3") < 0);
        assertTrue(c.compare("2.2.3", "1.2.3") > 0);
        assertTrue(c.compare("1.2.3", "1.2.3") == 0);
    }

}
