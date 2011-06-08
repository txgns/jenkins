package metanectar.model;

import hudson.tools.PropertyDescriptor;

/**
 * @author Stephen Connolly
 */
public abstract class SharedSlavePropertyDescriptor extends PropertyDescriptor<SharedSlaveProperty<?>, SharedSlave> {
    protected SharedSlavePropertyDescriptor(Class<? extends SharedSlaveProperty<?>> clazz) {
        super(clazz);
    }

    protected SharedSlavePropertyDescriptor() {
    }
}
