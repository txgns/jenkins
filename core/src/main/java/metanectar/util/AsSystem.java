package metanectar.util;

import hudson.security.ACL;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;

import java.util.concurrent.Callable;

/**
 * Utility class that allows execution of callables in the context of {@link hudson.security.ACL#SYSTEM}
 */
public final class AsSystem {
    private AsSystem() {
        throw new IllegalAccessError("Utility class");
    }

    public static void run(final Runnable runnable) {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
            runnable.run();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }

    public static <T> T eval(Callable<? extends T> callable) throws Exception {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
            return callable.call();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }

    public static <V, T extends java.lang.Throwable> V eval(hudson.remoting.Callable<V,T> callable) throws T {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
            return callable.call();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }
}
