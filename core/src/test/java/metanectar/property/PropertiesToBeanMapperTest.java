package metanectar.property;

import junit.framework.TestCase;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Properties;

/**
 * @author Paul Sandoz
 */
public class PropertiesToBeanMapperTest extends TestCase {

    public static class BeanWithAllTypes {
        private String string;
        private boolean booleanPrimitive;
        private Boolean booleanType;
        private int i;
        private Integer integer;
        private URL url;

        @Property("string")
        public void setString(String string) {
            this.string = string;
        }

        @Property("booleanPrimitive")
        public void setBooleanPrimitive(boolean booleanPrimitive) {
            this.booleanPrimitive = booleanPrimitive;
        }

        @Property("booleanType")
        public void setBooleanType(Boolean booleanType) {
            this.booleanType = booleanType;
        }

        @Property("int")
        public void setI(int i) {
            this.i = i;
        }

        @Property("integer")
        public void setInteger(Integer integer) {
            this.integer = integer;
        }

        @Property("url")
        public void setUrl(URL url) {
            this.url = url;
        }
    }

    public void testBeanWithAllTypes() throws Exception {
        Properties p = new Properties();
        p.setProperty("string", "foo");
        p.setProperty("booleanPrimitive", "true");
        p.setProperty("booleanType", "true");
        p.setProperty("int", "1");
        p.setProperty("integer", "1");
        p.setProperty("url", "http://localhost");

        PropertiesToBeanMapper m = new PropertiesToBeanMapper(p);

        BeanWithAllTypes b = m.mapTo(BeanWithAllTypes.class);

        assertNotNull(b.string);
        assertEquals("foo", b.string);

        assertTrue(b.booleanPrimitive);

        assertNotNull(b.booleanType);
        assertEquals(new Boolean(true), b.booleanType);

        assertEquals(1, b.i);

        assertNotNull(b.integer);
        assertEquals(new Integer(1), b.integer);

        assertNotNull(b.url);
        assertEquals(new URL("http://localhost"), b.url);
    }

    public void testRequired() {
        _testWithException(BeanWithAllTypes.class);
    }

    public void testSystemProperties() throws Exception {
        try {
            Properties p = System.getProperties();
            p.setProperty("string", "foo");
            p.setProperty("booleanPrimitive", "true");
            p.setProperty("booleanType", "true");
            p.setProperty("int", "1");
            p.setProperty("integer", "1");
            p.setProperty("url", "http://localhost");

            PropertiesToBeanMapper m = new PropertiesToBeanMapper(new Properties());

            BeanWithAllTypes b = m.mapTo(BeanWithAllTypes.class);

            assertNotNull(b.string);
            assertEquals("foo", b.string);

            assertTrue(b.booleanPrimitive);

            assertNotNull(b.booleanType);
            assertEquals(new Boolean(true), b.booleanType);

            assertEquals(1, b.i);

            assertNotNull(b.integer);
            assertEquals(new Integer(1), b.integer);

            assertNotNull(b.url);
            assertEquals(new URL("http://localhost"), b.url);
        } finally {
            System.getProperties().remove("string");
            System.getProperties().remove("booleanPrimitive");
            System.getProperties().remove("booleanType");
            System.getProperties().remove("int");
            System.getProperties().remove("integer");
            System.getProperties().remove("url");

        }
    }

    public void testSystemPropertiesPrecedence() throws Exception {
        try {
            Properties p = System.getProperties();
            p.setProperty("string", "foo");
            p.setProperty("booleanPrimitive", "true");
            p.setProperty("booleanType", "true");
            p.setProperty("int", "1");
            p.setProperty("integer", "1");
            p.setProperty("url", "http://localhost");

            Properties _p = new Properties();
            p.setProperty("string", "bar");

            PropertiesToBeanMapper m = new PropertiesToBeanMapper(_p);

            BeanWithAllTypes b = m.mapTo(BeanWithAllTypes.class);

            assertNotNull(b.string);
            assertEquals("bar", b.string);
        } finally {
            System.getProperties().remove("string");
            System.getProperties().remove("booleanPrimitive");
            System.getProperties().remove("booleanType");
            System.getProperties().remove("int");
            System.getProperties().remove("integer");
            System.getProperties().remove("url");
        }
    }

    //

    public static class BeanWithDefaultValue {
        private String string;
        private boolean booleanPrimitive;
        private Boolean booleanType;
        private int i;
        private Integer integer;
        private URL url;

        @Property("string") @DefaultValue("foo")
        public void setString(String string) {
            this.string = string;
        }

        @Property("booleanPrimitive") @DefaultValue("true")
        public void setBooleanPrimitive(boolean booleanPrimitive) {
            this.booleanPrimitive = booleanPrimitive;
        }

        @Property("booleanType") @DefaultValue("true")
        public void setBooleanType(Boolean booleanType) {
            this.booleanType = booleanType;
        }

        @Property("int") @DefaultValue("1")
        public void setI(int i) {
            this.i = i;
        }

        @Property("integer") @DefaultValue("1")
        public void setInteger(Integer integer) {
            this.integer = integer;
        }

        @Property("url") @DefaultValue("http://localhost")
        public void setUrl(URL url) {
            this.url = url;
        }

    }

    public void testDefaultValue() throws Exception {
        Properties p = new Properties();

        PropertiesToBeanMapper m = new PropertiesToBeanMapper(p);

        BeanWithDefaultValue b = m.mapTo(BeanWithDefaultValue.class);

        assertNotNull(b.string);
        assertEquals("foo", b.string);

        assertTrue(b.booleanPrimitive);

        assertNotNull(b.booleanType);
        assertEquals(new Boolean(true), b.booleanType);

        assertEquals(1, b.i);

        assertNotNull(b.integer);
        assertEquals(new Integer(1), b.integer);

        assertNotNull(b.url);
        assertEquals(new URL("http://localhost"), b.url);
    }

    //

    public static class BeanWithEmptyProperty {
        private String string;

        @Property("")
        public void setString(String string) {
            this.string = string;
        }

    }

    public void testEmptyProperty() {
        _testWithException(BeanWithEmptyProperty.class);
    }

    //

    public static class BeanWithInvocationError {
        private String string;

        @Property("string")
        public void setString(String string) {
            throw new RuntimeException(string);
        }

    }

    public void testInvocationError() {
        Properties p = new Properties();
        p.setProperty("string", "foo");

        Exception e = _testWithException(BeanWithInvocationError.class, p);
        Throwable invocationTargetException = e.getCause();
        assertEquals(InvocationTargetException.class, invocationTargetException.getClass());

        Throwable runtimeException = invocationTargetException.getCause();
        assertEquals(RuntimeException.class, runtimeException.getClass());
        assertEquals("foo", runtimeException.getMessage());
    }

    //

    public static class BeanWithConversionError {
        private int i;

        @Property("int")
        public void setI(int i) {
            this.i = i;
        }

    }

    public void testConversionError() {
        Properties p = new Properties();
        p.setProperty("int", "foo");

        Exception e = _testWithException(BeanWithConversionError.class, p);
        assertEquals(StringConverter.StringConverterException.class, e.getCause().getClass());
    }


    //

    private <T> void _testWithException(Class<T> bean) {
        _testWithException(bean, new Properties());
    }

    private <T> Exception _testWithException(Class<T> bean, Properties p) {
        PropertiesToBeanMapper m = new PropertiesToBeanMapper(p);

        Exception caught = null;
        try {
            T b = m.mapTo(bean);
        } catch (Exception e) {
            caught = e;
            e.printStackTrace();
        }

        assertNotNull(caught);
        assertEquals(PropertiesToBeanMapper.PropertyMappingException.class, caught.getClass());

        return caught;
    }

}
