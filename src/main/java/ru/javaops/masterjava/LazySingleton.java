package ru.javaops.masterjava;

public class LazySingleton {
    private static volatile LazySingleton instance;

    private LazySingleton() {
    }

    private static class LazyHolder {
        private static final LazySingleton INSTANCE = new LazySingleton();
    }

    public static LazySingleton getInstance() {
//        if (instance == null) {
//            synchronized (LazySingleton.class) {
//                if (instance == null) {
//                    instance = new LazySingleton();
//                }
//            }
//        }
//        return instance;
        return LazyHolder.INSTANCE;
    }
}
