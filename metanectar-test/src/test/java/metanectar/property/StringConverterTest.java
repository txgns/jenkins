package metanectar.property;

import junit.framework.TestCase;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author Paul Sandoz
 */
public class StringConverterTest extends TestCase {
    public void testString() throws StringConverter.StringConverterException {
        assertEquals("foo", StringConverter.valueOf(String.class, "foo"));
    }

    public void testBooleanPrimitive() throws StringConverter.StringConverterException {
        boolean b = StringConverter.valueOf(boolean.class, "true");

        assertTrue(b);
    }

    public void testBoolean() throws StringConverter.StringConverterException {
        boolean b = StringConverter.valueOf(Boolean.class, "true");

        assertTrue(b);
    }

    public void testInt() throws StringConverter.StringConverterException {
        int i = StringConverter.valueOf(int.class, "1");

        assertEquals(1, i);
    }

    public void testInteger() throws StringConverter.StringConverterException {
        Integer i = StringConverter.valueOf(Integer.class, "1");

        assertEquals(new Integer(1), i);
    }

    public void testURL() throws StringConverter.StringConverterException, MalformedURLException {
        assertEquals(new URL("http://localhost:8080/"), StringConverter.valueOf(URL.class, "http://localhost:8080/"));
    }
}
