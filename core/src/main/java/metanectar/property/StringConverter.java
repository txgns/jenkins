package metanectar.property;

import com.google.common.collect.Maps;

import java.net.URL;
import java.util.Map;

/**
 * @author Paul Sandoz
 */
public final class StringConverter {

    static final Map<Class, Converter> converterMap = createConvertorMap();

    private StringConverter() {
    }

    public static Object convert(Class type, String value) throws StringConversionException {
        final Converter sc = converterMap.get(type);
        if (sc == null) {
            throw new StringConversionException(String.format("The string \"%s\" could not be converted to an instance of the type %s. There is no registered converter for the type.", value, type.getName()));
        }

        Object oValue = null;
        try {
            return sc.fromString(value);
        } catch (Exception e) {
            throw new StringConversionException(String.format("The string \"%s\" could not be converted to an instance of the type %s", value, type.getName()), e);
        }
    }

    public static class StringConversionException extends Exception {
        public StringConversionException(String s) {
            super(s);
        }

        public StringConversionException(String s, Throwable throwable) {
            super(s, throwable);
        }
    }

    private static Map<Class, Converter> createConvertorMap() {
        final Map<Class, Converter> converterMap = Maps.newHashMap();

        converterMap.put(String.class, new Converter<String>() {
            public String fromString(String s) {
                return s;
            }
        });

        converterMap.put(int.class, new Converter<Integer>() {
            public Integer fromString(String s) {
                if (s == null) return 0;
                return Integer.valueOf(s);
            }
        });
        converterMap.put(Integer.class, converterMap.get(int.class));

        converterMap.put(URL.class, new Converter<URL>() {
            public URL fromString(String s) throws Exception{
                return new URL(s);
            }
        });

        return converterMap;
    }

    private interface Converter<T> {
        T fromString(String s) throws Exception;
    }

}
