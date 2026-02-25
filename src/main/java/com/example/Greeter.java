package com.example;

public class Greeter {
    public String greet(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name must not be blank");
        }
        return "Hello, " + name + "!";
    }

    public String farewell(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name must not be blank");
        }
        return "Goodbye, " + name + "!";
    }
}
