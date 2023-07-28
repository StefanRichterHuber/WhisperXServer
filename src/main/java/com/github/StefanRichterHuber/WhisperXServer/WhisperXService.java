package com.github.StefanRichterHuber.WhisperXServer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class WhisperXService {

    @Inject
    Logger logger;

    private String workdir = "/tmp";
    private String executable = "whisperx";
    private String computeType = "int8";
    /**
     * Number of parallel whisperX instances to run
     */
    private int numOfInstances = 1;

    private static final String TASK_TRANSCRIBE = "transcribe";
    private static final String TASK_TRANSLATE = "translate";

    private Executor executor;

    @PostConstruct
    private void createExecutor() {
        /**
         * Since whisperX uses a lot of compute power and memory, we limit the amount of
         * threads used (== 1 thread per parallel execution)
         */
        this.executor = new ThreadPoolExecutor(1, numOfInstances, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>());
    }

    /**
     * Transcribes the given audio content
     * 
     * @param content      Audio file in wav format, with 16.000 kHz and mono
     * @param language     language spoken in the audio, specify null to perform
     *                     language detection
     * @param outputFormat One of srt,vtt,txt,tsv,json,aud
     * @return String containing the result file
     */

    public CompletableFuture<String> transcribe(byte[] content, String language, String outputFormat) {
        return invokeWisperX(content, language, outputFormat, TASK_TRANSCRIBE);
    }

    /**
     * Translates the given audio content
     * 
     * @param content      Audio file in wav format, with 16.000 kHz and mono
     * @param language     language spoken in the audio, specify null to perform
     *                     language detection
     * @param outputFormat One of srt,vtt,txt,tsv,json,aud
     * @return String containing the result file
     */

    public CompletableFuture<String> translate(byte[] content, String language, String outputFormat) {
        return invokeWisperX(content, language, outputFormat, TASK_TRANSLATE);
    }

    /**
     * Invokes whisperX
     * 
     * @param content      Audio file in wav format, with 16.000 kHz and mono
     * @param language     language spoken in the audio, specify null to perform
     *                     language detection
     * @param outputFormat One of srt,vtt,txt,tsv,json,aud
     * @param task         One of transcribe,translate
     * @return String containing the result file
     */

    private CompletableFuture<String> invokeWisperX(byte[] content, String language, String outputFormat, String task) {

        final String filePrefix = UUID.randomUUID().toString();
        final String sourceFile = workdir + "/" + filePrefix + ".wav";
        final String resultFile = workdir + "/" + filePrefix + "." + outputFormat;

        return CompletableFuture.supplyAsync(() -> {
            logger.infof(
                    "Invoked whisperX service with task %s in language %s for input %s and output %s in the format %s",
                    task, language, sourceFile, resultFile, outputFormat);
            // Write temporary file
            try (OutputStream os = new BufferedOutputStream(new FileOutputStream(sourceFile))) {
                os.write(content);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write source file " + sourceFile, e);
            }

            // Invoke whisperX
            try {
                // Prepare default parameters
                final List<String> parameters = new ArrayList<>();
                parameters.addAll(Arrays.asList(executable, sourceFile, //
                        "--compute_type", computeType, //
                        "--output_dir", workdir, //
                        "--task", task, //
                        "--output_format", outputFormat));
                // Add optional parameters
                if (language != null && !language.isBlank()) {
                    parameters.add("--language");
                    parameters.add(language);
                }

                logger.infof("Invoking %s", parameters.stream().collect(Collectors.joining(" "));

                final Process process = new ProcessBuilder(parameters.toArray(new String[parameters.size()])).start();

                final int exitCode = process.waitFor();
                logger.infof("WhisperX final status %d", exitCode);

                if (exitCode == 0) {
                    if (Files.exists(Paths.get(resultFile))) {
                        // Read result file
                        try (InputStream is = new BufferedInputStream(new FileInputStream(resultFile))) {
                            var rawContent = is.readAllBytes();
                            String result = new String(rawContent, StandardCharsets.UTF_8);
                            return result;
                        }
                    } else {
                        throw new IOException("Result file " + resultFile + " not found");
                    }
                } else {
                    throw new IOException("Failed to invoke " + executable + " with code " + exitCode);
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Failed to invoke " + executable, e);
            } finally {
                // Clean up both temporary files
                try {
                    Files.deleteIfExists(Paths.get(sourceFile));
                    Files.deleteIfExists(Paths.get(resultFile));
                } catch (IOException e) {
                    throw new RuntimeException("Failed to clean up files", e);
                }
            }
        }, executor);
    }

}
