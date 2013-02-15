package jenkins.security;

import hudson.ExtensionPoint;

import org.kohsuke.stapler.StaplerRequest;

/**
 * An extension point for authorizing access to the given object from
 * a jsonp request.
 * 
 * @author recampbell
 * @since 1.502
 */
public interface SecureRequester extends ExtensionPoint {
    /**
     * Return true if the bean can be accessed by the given request.
     * For instance, if the Referer matches a given host, or
     * anonymous read is allowed for the given object.
     * 
     * @param req
     * @param bean
     * @return
     */
    boolean permit(StaplerRequest req, Object bean);
}