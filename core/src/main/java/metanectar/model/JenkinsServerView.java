package metanectar.model;

import hudson.model.View;
import hudson.model.ViewGroup;

public abstract class JenkinsServerView extends View {
    protected JenkinsServerView(String name) {
        super(name);
    }

    protected JenkinsServerView(String name, ViewGroup owner) {
        super(name, owner);
    }
}
