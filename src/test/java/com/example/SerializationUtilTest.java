package com.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.NotSerializableException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
        String result = SerializationUtil.deserialize(file, true);
        assertEquals("hello world", result);
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
    void deserializeWrongCompressionFlagThrows() throws Exception {
        Path file = tempDir.resolve("plain.ser");
        SerializationUtil.serialize("data", file, false);
        assertThrows(IOException.class, () -> SerializationUtil.deserialize(file, true));
    }

    @Test
    void serializeNonSerializableObjectThrows() {
        Path file = tempDir.resolve("bad.ser");
        Object notSerializable = new Object() {};
        assertThrows(NotSerializableException.class,
                () -> SerializationUtil.serialize(notSerializable, file));
    }
}
