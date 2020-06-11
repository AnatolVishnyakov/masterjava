package ru.javaops.masterjava;

public class LazySingleton {
    private LazySingleton() {
    }

    private static class LazyHolder {
        private static final LazySingleton INSTANCE = new LazySingleton();
    }

    public static LazySingleton getInstance() {
        return LazyHolder.INSTANCE;
    }
}
