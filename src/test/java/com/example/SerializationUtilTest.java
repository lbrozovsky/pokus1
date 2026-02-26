package com.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.NotSerializableException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SerializationUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void serializeAndDeserializeString() throws Exception {
        Path file = tempDir.resolve("test.ser");
        SerializationUtil.serialize("hello world", file);
        String result = SerializationUtil.deserialize(file);
        assertEquals("hello world", result);
    }

    @Test
    void serializeAndDeserializeWithCompression() throws Exception {
        Path file = tempDir.resolve("test.ser.gz");
        SerializationUtil.serialize("hello world", file, true);
        String result = SerializationUtil.deserialize(file);
        assertEquals("hello world", result);
    }

    @Test
    void autoDetectsBothFormatsFromSameDeserializeMethod() throws Exception {
        Path plain = tempDir.resolve("plain.ser");
        Path compressed = tempDir.resolve("compressed.ser.gz");

        SerializationUtil.serialize("plain", plain, false);
        SerializationUtil.serialize("compressed", compressed, true);

        assertEquals("plain", SerializationUtil.deserialize(plain));
        assertEquals("compressed", SerializationUtil.deserialize(compressed));
    }

    @Test
    void compressedFileIsSmallerForLargeData() throws Exception {
        // byte array of zeros is highly compressible and avoids Java serialization deduplication
        byte[] data = new byte[100_000];
        Path plain = tempDir.resolve("plain.ser");
        Path compressed = tempDir.resolve("compressed.ser.gz");

        SerializationUtil.serialize(data, plain);
        SerializationUtil.serialize(data, compressed, true);

        assertTrue(compressed.toFile().length() < plain.toFile().length());
    }

    @Test
    void serializeAndDeserializeComplexObject() throws Exception {
        Path file = tempDir.resolve("list.ser");
        List<Integer> original = List.of(1, 2, 3, 4, 5);
        SerializationUtil.serialize(original, file);
        List<Integer> result = SerializationUtil.deserialize(file);
        assertEquals(original, result);
    }

    @Test
    void deserializeEmptyFileThrows() throws Exception {
        Path file = tempDir.resolve("empty.ser");
        Files.createFile(file);
        assertThrows(IOException.class, () -> SerializationUtil.deserialize(file));
    }

    @Test
    void deserializeUnknownMagicByteThrows() throws Exception {
        Path file = tempDir.resolve("unknown.ser");
        Files.write(file, new byte[]{0x42});
        assertThrows(IOException.class, () -> SerializationUtil.deserialize(file));
    }

    @Test
    void serializeNonSerializableObjectThrows() {
        Path file = tempDir.resolve("bad.ser");
        Object notSerializable = new Object() {};
        assertThrows(NotSerializableException.class,
                () -> SerializationUtil.serialize(notSerializable, file));
    }

    @Test
    void deserializeNonExistentFileThrows() {
        Path file = tempDir.resolve("missing.ser");
        assertThrows(IOException.class, () -> SerializationUtil.deserialize(file));
    }

    @Test
    void wrongTypeThrowsClassCastException() throws Exception {
        Path file = tempDir.resolve("int.ser");
        SerializationUtil.serialize(42, file);
        assertThrows(ClassCastException.class, () -> {
            String result = SerializationUtil.deserialize(file);
            result.length(); // trigger cast
        });
    }

    @Test
    void serializeAndDeserializeWithExplicitGzipCodec() throws Exception {
        Path file = tempDir.resolve("test.ser.gz");
        SerializationUtil.serialize("hello world", file, SerializationUtil.Compression.GZIP);
        String result = SerializationUtil.deserialize(file);
        assertEquals("hello world", result);
    }

    @Test
    void serializeAndDeserializeWithExplicitXzCodec() throws Exception {
        Path file = tempDir.resolve("test.ser.xz");
        SerializationUtil.serialize("hello world", file, SerializationUtil.Compression.XZ);
        String result = SerializationUtil.deserialize(file);
        assertEquals("hello world", result);
    }

    @Test
    void allCodecsRoundTripComplexObject() throws Exception {
        java.util.List<Integer> original = java.util.List.of(1, 2, 3, 4, 5);
        for (SerializationUtil.Compression codec : SerializationUtil.Compression.values()) {
            Path file = tempDir.resolve("list-" + codec + ".ser");
            SerializationUtil.serialize(original, file, codec);
            java.util.List<Integer> result = SerializationUtil.deserialize(file);
            assertEquals(original, result, "Round-trip failed for codec: " + codec);
        }
    }

    @Test
    void serializeAndDeserializeWithExplicitBzip2Codec() throws Exception {
        Path file = tempDir.resolve("test.ser.bz2");
        SerializationUtil.serialize("hello world", file, SerializationUtil.Compression.BZIP2);
        String result = SerializationUtil.deserialize(file);
        assertEquals("hello world", result);
    }

    @Test
    void bzip2CompressedFileIsSmallerForLargeData() throws Exception {
        byte[] data = new byte[100_000];
        Path plain = tempDir.resolve("plain.ser");
        Path bzip2 = tempDir.resolve("compressed.ser.bz2");

        SerializationUtil.serialize(data, plain);
        SerializationUtil.serialize(data, bzip2, SerializationUtil.Compression.BZIP2);

        assertTrue(Files.size(bzip2) < Files.size(plain));
    }

    @Test
    void autoCompressionWritesCompressedMagicByte() throws Exception {
        byte[] data = new byte[100_000];
        Path file = tempDir.resolve("auto.ser");
        SerializationUtil.serialize(data, file, true);

        byte magic = Files.readAllBytes(file)[0];
        assertTrue(magic == 0x01 || magic == 0x02 || magic == 0x03,
                "Expected GZIP (0x01), XZ (0x02), or BZip2 (0x03) magic byte, got: 0x"
                        + Integer.toHexString(magic & 0xFF));
    }

    @Test
    void autoCompressionPicksSmallestCodec() throws Exception {
        // 100K zeros are highly compressible — auto should match the smallest individual codec
        byte[] data = new byte[100_000];
        Path auto  = tempDir.resolve("auto.ser");
        Path gzip  = tempDir.resolve("gzip.ser");
        Path xz    = tempDir.resolve("xz.ser");
        Path bzip2 = tempDir.resolve("bzip2.ser");

        SerializationUtil.serialize(data, auto,  true);
        SerializationUtil.serialize(data, gzip,  SerializationUtil.Compression.GZIP);
        SerializationUtil.serialize(data, xz,    SerializationUtil.Compression.XZ);
        SerializationUtil.serialize(data, bzip2, SerializationUtil.Compression.BZIP2);

        long autoSize = Files.size(auto);
        long minSize  = Math.min(Files.size(gzip), Math.min(Files.size(xz), Files.size(bzip2)));
        assertEquals(minSize, autoSize, "Auto-selected file should match the smallest individual codec");

        // Must also deserialize correctly
        assertArrayEquals(data, (byte[]) SerializationUtil.deserialize(auto));
    }

    @Test
    void xzCompressedFileIsSmallerForLargeData() throws Exception {
        byte[] data = new byte[100_000];
        Path plain = tempDir.resolve("plain.ser");
        Path xz    = tempDir.resolve("compressed.ser.xz");

        SerializationUtil.serialize(data, plain);
        SerializationUtil.serialize(data, xz, SerializationUtil.Compression.XZ);

        assertTrue(Files.size(xz) < Files.size(plain));
    }
}
