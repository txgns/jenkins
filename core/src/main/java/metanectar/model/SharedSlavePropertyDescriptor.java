package metanectar.model;

import hudson.model.Hudson;
import hudson.model.JobProperty;
import hudson.tools.PropertyDescriptor;

import java.util.Collection;
import java.util.List;

/**
 * @author Stephen Connolly
 */
public abstract class SharedSlavePropertyDescriptor extends PropertyDescriptor<SharedSlaveProperty<?>, SharedSlave> {
    protected SharedSlavePropertyDescriptor(Class<? extends SharedSlaveProperty<?>> clazz) {
        super(clazz);
    }

    protected SharedSlavePropertyDescriptor() {
    }

    public static List<SharedSlavePropertyDescriptor> all() {
        return (List) Hudson.getInstance().getDescriptorList(SharedSlaveProperty.class);
    }

}
