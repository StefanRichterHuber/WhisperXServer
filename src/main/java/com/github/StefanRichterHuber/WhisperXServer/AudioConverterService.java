package com.github.StefanRichterHuber.WhisperXServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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

@ApplicationScoped
public class AudioConverterService {
    @Inject
    Logger logger;

    /**
     * Invokes a local ffmpeg to convert an audio InputStream into the required
     * target format for wisperX
     * 
     * @param sourceData InputStream containing the source audio
     * @return Byte array with the target data format
     * @throws IOException
     * @throws InterruptedException
     * @throws ExecutionException
     */

    public byte[] convertAudioToTargetFormat(InputStream sourceData)
            throws IOException, InterruptedException, ExecutionException {
        // ffmpeg -i 111.mp3 -acodec pcm_ s16le -ac 1 -ar 16000 out.wav
        /// cat podcast.mpga | ffmpeg -i pipe: -acodec pcm_s16le -ac 1 -ar 16000 -f wav
        // -

        logger.infof("Received file to convert to wav format");
        final Process process = new ProcessBuilder("ffmpeg", //
                "-i", "pipe:", //
                "-loglevel", "quiet", //
                "-acodec", "pcm_s16le", //
                "-ac", "1", //
                "-ar", "16000", //
                "-f", "wav", // Output as wav file
                "-" // Output to stdout
        ).start();

        // If loglevel is not quiet, lots of information is collected here. Fetch it,
        // otherwise the stream blocks
        ProcessUtils.handleProcessOutput(process.getErrorStream(), line -> logger.debug(line));

        // We have to collect the result data async because otherwise the stdin
        // outputstream blocks
        final CompletableFuture<byte[]> convertedAudio = CompletableFuture.supplyAsync(() -> {
            try (InputStream stdout = process.getInputStream()) {
                final byte[] result = stdout.readAllBytes();

                final int status = process.waitFor();
                logger.infof("Finalized conversion of audio input file to target format with status %d", status);

                if (status == 0) {
                    return result;
                } else {
                    throw new IOException("Failed to invoke ffmepg. Status code " + status);
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        // Pipe source data to stdin
        try (InputStream is = sourceData;
                OutputStream os = process.getOutputStream()) {
            is.transferTo(os);
        }

        return convertedAudio.get();
    }
}
