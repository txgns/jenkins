package metanectar.model;

import hudson.tools.PropertyDescriptor;

public abstract class MasterTemplatePropertyDescriptor extends PropertyDescriptor<MasterTemplateProperty, MasterTemplate> {
    protected MasterTemplatePropertyDescriptor(Class<? extends MasterTemplateProperty> clazz) {
        super(clazz);
    }

    protected MasterTemplatePropertyDescriptor() {
    }
}
