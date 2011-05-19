package metanectar.property;

import com.google.common.collect.Maps;
import com.google.common.primitives.Primitives;

import java.net.URL;
import java.util.Map;

/**
 * Convert a string to an instance of a type.
 *
 * @author Paul Sandoz
 */
public final class StringConverter {

    static final Map<Class, StringToType> stringToTypeMap = createStringToTypeMap();

    private StringConverter() {
    }

    public static <T> T valueOf(Class<T> type, String value) throws StringConverterException {
        type = Primitives.wrap(type);
        final StringToType sc = stringToTypeMap.get(type);
        if (sc == null) {
            throw new StringConverterException(String.format("The string \"%s\" could not be converted to an instance of the type %s. There is no registered converter for the type.", value, type.getName()));
        }

        try {
            return type.cast(sc.valueOf(value));
        } catch (Exception e) {
            throw new StringConverterException(String.format("The string \"%s\" could not be converted to an instance of the type %s", value, type.getName()), e);
        }
    }

    public static class StringConverterException extends RuntimeException {
        public StringConverterException(String s) {
            super(s);
        }

        public StringConverterException(String s, Throwable throwable) {
            super(s, throwable);
        }
    }

    private static Map<Class, StringToType> createStringToTypeMap() {
        final Map<Class, StringToType> stringToTypeMap = Maps.newHashMap();

        stringToTypeMap.put(String.class, new StringToType<String>() {
            public String valueOf(String s) {
                return s;
            }
        });

        stringToTypeMap.put(Boolean.class, new StringToType<Boolean>() {
            public Boolean valueOf(String s) {
                return Boolean.valueOf(s);
            }
        });

        stringToTypeMap.put(Integer.class, new StringToType<Integer>() {
            public Integer valueOf(String s) {
                return Integer.valueOf(s);
            }
        });

        stringToTypeMap.put(URL.class, new StringToType<URL>() {
            public URL valueOf(String s) throws Exception {
                return new URL(s);
            }
        });

        return stringToTypeMap;
    }

    private interface StringToType<T> {
        T valueOf(String s) throws Exception;
    }

}
