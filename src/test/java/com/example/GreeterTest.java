package com.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GreeterTest {

    private final Greeter greeter = new Greeter();

    @Test
    void greetWithName() {
        assertEquals("Hello, Alice!", greeter.greet("Alice"));
    }

    @Test
    void greetWithWorld() {
        assertEquals("Hello, World!", greeter.greet("World"));
    }

    @Test
    void greetWithBlankNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> greeter.greet(""));
    }

    @Test
    void greetWithNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> greeter.greet(null));
    }

    @Test
    void farewellWithName() {
        assertEquals("Goodbye, Alice!", greeter.farewell("Alice"));
    }

    @Test
    void farewellWithBlankNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> greeter.farewell(""));
    }

    @Test
    void farewellWithNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> greeter.farewell(null));
    }
}
