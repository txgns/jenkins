package metanectar.provisioning;

import java.io.IOException;
import java.io.Writer;

/**
 * @author Kohsuke Kawaguchi
 */
public class TeeWriter extends Writer {
    private final Writer lhs,rhs;

    public TeeWriter(Writer lhs, Writer rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public void write(int c) throws IOException {
        lhs.write(c);
        rhs.write(c);
    }

    @Override
    public void write(char[] cbuf) throws IOException {
        lhs.write(cbuf);
        rhs.write(cbuf);
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        lhs.write(cbuf, off, len);
        rhs.write(cbuf, off, len);
    }

    @Override
    public void write(String str) throws IOException {
        lhs.write(str);
        rhs.write(str);
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
        lhs.write(str, off, len);
        rhs.write(str, off, len);
    }

    @Override
    public void flush() throws IOException {
        lhs.flush();
        rhs.flush();
    }

    @Override
    public void close() throws IOException {
        lhs.close();
        rhs.close();
    }
}
