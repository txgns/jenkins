package metanectar.provisioning;

import hudson.util.XStream2;

/**
 * A resource holder class for an XStream2 instance that will serialize {@link hudson.util.Secret} instances
 * over a channel in clear-text, re-encrypting them on receipt.
 */
public final class NotSecretXStream {
    public static final XStream2 INSTANCE = SecretOverChannelConverterImpl.register(new XStream2());
}
