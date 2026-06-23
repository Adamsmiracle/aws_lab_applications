package com.example.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class PhotoRepository {

    private static final RowMapper<Photo> MAPPER = (rs, n) -> new Photo(
            rs.getLong("id"),
            rs.getString("s3_key"),
            rs.getString("description"),
            rs.getObject("created_at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public PhotoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(String s3Key, String description) {
        jdbc.update("INSERT INTO photos (s3_key, description) VALUES (?, ?)", s3Key, description);
    }

    public List<Photo> findAllNewestFirst() {
        return jdbc.query(
                "SELECT id, s3_key, description, created_at FROM photos ORDER BY created_at DESC", MAPPER);
    }

    public Photo findById(long id) {
        List<Photo> rows = jdbc.query(
                "SELECT id, s3_key, description, created_at FROM photos WHERE id = ?", MAPPER, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void updateDescription(long id, String description) {
        jdbc.update("UPDATE photos SET description = ? WHERE id = ?", description, id);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM photos WHERE id = ?", id);
    }
}
