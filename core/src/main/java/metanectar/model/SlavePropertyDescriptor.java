package metanectar.model;

import hudson.tools.PropertyDescriptor;

public abstract class SlavePropertyDescriptor extends PropertyDescriptor<SlaveProperty<?>, Slave> {
    protected SlavePropertyDescriptor(Class<? extends SlaveProperty<?>> clazz) {
        super(clazz);
    }

    protected SlavePropertyDescriptor() {
    }
}
