package org.example.gui.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

/**
 * Executes Python commands on a dedicated background thread and reports the outcome to the caller.
 */
public class PythonProcessService {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(new DaemonThreadFactory());

    /**
     * Runs the provided command asynchronously.
     *
     * @param command  the command and its arguments (e.g. {@code List.of("python3", "script.py")})
     * @param callback callback invoked with the command result on the executor thread
     */
    public void executeAsync(List<String> command, Consumer<PythonResult> callback) {
        executor.submit(() -> {
            PythonResult result = run(command);
            if (callback != null) {
                callback.accept(result);
            }
        });
    }

    private PythonResult run(List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);

        try {
            Process process = builder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            }

            int exitCode = process.waitFor();
            return new PythonResult(exitCode, output.toString().trim());
        } catch (Exception e) {
            return new PythonResult(-1, e.getMessage());
        }
    }

    /**
     * Stops all running processes.
     */
    public void shutdown() {
        executor.shutdownNow();
    }

    private static class DaemonThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "PythonProcessThread");
            thread.setDaemon(true);
            return thread;
        }
    }

    public record PythonResult(int exitCode, String output) {
    }
}