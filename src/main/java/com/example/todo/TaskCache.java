package com.example.todo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;


/**
 * Read-through cache for the task list, backed by ElastiCache (Redis).
 *
 *   - read():      try Redis first; on a miss, the caller loads from RDS and
 *                  calls put() to populate the cache (with a short TTL).
 *   - invalidate():called after every write so the next read re-loads from RDS.
 *
 * Every Redis call is wrapped so a cache outage degrades to a direct DB read
 * instead of failing the request.
 */
@Service
public class TaskCache {

    static final String KEY = "todo:tasks:all";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public TaskCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Returns the cached task list, or null on a miss / cache outage. */
    public List<Task> read() {
        try {
            String json = redis.opsForValue().get(KEY);
            if (json == null) {
                return null;
            }
            return mapper.readValue(json, mapper.getTypeFactory()
                    .constructCollectionType(List.class, Task.class));
        } catch (Exception e) {
            return null;
        }
    }

    /** Stores the task list with a short TTL. Best-effort. */
    public void put(List<Task> tasks) {
        try {
            redis.opsForValue().set(KEY, mapper.writeValueAsString(tasks), TTL);
        } catch (Exception ignored) {
        }
    }

    /** Drops the cached list so the next read reflects the latest write. */
    public void invalidate() {
        try {
            redis.delete(KEY);
        } catch (Exception ignored) {
            // best-effort invalidation
        }
    }
}
