package com.kebabshop.backend;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Repository
public class ContentTranslations {
    public enum Kind {
        PROFILE("restaurant_profile_translation", "profile_id", "display_name", 160, 1000),
        CATEGORY("menu_category_translation", "category_id", "name", 160, 0),
        ITEM("menu_item_translation", "item_id", "name", 160, 1000),
        PROMOTION("promotion_translation", "promotion_id", "title", 160, 500);

        final String table, idColumn, firstColumn;
        final int firstLimit, secondLimit;
        Kind(String table, String idColumn, String firstColumn, int firstLimit, int secondLimit) {
            this.table = table; this.idColumn = idColumn; this.firstColumn = firstColumn;
            this.firstLimit = firstLimit; this.secondLimit = secondLimit;
        }
    }

    public record Text(String first, String description) {}

    private final JdbcTemplate jdbc;

    public ContentTranslations(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static String locale(String raw) {
        if (raw == null) return "lt";
        if (!Set.of("lt", "en", "ru", "ka").contains(raw)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported language");
        }
        return raw;
    }

    public Map<Long, Map<String, Text>> all(Kind kind) {
        var result = new HashMap<Long, Map<String, Text>>();
        String second = kind.secondLimit == 0 ? "NULL" : "description";
        jdbc.query("SELECT " + kind.idColumn + ", locale, " + kind.firstColumn + ", " + second
                + " FROM " + kind.table, rs -> {
            result.computeIfAbsent(rs.getLong(1), ignored -> new LinkedHashMap<>())
                    .put(rs.getString(2), new Text(rs.getString(3), rs.getString(4)));
        });
        return result;
    }

    public Map<String, Text> forId(Kind kind, long id) {
        var result = new LinkedHashMap<String, Text>();
        String second = kind.secondLimit == 0 ? "NULL" : "description";
        jdbc.query("SELECT locale, " + kind.firstColumn + ", " + second + " FROM " + kind.table
                + " WHERE " + kind.idColumn + " = ?", rs -> {
            result.put(rs.getString(1), new Text(rs.getString(2), rs.getString(3)));
        }, id);
        return result;
    }

    public void replace(Kind kind, long id, Map<String, Text> translations, Text canonical) {
        if (translations == null) return; // Legacy admin request preserves existing translations.
        var normalized = new LinkedHashMap<String, Text>();
        for (var entry : translations.entrySet()) {
            String lang = locale(entry.getKey());
            if (entry.getValue() == null) throw new IllegalArgumentException("Translation must be an object");
            Text text = new Text(normalize(entry.getValue().first(), kind.firstLimit),
                    normalize(entry.getValue().description(), kind.secondLimit));
            if ("lt".equals(lang)) {
                if (!text.equals(canonical)) throw new IllegalArgumentException("Lithuanian translation must match canonical fields");
                continue;
            }
            if (kind == Kind.CATEGORY && text.first() == null && text.description() != null) {
                throw new IllegalArgumentException("Category has no description");
            }
            if (text.first() != null || text.description() != null) normalized.put(lang, text);
        }
        jdbc.update("DELETE FROM " + kind.table + " WHERE " + kind.idColumn + " = ?", id);
        for (var entry : normalized.entrySet()) {
            if (kind.secondLimit == 0) {
                jdbc.update("INSERT INTO " + kind.table + " (" + kind.idColumn + ", locale, " + kind.firstColumn
                        + ") VALUES (?, ?, ?)", id, entry.getKey(), entry.getValue().first());
            } else {
                jdbc.update("INSERT INTO " + kind.table + " (" + kind.idColumn + ", locale, " + kind.firstColumn
                        + ", description) VALUES (?, ?, ?, ?)", id, entry.getKey(), entry.getValue().first(),
                        entry.getValue().description());
            }
        }
    }

    public static Map<String, Text> withCanonical(Text canonical, Map<String, Text> translations) {
        var result = new LinkedHashMap<String, Text>();
        result.put("lt", canonical);
        result.putAll(translations);
        return result;
    }

    public static Text resolve(String lang, Text canonical, Map<String, Text> translations) {
        Text candidate = translations.get(lang);
        return new Text(candidate == null || candidate.first() == null ? canonical.first() : candidate.first(),
                candidate == null || candidate.description() == null ? canonical.description() : candidate.description());
    }

    private static String normalize(String value, int limit) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (limit == 0 || trimmed.length() > limit) throw new IllegalArgumentException("Invalid translated field length");
        return trimmed;
    }
}
