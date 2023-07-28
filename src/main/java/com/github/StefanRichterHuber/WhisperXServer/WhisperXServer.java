package com.github.StefanRichterHuber.WhisperXServer;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.sound.sampled.UnsupportedAudioFileException;

import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
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

    @Inject
    AudioConverterService audioConverterService;

    @Inject
    WhisperXService whisperXService;

    @POST
    @Path("/convert")
    @Consumes("audio/*")
    @Produces("audio/wav")
    public void convertAudioToTargetFormat(@Suspended AsyncResponse ar, InputStream content) {
        CompletableFuture.runAsync(() -> {
            try {
                final byte[] converted = audioConverterService.convertAudioToTargetFormat(content);
                ar.resume(Response.ok(converted, "audio/wav").build());

            } catch (IOException | InterruptedException | ExecutionException e) {
                ar.resume(Response.serverError().entity(e.toString()).build());
            }
        });
    }

    @POST
    @Path("transcribe")
    @Consumes("audio/wav")
    public void transcribe(@Suspended AsyncResponse ar, byte[] content,
            @QueryParam("language") @DefaultValue("en") String language,
            @HeaderParam(HttpHeaders.ACCEPT) String accept)
            throws UnsupportedAudioFileException, IOException, InterruptedException {
        final String outputFormat = this.getOutputFormat(accept);

        whisperXService.transcribe(content, language, outputFormat) //
                .thenAccept(text -> {
                    ar.resume(Response.ok(text, accept).build());
                })
                .exceptionally(e -> {
                    ar.resume(Response.serverError().entity(e.getMessage()).build());
                    return null;
                });
    }

    /**
     * Converts the mime type from the accept header to the requested output file
     * format
     * 
     * @param accept Accept header
     * @return output file format
     */
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
