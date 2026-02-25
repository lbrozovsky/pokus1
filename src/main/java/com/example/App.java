package com.example;

public class App {
    public static void main(String[] args) {
        Greeter greeter = new Greeter();
        String name = args.length > 0 ? args[0] : "World";
        System.out.println(greeter.greet(name));
    }
}
