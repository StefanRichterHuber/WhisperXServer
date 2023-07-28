package org.acme.com.github.StefanRichterHuber.WhisperXServer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.sound.sampled.UnsupportedAudioFileException;

import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/")
public class WhisperXServer {

    @Inject
    Logger logger;

    private final String workdir = "/tmp";

    @POST
    @Path("/convert")
    public void convertToTargetFormat(@Suspended AsyncResponse ar, byte[] content) {

    }

    @POST
    public void whisper(@Suspended AsyncResponse ar, byte[] content,
            @QueryParam("language") @DefaultValue("en") String language,
            @HeaderParam(HttpHeaders.ACCEPT) String accept)
            throws UnsupportedAudioFileException, IOException, InterruptedException {
        ByteArrayInputStream fileContent = new ByteArrayInputStream(content);

        final String outputFormat = this.getOutputFormat(accept);

        final String filePrefix = UUID.randomUUID().toString();
        final String sourceFile = workdir + "/" + filePrefix + ".wav";
        final String resultFile = workdir + "/" + filePrefix + "." + outputFormat;

        /*
         * Required audio format:
         * Channels: 1
         * Encoding: PCM_SIGNED
         * Frame Rate: 16000.0
         * Frame Size: 2
         * Sample Rate: 16000.0
         * Sample size (bits): 16
         * Big endian: false
         * Audio Format String: PCM_SIGNED 16000.0 Hz, 16 bit, mono, 2 bytes/frame,
         * little-endian
         */
        /*
         * try (AudioInputStream ais = AudioSystem.getAudioInputStream(fileContent)) {
         * AudioFormat audioFormat = ais.getFormat();
         * // logger.info("File Format Type: " + inputFileFormat.getType());
         * // logger.info("File Format String: " + inputFileFormat.toString());
         * // logger.info("File length: " + inputFileFormat.getByteLength());
         * // logger.info("Frame length: " + inputFileFormat.getFrameLength());
         * logger.info("Channels: " + audioFormat.getChannels());
         * logger.info("Encoding: " + audioFormat.getEncoding());
         * logger.info("Frame Rate: " + audioFormat.getFrameRate());
         * logger.info("Frame Size: " + audioFormat.getFrameSize());
         * logger.info("Sample Rate: " + audioFormat.getSampleRate());
         * logger.info("Sample size (bits): " + audioFormat.getSampleSizeInBits());
         * logger.info("Big endian: " + audioFormat.isBigEndian());
         * logger.info("Audio Format String: " + audioFormat.toString());
         * 
         * AudioFormat newFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
         * 16000.0F, 16, 1, 2, 16000.0F,
         * false);
         * 
         * try (AudioInputStream targetFormat =
         * AudioSystem.getAudioInputStream(newFormat, ais)) {
         * // Write content to file
         * 
         * AudioSystem.write(targetFormat, AudioFileFormat.Type.WAVE, new
         * File(sourceFile));
         * 
         * logger.info("Successfully wrote temporary file " + sourceFile);
         * }
         * }
         */

        // Audio conversion not supported in native image -> directly write file and
        // hope it is compatible

        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(sourceFile))) {
            os.write(content);
        }

        // TODO add queue
        CompletableFuture.runAsync(() -> {
            // Invoke whisperX
            try {
                final Process process = new ProcessBuilder("whisperx", sourceFile, //
                        "--compute_type", "int8", //
                        "--language", language, //
                        "--output_dir", workdir, //
                        "--output_format", outputFormat).start();

                final int exitCode = process.waitFor();
                logger.infof("Invoked whisperx on file %s with status %d", process, exitCode);

                if (exitCode == 0) {
                    if (Files.exists(Paths.get(resultFile))) {

                        // Read result file
                        try (InputStream is = new BufferedInputStream(new FileInputStream(resultFile))) {
                            var rawContent = is.readAllBytes();
                            String result = new String(rawContent, StandardCharsets.UTF_8);
                            ar.resume(Response.ok(result, accept).build());
                        }
                    } else {
                        ar.resume(Response.serverError().entity("Result file " + resultFile + " not found").build());
                    }
                } else {
                    ar.resume(Response.serverError().entity("Failed to invoke whisperx with code " + exitCode).build());
                }
            } catch (IOException | InterruptedException e) {
                logger.errorf(e, "Failed to invoke whisperx");
                ar.resume(Response.serverError().entity(e.toString()).build());
            }

        });

    }

    private String getOutputFormat(String accept) {
        switch (accept) {
            case MediaType.APPLICATION_JSON:
                return "json";
            case MediaType.TEXT_PLAIN:
                return "txt";
            case "text/srt":
                return "srt";
            case "text/vtt":
                return "vtt";
            case "text/tsv":
                return "tsv";
            case "text/aud":
                return "aud";
            default:
                return "txt";
        }
    }
}
