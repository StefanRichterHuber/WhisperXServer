package com.github.StefanRichterHuber.WhisperXServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.StefanRichterHuber.WhisperXServer.WhisperXOutput.Segment.Word;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class WhisperXOutput {
    @RegisterForReflection
    public static class Segment {
        @RegisterForReflection
        public static class Word {
            private final String word;
            private final double start;
            private final double end;
            private final double score;
            private final String speaker;

            public String getWord() {
                return word;
            }

            public double getStart() {
                return start;
            }

            public double getEnd() {
                return end;
            }

            public double getScore() {
                return score;
            }

            public String getSpeaker() {
                return speaker;
            }

            public String toString() {
                return String.format("%s (%,.3f - %,.3f) with score %,.2f: %s", speaker, start, end, score, word);
            }

            public Word() {
                this.word = null;
                this.start = 0.0d;
                this.end = 0.0d;
                this.score = 0.0d;
                this.speaker = null;
            }

        }

        private final double start;
        private final double end;
        private final String text;
        private final List<Word> words;
        private final String speaker;

        public double getStart() {
            return start;
        }

        public double getEnd() {
            return end;
        }

        public String getText() {
            return text;
        }

        public List<Word> getWords() {
            return words;
        }

        public String getSpeaker() {
            return this.speaker;
        }

        public String toString() {
            return String.format("%s (%,.3f - %,.3f): %s", speaker, start, end, text);
        }

        public Segment() {
            this.start = 0.0d;
            this.end = 0.0d;
            this.text = null;
            this.words = new ArrayList<>();
            this.speaker = null;
        }

    }

    private final List<Segment> segments;
    @JsonProperty("word_segments")
    private final List<Word> wordSegments;

    public WhisperXOutput() {
        segments = new ArrayList<>();
        this.wordSegments = new ArrayList<>();
    }

    /**
     * Converts this json representation of the WhisperX output to another format
     * 
     * @param outputFormat One of srt,vtt,txt,tsv,json,aud
     * @return String representation or or null if format not supported
     */
    public String toString(final String outputFormat) {
        if ("json".equals(outputFormat)) {
            final ObjectMapper om = new ObjectMapper();
            try {
                final String result = om.writeValueAsString(this);
                return result;
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        if ("txt".equals(outputFormat)) {
            String currentSpeaker = null;
            final StringBuilder sb = new StringBuilder();

            for (Segment segment : this.getSegments()) {
                final String speaker = segment.getSpeaker();
                // Header line for each speaker
                if (speaker != null && !speaker.isBlank() && !Objects.equals(currentSpeaker, speaker)) {
                    if (!sb.isEmpty()) {
                        sb.append("\n\n");
                    }
                    sb.append(speaker).append(":").append("\n");
                    currentSpeaker = speaker;
                }
                final String text = segment.getText().trim();
                sb.append(text);
                if (text.endsWith("!") || text.endsWith("?")
                        || text.endsWith(".")) {
                    sb.append(" ");
                }
            }
            return sb.toString();
        }
        // logger.errorf("Output format '%s' not supported for conversion",
        // outputFormat);
        return null;
    }

    public String toString() {
        return this.toString("txt");
    }

    public List<Segment> getSegments() {
        return segments;
    }

    public List<Word> getWordSegments() {
        return this.wordSegments;
    }
}
