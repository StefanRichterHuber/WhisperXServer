package com.github.StefanRichterHuber.WhisperXServer;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
@Disabled
public class WhisperXServerTest {

    @Inject
    AudioConverterService audioConverterService;

    @Test
    public void t1() throws FileNotFoundException, IOException, InterruptedException, ExecutionException {
        try (FileInputStream fis = new FileInputStream("podcast.mpga");
                FileOutputStream fos = new FileOutputStream("podcast.wav")) {
            byte[] result = audioConverterService.convertAudioToTargetFormat(fis);
            assertNotNull(result);
            fos.write(result);

        }
    }
}
