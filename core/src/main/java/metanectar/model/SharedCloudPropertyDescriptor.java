package metanectar.model;

import hudson.model.Hudson;
import hudson.tools.PropertyDescriptor;

import java.util.List;

/**
 * @author Stephen Connolly
 */
public abstract class SharedCloudPropertyDescriptor extends PropertyDescriptor<SharedCloudProperty<?>, SharedCloud> {
    protected SharedCloudPropertyDescriptor(Class<? extends SharedCloudProperty<?>> clazz) {
        super(clazz);
    }

    protected SharedCloudPropertyDescriptor() {
    }

    public static List<SharedCloudPropertyDescriptor> all() {
        return (List) Hudson.getInstance().getDescriptorList(SharedCloudProperty.class);
    }

}
