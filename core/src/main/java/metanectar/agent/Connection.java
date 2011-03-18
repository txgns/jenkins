package metanectar.agent;

import com.trilead.ssh2.crypto.Base64;

import javax.crypto.KeyAgreement;
import javax.crypto.spec.DHParameterSpec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.AlgorithmParameterGenerator;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

/**
 * Represents a TCP/IP-like connection with the other side.
 *
 * <p>
 * The basis of this is an {@link InputStream}/{@link OutputStream} pair,
 * plus {@link AgentStatusListener} to report the events in this connection.
 *
 * <p>
 * Because the early hand-shaking phase of the protocol depends on
 * {@link DataInputStream}/{@link DataOutputStream}, that is provided
 * here for convenience.
 *
 * @author Kohsuke Kawaguchi, Paul Sandoz
 */
public class Connection {
    public final InputStream in;
    public final OutputStream out;

    public final DataInputStream din;
    public final DataOutputStream dout;

    private AgentStatusListener listener;

    public Connection(Socket socket) throws IOException {
        this(socket.getInputStream(),socket.getOutputStream());
    }

    public Connection(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
        this.din = new DataInputStream(in);
        this.dout = new DataOutputStream(out);
    }

    public AgentStatusListener getListener() {
        return listener;
    }

    public void setListener(AgentStatusListener listener) {
        this.listener = listener;
    }

//
//
// Convenience methods
//
//
    public void writeUTF(String msg) throws IOException {
        dout.writeUTF(msg);
    }

    public String readUTF() throws IOException {
        return din.readUTF();
    }

    /**
     * Sends a serializable object.
     */
    public void writeObject(Object o) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(out);
        oos.writeObject(o);
        // don't close oss, which will close the underlying stream
        // no need to flush either, given the way oos is implemented
    }

    /**
     * Receives an object sent by {@link #writeObject(Object)}
     */
    public <T> T readObject() throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ObjectInputStream(in);
        return (T)ois.readObject();
    }

    public void writeKey(Key key) throws IOException {
        writeUTF(new String(Base64.encode(key.getEncoded())));
    }

    public X509EncodedKeySpec readKey() throws IOException {
        byte[] otherHalf = Base64.decode(readUTF().toCharArray());
        return new X509EncodedKeySpec(otherHalf);
    }

    /**
     * Performs a Diffie-Hellman key exchange and produce a common secret between two ends of the connection.
     *
     * <p>
     * DH is also useful as a coin-toss algorithm. Two parties get the same random number without trusting
     * each other.
     */
    public KeyAgreement diffieHellman() throws IOException, GeneralSecurityException {
        AlgorithmParameterGenerator paramGen = AlgorithmParameterGenerator.getInstance("DH");
        paramGen.init(512);

        KeyPairGenerator dh = KeyPairGenerator.getInstance("DH");
        dh.initialize(paramGen.generateParameters().getParameterSpec(DHParameterSpec.class));
        KeyPair keyPair = dh.generateKeyPair();

        // send a half and get a half
        writeKey(keyPair.getPublic());
        PublicKey otherHalf = KeyFactory.getInstance("DH").generatePublic(readKey());

        KeyAgreement ka = KeyAgreement.getInstance("DH");
        ka.init(keyPair.getPrivate());
        ka.doPhase(otherHalf, true);

        return ka;
    }
}
