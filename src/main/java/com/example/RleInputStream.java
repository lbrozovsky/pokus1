package com.example;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Streaming run-length decoder matching {@link RleOutputStream}.
 * Reads {@code (count, byte)} pairs and expands each pair into {@code count} copies of {@code byte}.
 */
class RleInputStream extends FilterInputStream {
    private int remaining   = 0;
    private int currentByte = -1;

    RleInputStream(InputStream in) { super(in); }

    @Override
    public int read() throws IOException {
        while (remaining == 0) {
            int cnt = in.read();
            if (cnt == -1) return -1;
            if (cnt == 0) throw new IOException("Invalid RLE count: 0");
            currentByte = in.read();
            if (currentByte == -1) throw new IOException("Unexpected EOF in RLE stream");
            remaining = cnt;
        }
        remaining--;
        return currentByte;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int read = 0;
        for (int i = 0; i < len; i++) {
            int c = read();
            if (c == -1) return read == 0 ? -1 : read;
            b[off + i] = (byte) c;
            read++;
        }
        return read;
    }
}
