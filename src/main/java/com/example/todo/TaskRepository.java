package com.example.todo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/** All writes (and the authoritative read) go to PostgreSQL through RDS Proxy. */
@Repository
public class TaskRepository {

    private static final RowMapper<Task> MAPPER = (rs, n) -> new Task(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getBoolean("completed"),
            rs.getObject("created_at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public TaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(String title) {
        jdbc.update("INSERT INTO tasks (title) VALUES (?)", title);
    }

    public List<Task> findAllNewestFirst() {
        return jdbc.query(
                "SELECT id, title, completed, created_at FROM tasks ORDER BY created_at DESC", MAPPER);
    }

    public Task findById(long id) {
        List<Task> rows = jdbc.query(
                "SELECT id, title, completed, created_at FROM tasks WHERE id = ?", MAPPER, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void updateTitle(long id, String title) {
        jdbc.update("UPDATE tasks SET title = ? WHERE id = ?", title, id);
    }

    public void setCompleted(long id, boolean completed) {
        jdbc.update("UPDATE tasks SET completed = ? WHERE id = ?", completed, id);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM tasks WHERE id = ?", id);
    }
}
