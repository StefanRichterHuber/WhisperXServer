package com.github.StefanRichterHuber.WhisperXServer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.UnsupportedAudioFileException;

import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
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
import jakarta.ws.rs.core.Response.Status;

@Path("/")
public class WhisperXServer {

    @Inject
    Logger logger;

    @Inject
    AudioConverterService audioConverterService;

    @Inject
    WhisperXService whisperXService;

    @Inject
    ScheduledExecutorService scheduledExecutorService;

    private static final Map<String, Job> JOBS = new ConcurrentHashMap<>();

    private enum JobStatus {
        ON_GOING,
        FINISHED,
        ERROR,
    }

    private record Job(String id, String accept, JobStatus status, String result) {
    }

    /**
     * Converts any audio file to the audio format required for WhisperX
     * 
     * @param ar
     * @param content
     */
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

    /**
     * Checks the status of an on-going transcription job
     * 
     * @param jobID ID of the job to look up
     * @return Current status of transcription / result / error
     */
    @GET
    @Path("transcription-status")
    public Response transcriptionStatus(@QueryParam("job-id") String jobID) {
        final Job job = JOBS.get(jobID);
        if (job != null) {
            if (job.status() == JobStatus.FINISHED) {
                JOBS.remove(jobID);
                return Response.ok(job.result(), job.accept()).build();
            }
            if (job.status() == JobStatus.ERROR) {
                JOBS.remove(jobID);
                return Response.serverError().entity(job.result()).build();
            }
            if (job.status() == JobStatus.ON_GOING) {
                return Response.status(Status.ACCEPTED)
                        .entity(this.buildStatusResponse(jobID)).type(MediaType.APPLICATION_JSON).build();
            }
        }
        return Response.status(Status.NOT_FOUND).build();
    }

    /**
     * Starts a transcription of a a given audio file
     * 
     * @param content  Audio file content
     * @param language Language of the content
     * @param diarize  Add speaker diarization
     * @param accept   Output file format
     * @return
     * @throws UnsupportedAudioFileException
     * @throws IOException
     * @throws InterruptedException
     */

    @POST
    @Path("transcribe")
    @Consumes("audio/wav")
    public Response transcribe(
            byte[] content, //
            @QueryParam("language") String language, //
            @QueryParam("diarize") @DefaultValue("false") boolean diarize, //
            @HeaderParam(HttpHeaders.ACCEPT) String accept)
            throws UnsupportedAudioFileException, IOException, InterruptedException {
        final String outputFormat = this.getOutputFormat(accept);

        // Timeouts occur -> immediately send back a 202 Accepted and let the client
        // poll the status
        final String jobID = UUID.randomUUID().toString();
        JOBS.put(jobID, new Job(jobID, accept, JobStatus.ON_GOING, null));

        final CompletableFuture<Void> process = whisperXService.transcribe(content, diarize, language, outputFormat) //
                .thenApply(text -> {
                    return new Job(jobID, accept, JobStatus.FINISHED, text);
                })
                .exceptionally(e -> {
                    logger.errorf(e, "Failed to invoke WhisperX");
                    return new Job(jobID, accept, JobStatus.ERROR, e.getMessage());
                })
                .thenAccept(job -> {
                    JOBS.put(jobID, job);
                });

        // Always clean up the job after some hours, to prevent memory leaks
        this.scheduledExecutorService.schedule(() -> {
            JOBS.remove(jobID);
            if (!process.isDone()) {
                process.cancel(true);
            }
        }, 48, TimeUnit.HOURS);

        return Response.status(Status.ACCEPTED)
                .entity(this.buildStatusResponse(jobID)).type(MediaType.APPLICATION_JSON).build();
    }

    /**
     * Builds the JSON status response for an on-going transcription processs
     * 
     * @param id
     * @return
     */
    private TranscriptionStatus buildStatusResponse(String id) {
        return new TranscriptionStatus(id, String.format("/transcription-status?job-id=%s", id));
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
