package xyz.zcraft.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class AsyncHelper {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> s) {
        return CompletableFuture.supplyAsync(s, EXECUTOR);
    }
}
