package metanectar.provisioning;

import antlr.ANTLRException;
import hudson.model.Label;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.RemoteFuture;
import sun.reflect.generics.visitor.Visitor;

import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
class MetaNectarSlaveManagerProxy implements MetaNectarSlaveManager {
    private final Channel channel;
    private final Handle proxy;

    MetaNectarSlaveManagerProxy(Channel channel, Handle proxy) {
        this.channel = channel;
        this.proxy = proxy;
    }

    public boolean canProviosion(Label label, final int numOfExecutors) throws IOException, InterruptedException {
        final String labelExpr = label.toString();
        return channel.call(new Callable<Boolean,RuntimeException>() {
            public Boolean call() {
                try {
                    return ((MetaNectarSlaveManager)proxy).canProviosion(Label.parseExpression(labelExpr), numOfExecutors);
                } catch (ANTLRException e) {
                    throw new Error(e); // can't happen because we know it's a valid label string
                }
            }
        });
    }

    public ProvisioningInProgress provision(Label label, final TaskListener listener, final int numOfExecutors) throws IOException, InterruptedException {
        final String labelExpr = label.toString();

        return proxy.accept(new Visitor<MetaNectarSlaveManager, ProvisioningInProgress, RuntimeException>() {
            public ProvisioningInProgress actOn(MetaNectarSlaveManager o) throws IOException, InterruptedException {
                try {
                    final ProvisioningInProgress r = o.provision(Label.parseExpression(labelExpr), listener, numOfExecutors);
                    return new ProvisioningInProgress(r.displayName, r.numOfExecutors, new RemoteFuture<ProvisioningResult>(r.future));
                } catch (ANTLRException e) {
                    throw new Error(e); // can't happen because we know it's a valid label string
                }
            }
        });
    }
}
