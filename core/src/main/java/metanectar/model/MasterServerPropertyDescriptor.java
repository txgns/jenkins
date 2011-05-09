package metanectar.model;

import hudson.slaves.NodeProperty;
import hudson.tools.PropertyDescriptor;

public abstract class MasterServerPropertyDescriptor extends PropertyDescriptor<MasterServerProperty<?>, MasterServer> {
    protected MasterServerPropertyDescriptor(Class<? extends MasterServerProperty<?>> clazz) {
        super(clazz);
    }

    protected MasterServerPropertyDescriptor() {
    }
}
