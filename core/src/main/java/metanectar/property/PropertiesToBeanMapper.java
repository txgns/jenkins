package metanectar.property;

import java.beans.*;
import java.beans.IntrospectionException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;

/**
 *
 * @author Paul Sandoz
 */
public class PropertiesToBeanMapper {
    private final Properties properties;

    public PropertiesToBeanMapper(Properties properties) {
        this.properties = properties;
    }

    public <T> T mapTo(Class<T> ct) throws PropertyMappingException {

        try {
            return mapTo(ct.newInstance());
        } catch (PropertyMappingException e) {
            throw e;
        } catch (Exception e) {
            throw new PropertyMappingException(e);
        }
    }

    public <T> T mapTo(final T t) throws PropertyMappingException {
        final BeanInfo bi = getBeanInfo(t.getClass());

        for (final PropertyDescriptor pd : bi.getPropertyDescriptors()) {
            final Method writeMethod = pd.getWriteMethod();

            if (writeMethod == null || !writeMethod.isAnnotationPresent(Property.class))
                continue;

            final Property p = writeMethod.getAnnotation(Property.class);
            final Class type = pd.getPropertyType();

            if (p.value().isEmpty()) {
                throw onError(writeMethod, "The declared @Property(\"\") MUST not be an empty string");
            }

            if (writeMethod.isAnnotationPresent(DefaultValue.class)) {
                final DefaultValue dv = writeMethod.getAnnotation(DefaultValue.class);

            }

            String value = getProperty(p.value());
            if (value == null) {
                final DefaultValue dv = writeMethod.getAnnotation(DefaultValue.class);
                if (dv != null) {
                    value = dv.value();
                } else {
                    // TODO optional
                    throw onError(writeMethod, String.format("The required @Property(\"%s\") is not present in the properties %s", p.value(), properties));
                }
            }

            try {
                // TODO optional
                writeMethod.invoke(t, StringConverter.valueOf(type, value));
            } catch (IllegalAccessException e) {
                throw onError(writeMethod, e.getMessage(), e);
            } catch (InvocationTargetException e) {
                throw onError(writeMethod, "Error setting property", e);
            } catch (StringConverter.StringConverterException e) {
                throw onError(writeMethod, "Error converting string to instance of property type", e);
            }
        }

        return t;
    }

    private String getProperty(String name) {
        String value = properties.getProperty(name);
        if (value != null)
            return value;

        return System.getProperty(name);
    }

    private PropertyMappingException onError(Class bean, String message, Throwable cause) {
        return new PropertyMappingException(String.format("Error processing property bean %s. %s",
                bean.getName(), message), cause);
    }

    private PropertyMappingException onError(Method writeMethod, String message) {
        return new PropertyMappingException(String.format("Error processing property bean method %s#%s. %s",
                writeMethod.getDeclaringClass().getName(),
                writeMethod.getName(),
                message));
    }

    private PropertyMappingException onError(Method writeMethod, String message, Throwable cause) {
        return new PropertyMappingException(String.format("Error processing property bean method %s#%s. %s",
                writeMethod.getDeclaringClass().getName(),
                writeMethod.getName(),
                message),
                cause);
    }

    private BeanInfo getBeanInfo(Class c) throws PropertyMappingException {
        try {
            return Introspector.getBeanInfo(c);
        } catch (IntrospectionException e) {
            throw onError(c, "Bean introspection error", e);
        }
    }

    public static class PropertyMappingException extends RuntimeException {
        public PropertyMappingException(String s) {
            super(s);
        }

        public PropertyMappingException(Throwable throwable) {
            super(throwable);
        }

        public PropertyMappingException(String s, Throwable throwable) {
            super(s, throwable);
        }
    }
}
