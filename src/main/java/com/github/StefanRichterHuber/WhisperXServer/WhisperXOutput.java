package com.github.StefanRichterHuber.WhisperXServer;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    public List<Segment> getSegments() {
        return segments;
    }

    public List<Word> getWordSegments() {
        return this.wordSegments;
    }
}
