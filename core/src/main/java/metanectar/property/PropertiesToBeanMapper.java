package metanectar.property;

import com.google.common.collect.Maps;

import java.beans.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Map;
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

    public <T> T mapTo(Class<T> ct) throws IllegalAccessException, InstantiationException, IntrospectionException {
        return mapTo(ct.newInstance());
    }

    public <T> T mapTo(final T t) throws IntrospectionException {
        final BeanInfo bi = Introspector.getBeanInfo(t.getClass());

        for (final PropertyDescriptor pd : bi.getPropertyDescriptors()) {
            final Method writeMethod = pd.getWriteMethod();

            if (!writeMethod.isAnnotationPresent(Property.class))
                continue;

            final Property p = writeMethod.getAnnotation(Property.class);
            final Class type = pd.getPropertyType();

            if (p.value() == null || p.value().isEmpty()) {
                // TODO error
            }

            String value = properties.getProperty(p.value());
            if (value == null) {
                // TODO required or default value
            }

            try {
                writeMethod.invoke(t, StringConverter.convert(type, value));
            } catch (IllegalAccessException e) {
                // TODO
            } catch (InvocationTargetException e) {
                // TODO
            } catch (StringConverter.StringConversionException e) {
                // TODO
            }
        }

        return t;
    }
}
