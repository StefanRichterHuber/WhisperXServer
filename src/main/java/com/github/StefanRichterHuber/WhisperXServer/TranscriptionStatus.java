package com.github.StefanRichterHuber.WhisperXServer;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.xml.bind.annotation.XmlRootElement;

@RegisterForReflection
@XmlRootElement
public class TranscriptionStatus {
    public static class Task {
        private final String href;

        private final String id;

        public Task() {
            this(null, null);
        }

        public Task(String id, String href) {
            this.id = id;
            this.href = href;
        }

        public String getHref() {
            return href;
        }

        public String getId() {
            return id;
        }

    }

    private final Task task;

    public TranscriptionStatus() {
        this(null);
    }

    public TranscriptionStatus(Task task) {
        this.task = task;
    }

    public TranscriptionStatus(String id, String href) {
        this.task = new Task(id, href);
    }

    public Task getTask() {
        return task;
    }
}
