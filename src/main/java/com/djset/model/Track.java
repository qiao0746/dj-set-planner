package com.djset.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Track {
    private final String id;
    private final String title;
    private final String artist;
    private final double bpm;
    private final double tempoConfidence;
    private final String key;
    private final double keyConfidence;
    private final double energy;
    private final double danceability;
    private final double djEnergy;
    private final double intensity;
    private final double tension;
    private final Map<String, Double> analysisDebug;
    private final List<String> genreTags;
    private final List<String> vibeTags;
    private final double durationSec;

    public Track(
            String id,
            String title,
            String artist,
            double bpm,
            double tempoConfidence,
            String key,
            double keyConfidence,
            double energy,
            double danceability,
            double djEnergy,
            double intensity,
            double tension,
            Map<String, Double> analysisDebug,
            List<String> genreTags,
            List<String> vibeTags,
            double durationSec
    ) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.bpm = bpm;
        this.tempoConfidence = tempoConfidence;
        this.key = key;
        this.keyConfidence = keyConfidence;
        this.energy = energy;
        this.danceability = danceability;
        this.djEnergy = djEnergy;
        this.intensity = intensity;
        this.tension = tension;
        this.analysisDebug = analysisDebug == null ? Map.of() : Map.copyOf(analysisDebug);
        this.genreTags = toUnmodifiableList(genreTags);
        this.vibeTags = toUnmodifiableList(vibeTags);
        this.durationSec = durationSec;
    }

    public Track(
            String id,
            String title,
            String artist,
            double bpm,
            double tempoConfidence,
            String key,
            double keyConfidence,
            double energy,
            double danceability,
            List<String> genreTags,
            List<String> vibeTags,
            double durationSec
    ) {
        this(
                id, title, artist, bpm, tempoConfidence, key, keyConfidence,
                energy, danceability,
                0.0, 0.0, 0.0, Map.of(),
                genreTags, vibeTags, durationSec
        );
    }

    private static List<String> toUnmodifiableList(List<String> input) {
        return input == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(input));
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public double getBpm() {
        return bpm;
    }

    public String getKey() {
        return key;
    }

    public double getKeyConfidence() {
        return keyConfidence;
    }

    public double getEnergy() {
        return energy;
    }

    public double getTempoConfidence() {
        return tempoConfidence;
    }

    public double getDanceability() {
        return danceability;
    }

    public double getDjEnergy() {
        return djEnergy;
    }

    public double getIntensity() {
        return intensity;
    }

    public double getTension() {
        return tension;
    }

    public Map<String, Double> getAnalysisDebug() {
        return analysisDebug;
    }

    public List<String> getGenreTags() {
        return genreTags;
    }

    public List<String> getVibeTags() {
        return vibeTags;
    }

    public double getDurationSec() {
        return durationSec;
    }

    @Override
    public String toString() {
        return "Track{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                ", bpm=" + bpm +
                ", tempoConfidence=" + tempoConfidence +
                ", key='" + key + '\'' +
                ", keyConfidence=" + keyConfidence +
                ", energy=" + energy +
                ", danceability=" + danceability +
                ", djEnergy=" + djEnergy +
                ", intensity=" + intensity +
                ", tension=" + tension +
                ", analysisDebug=" + analysisDebug +
                ", genreTags=" + genreTags +
                ", vibeTags=" + vibeTags +
                ", durationSec=" + durationSec +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Track track)) return false;
        return Double.compare(track.bpm, bpm) == 0
                && Double.compare(track.tempoConfidence, tempoConfidence) == 0
                && Double.compare(track.keyConfidence, keyConfidence) == 0
                && Double.compare(track.energy, energy) == 0
                && Double.compare(track.danceability, danceability) == 0
                && Double.compare(track.djEnergy, djEnergy) == 0
                && Double.compare(track.intensity, intensity) == 0
                && Double.compare(track.tension, tension) == 0
                && Double.compare(track.durationSec, durationSec) == 0
                && Objects.equals(id, track.id)
                && Objects.equals(title, track.title)
                && Objects.equals(artist, track.artist)
                && Objects.equals(key, track.key)
                && Objects.equals(analysisDebug, track.analysisDebug)
                && Objects.equals(genreTags, track.genreTags)
                && Objects.equals(vibeTags, track.vibeTags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id, title, artist, bpm, tempoConfidence, key, keyConfidence,
                energy, danceability, djEnergy, intensity, tension, analysisDebug,
                genreTags, vibeTags, durationSec
        );
    }
}
