package metanectar.model;

import hudson.tools.PropertyDescriptor;

public abstract class ConnectedMasterPropertyDescriptor extends PropertyDescriptor<ConnectedMasterProperty, ConnectedMaster> {
    protected ConnectedMasterPropertyDescriptor(Class<? extends ConnectedMasterProperty> clazz) {
        super(clazz);
    }

    protected ConnectedMasterPropertyDescriptor() {
    }
}
