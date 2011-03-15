package metanectar.provisioning;

import hudson.remoting.Channel;
import org.jaxen.expr.Visitable;

/**
 * @author Kohsuke Kawaguchi
 */
public class MetaNectarSlaveManagerImpl implements MetaNectarSlaveManager {

    private Object writeReplace() {
        final Channel ch = Channel.current();
        if (ch!=null)
            return new MetaNectarSlaveManagerProxy(ch,ch.export(Handle.class, this));
        else
            return this;
    }

}
