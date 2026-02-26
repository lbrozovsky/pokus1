package com.example;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Streaming run-length encoder: each consecutive run of identical bytes is stored
 * as a pair {@code (count, byte)} where {@code count} is in the range 1–255.
 *
 * <p>Note: {@link #flush()} emits the current buffered run (if any) to the underlying
 * stream before flushing. This makes accumulated data immediately available downstream
 * but may split a run that would otherwise have been encoded as a single pair.
 */
class RleOutputStream extends FilterOutputStream {
    private int lastByte = -1;
    private int count    = 0;

    RleOutputStream(OutputStream out) { super(out); }

    @Override
    public void write(int b) throws IOException {
        b &= 0xFF;
        if (b == lastByte && count < 255) {
            count++;
        } else {
            if (count > 0) flushRun();
            lastByte = b;
            count    = 1;
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || len > b.length - off) throw new IndexOutOfBoundsException();
        for (int i = 0; i < len; i++) write(b[off + i] & 0xFF);
    }

    @Override
    public void flush() throws IOException {
        if (count > 0) flushRun();
        super.flush();
    }

    @Override
    public void close() throws IOException {
        if (count > 0) flushRun();
        super.close();
    }

    private void flushRun() throws IOException {
        out.write(count);
        out.write(lastByte);
        count = 0;
    }
}
