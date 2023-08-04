package com.github.StefanRichterHuber.WhisperXServer;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
@Disabled
public class WhisperXServerTest {

    @Inject
    AudioConverterService audioConverterService;

    @Inject
    WhisperXService whisperXService;

    @Test
    public void convertAudio() throws FileNotFoundException, IOException, InterruptedException, ExecutionException {
        try (FileInputStream fis = new FileInputStream("podcast.mpga");
                FileOutputStream fos = new FileOutputStream("podcast.wav")) {
            byte[] result = audioConverterService.convertAudioToTargetFormat(fis);
            assertNotNull(result);
            fos.write(result);

        }
    }

    @Test
    public void convertTranscription() throws FileNotFoundException, IOException {

        try (FileInputStream fis = new FileInputStream("podcast.json")) {
            ObjectMapper om = new ObjectMapper();
            var output = om.readValue(fis, WhisperXOutput.class);

            var result = whisperXService.convertTranscription(output, "txt");
            System.out.println(result);
        }

    }

    @Test
    public void convertTranscriptionWithoutSpeaker() throws FileNotFoundException, IOException {

        try (FileInputStream fis = new FileInputStream("podcast-without-diarize.json")) {
            ObjectMapper om = new ObjectMapper();
            var output = om.readValue(fis, WhisperXOutput.class);

            var result = whisperXService.convertTranscription(output, "txt");
            System.out.println(result);
        }

    }
}
