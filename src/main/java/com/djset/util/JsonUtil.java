package com.djset.util;

import com.djset.model.SetPlan;
import com.djset.model.Track;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class JsonUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private JsonUtil() {
    }

    public static List<Track> readTracks(Path path) {
        try {
            return parseTrackRows(path);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to read track JSON: " + path, ex);
        }
    }

    public static String toJson(SetPlan setPlan) {
        try {
            return MAPPER.writeValueAsString(setPlan);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize set plan.", ex);
        }
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize object.", ex);
        }
    }

    public static void writeTracks(Path path, List<Track> tracks) {
        try {
            MAPPER.writeValue(path.toFile(), tracks);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to write track JSON: " + path, ex);
        }
    }

    private static List<Track> parseTrackRows(Path path) throws IOException {
        List<Map<String, Object>> rows = MAPPER.readValue(path.toFile(), new TypeReference<>() {});
        List<Track> tracks = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            tracks.add(mapTrack(row));
        }
        return tracks;
    }

    private static Track mapTrack(Map<String, Object> row) {
        return new Track(
                asString(row.get("id")),
                asString(row.get("title")),
                asString(row.get("artist")),
                asDouble(row.get("bpm")),
                asDoubleOrDefault(row.get("tempoConfidence"), 0.0),
                asString(row.get("key")),
                asDoubleOrDefault(row.get("keyConfidence"), 0.0),
                asDouble(row.get("energy")),
                asDoubleOrDefault(row.get("danceability"), 0.0),
                asDoubleOrDefault(row.get("djEnergy"), 0.0),
                asDoubleOrDefault(row.get("intensity"), 0.0),
                asDoubleOrDefault(row.get("tension"), 0.0),
                asDoubleMap(row.get("analysisDebug")),
                asStringList(row.get("genreTags")),
                asStringList(row.get("vibeTags")),
                asDoubleOrDefault(row.get("durationSec"), 0.0)
        );
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static double asDouble(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            return Double.parseDouble(s);
        }
        return 0.0;
    }

    private static double asDoubleOrDefault(Object v, double defaultValue) {
        if (v == null) return defaultValue;
        return asDouble(v);
    }

    private static List<String> asStringList(Object v) {
        if (!(v instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                out.add(String.valueOf(item));
            }
        }
        return out;
    }

    private static Map<String, Double> asDoubleMap(Object v) {
        if (!(v instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        var out = new java.util.LinkedHashMap<String, Double>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            if (e.getKey() == null) continue;
            out.put(String.valueOf(e.getKey()), asDoubleOrDefault(e.getValue(), 0.0));
        }
        return out;
    }
}
