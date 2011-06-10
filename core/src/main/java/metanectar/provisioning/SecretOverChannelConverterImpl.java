package metanectar.provisioning;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.model.Hudson;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.util.Secret;

/**
 * Convert {@link Secret} for XStream serialization
 */
public final class SecretOverChannelConverterImpl implements Converter {

    private static final class ResourceHolder {
        private static final SecretOverChannelConverterImpl INSTANCE = new SecretOverChannelConverterImpl();
    }

    private final Secret.ConverterImpl delegate = new Secret.ConverterImpl();

    private SecretOverChannelConverterImpl() {
    }

    public boolean canConvert(Class type) {
        return type == Secret.class;
    }

    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        if (Channel.current() != null) {
            Secret src = (Secret) source;
            writer.setValue(src.getPlainText());
        } else {
            delegate.marshal(source, writer, context);
        }
    }

    public Object unmarshal(HierarchicalStreamReader reader, final UnmarshallingContext context) {
        if (Channel.current() != null) {
            return Secret.fromString(reader.getValue());
        } else {
            return delegate.unmarshal(reader, context);
        }
    }

    public static <X extends XStream> X register(X xstream) {
        xstream.registerConverter(ResourceHolder.INSTANCE, Integer.MAX_VALUE);
        return xstream;
    }
}
