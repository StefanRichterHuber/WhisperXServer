package com.github.StefanRichterHuber.WhisperXServer;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.quarkus.runtime.annotations.RegisterForReflection;

@JsonInclude(Include.NON_NULL)
@RegisterForReflection
public class TranscriptionStatus {
    @JsonInclude(Include.NON_NULL)
    @RegisterForReflection
    public static class Task {
        private final String href;

        private final String id;

        private final String contentType;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        private final ZonedDateTime start;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        private final ZonedDateTime end;

        public Task() {
            this(null, null, null, null, null);
        }

        public Task(String id, String href, String contentType, ZonedDateTime start, ZonedDateTime end) {
            this.id = id;
            this.href = href;
            this.contentType = contentType;
            this.start = start;
            this.end = end;
        }

        public String getHref() {
            return href;
        }

        public String getId() {
            return id;
        }

        public String getContentType() {
            return this.contentType;
        }

        public ZonedDateTime getStart() {
            return start;
        }

        public ZonedDateTime getEnd() {
            return end;
        }

    }

    private final Task task;

    public TranscriptionStatus() {
        this(null);
    }

    public TranscriptionStatus(Task task) {
        this.task = task;
    }

    public TranscriptionStatus(String id, String href, String contentType, ZonedDateTime start, ZonedDateTime end) {
        this.task = new Task(id, href, contentType, start, end);
    }

    public Task getTask() {
        return task;
    }
}
