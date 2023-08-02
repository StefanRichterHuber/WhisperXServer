package com.github.StefanRichterHuber.WhisperXServer;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ProcessUtils {
    private ProcessUtils() {

    }

    /**
     * Async handles the stdout / error output of a process, reading it line-by-line
     * and calling the given handler on each new line;
     * 
     * @param s          InputStream / Errorstream from the process
     * @param onNextLine Handler for each line read
     */
    public static CompletableFuture<Void> handleProcessOutput(InputStream s, Consumer<String> onNextLine) {
        return CompletableFuture.runAsync(() -> {
            try (final InputStream err = new BufferedInputStream(s)) {
                final InputStreamReader r = new InputStreamReader(err, StandardCharsets.UTF_8);
                final BufferedReader reader = new BufferedReader(r);

                while (reader.ready()) {
                    final String line = reader.readLine();
                    onNextLine.accept(line);
                }
            } catch (IOException e) {

            }
        });

    }
}
