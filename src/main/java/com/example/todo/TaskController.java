package com.example.todo;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Controller
public class TaskController {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    private final TaskRepository repo;
    private final TaskCache cache;

    public TaskController(TaskRepository repo, TaskCache cache) {
        this.repo = repo;
        this.cache = cache;
    }

    /**
     * To-Do UI. Reads are served from Redis when warm; on a miss we load from
     * RDS (through RDS Proxy) and populate the cache. Doubles as the ALB health
     * check (returns 200 even when empty).
     */
    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String index(@RequestParam(value = "filter", required = false, defaultValue = "all") String filter) {
        boolean cacheHit = true;
        List<Task> tasks = cache.read();
        if (tasks == null) {                 // cache miss / outage -> read RDS
            cacheHit = false;
            tasks = repo.findAllNewestFirst();
            cache.put(tasks);
        }

        long total = tasks.size();
        long done = tasks.stream().filter(Task::completed).count();
        long open = total - done;
        int percent = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);

        String f = switch (filter == null ? "all" : filter) {
            case "active", "completed" -> filter;
            default -> "all";
        };

        StringBuilder rows = new StringBuilder();
        for (Task t : tasks) {
            if (f.equals("active") && t.completed()) continue;
            if (f.equals("completed") && !t.completed()) continue;

            String title = escape(t.title());
            long id = t.id();
            String done2 = t.completed() ? " done" : "";
            String when = t.createdAt() == null ? "" : DATE_FMT.format(t.createdAt());

            rows.append("<li class='item").append(done2).append("'>")
                // toggle completed
                .append("<form class='toggle' action='/toggle' method='post'>")
                .append("<input type='hidden' name='id' value='").append(id).append("'>")
                .append("<input type='hidden' name='completed' value='").append(!t.completed()).append("'>")
                .append("<button type='submit' class='check' aria-label='Toggle complete'>")
                .append("<svg viewBox='0 0 24 24' width='14' height='14' fill='none' stroke='currentColor' stroke-width='3' stroke-linecap='round' stroke-linejoin='round'><path d='M4 12l5 5L20 6'/></svg>")
                .append("</button>")
                .append("</form>")
                // inline title edit (looks like text; saves on Enter or blur-if-changed)
                .append("<form class='edit' action='/update' method='post'>")
                .append("<input type='hidden' name='id' value='").append(id).append("'>")
                .append("<input class='title' type='text' name='title' value='").append(title)
                .append("' maxlength='280' aria-label='Task title' autocomplete='off' ")
                .append("onblur=\"if(this.value.trim()&&this.value!==this.defaultValue)this.form.submit()\">")
                .append("</form>")
                .append("<time class='date'>").append(when).append("</time>")
                // delete
                .append("<form class='del' action='/delete' method='post' onsubmit=\"return confirm('Delete this task?')\">")
                .append("<input type='hidden' name='id' value='").append(id).append("'>")
                .append("<button type='submit' class='del-btn' aria-label='Delete task'>")
                .append("<svg viewBox='0 0 24 24' width='16' height='16' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6m5 4v6m4-6v6'/></svg>")
                .append("</button>")
                .append("</form>")
                .append("</li>");
        }

        String emptyMsg = switch (f) {
            case "active"    -> "No active tasks — you're all caught up. 🎉";
            case "completed" -> "No completed tasks yet.";
            default          -> "No tasks yet. Add your first one above.";
        };
        boolean nothingShown = rows.length() == 0;
        String body = nothingShown ? "<li class='empty'>" + emptyMsg + "</li>" : rows.toString();

        String summary = total == 0
                ? "Nothing to do"
                : done + " of " + total + " completed";

        String filters = tab("all", "All", f, total)
                       + tab("active", "Active", f, open)
                       + tab("completed", "Completed", f, done);

        String source = cacheHit
                ? "<span class='src'>⚡ Served from Redis cache</span>"
                : "<span class='src'>🗄️ Loaded from database</span>";

        return PAGE.replace("<!--ROWS-->", body)
                   .replace("<!--SUMMARY-->", summary)
                   .replace("<!--PERCENT-->", String.valueOf(percent))
                   .replace("<!--FILTERS-->", filters)
                   .replace("<!--SOURCE-->", source);
    }

    private static String tab(String key, String label, String active, long count) {
        String cls = key.equals(active) ? "tab active" : "tab";
        return "<a class='" + cls + "' href='/?filter=" + key + "'>" + label
             + "<span class='badge'>" + count + "</span></a>";
    }

    /** Create a task: write to RDS (via proxy), then invalidate the cache. */
    @PostMapping("/add")
    public String add(@RequestParam("title") String title) {
        if (title != null && !title.isBlank()) {
            repo.save(title.trim());
            cache.invalidate();
        }
        return "redirect:/";
    }

    /** Toggle completion: write to RDS, then invalidate the cache. */
    @PostMapping("/toggle")
    public String toggle(@RequestParam("id") long id,
                         @RequestParam("completed") boolean completed) {
        repo.setCompleted(id, completed);
        cache.invalidate();
        return "redirect:/";
    }

    /** Edit a task's title: write to RDS, then invalidate the cache. */
    @PostMapping("/update")
    public String update(@RequestParam("id") long id,
                         @RequestParam(value = "title", required = false) String title) {
        if (title != null && !title.isBlank()) {
            repo.updateTitle(id, title.trim());
            cache.invalidate();
        }
        return "redirect:/";
    }

    /** Delete a task: write to RDS, then invalidate the cache. */
    @PostMapping("/delete")
    public String delete(@RequestParam("id") long id) {
        repo.delete(id);
        cache.invalidate();
        return "redirect:/";
    }

    /** Escape for safe use in both element text and single/double-quoted attributes. */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static final String PAGE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Tasks</title>
              <style>
                :root {
                  --bg: #f4f5f7; --card: #ffffff; --text: #1d2433; --muted: #8b92a4;
                  --line: #e7e9ef; --accent: #4f46e5; --accent-soft: #eef0fe;
                  --ok: #16a34a; --danger: #e11d48; --shadow: 0 1px 2px rgba(16,24,40,.06), 0 8px 24px rgba(16,24,40,.06);
                }
                * { box-sizing: border-box; }
                html, body { height: 100%; }
                body {
                  margin: 0; color: var(--text); background: var(--bg);
                  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, system-ui, sans-serif;
                  -webkit-font-smoothing: antialiased;
                }
                .wrap { max-width: 640px; margin: 0 auto; padding: 3rem 1.25rem 4rem; }

                .head { display: flex; align-items: center; gap: .7rem; margin-bottom: 1.5rem; }
                .logo { width: 38px; height: 38px; border-radius: 10px; display: grid; place-items: center;
                        background: var(--accent); color: #fff; box-shadow: var(--shadow); }
                .head h1 { margin: 0; font-size: 1.4rem; font-weight: 700; letter-spacing: -.01em; }
                .head p { margin: .1rem 0 0; font-size: .85rem; color: var(--muted); }

                .card { background: var(--card); border: 1px solid var(--line); border-radius: 16px;
                        box-shadow: var(--shadow); overflow: hidden; }

                .add { display: flex; gap: .6rem; padding: 1rem; border-bottom: 1px solid var(--line); }
                .add input {
                  flex: 1; padding: .7rem .9rem; font-size: .95rem; color: var(--text);
                  border: 1px solid var(--line); border-radius: 10px; background: #fff; outline: none;
                  transition: border-color .15s, box-shadow .15s;
                }
                .add input::placeholder { color: var(--muted); }
                .add input:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
                .add button {
                  padding: 0 1.2rem; border: 0; border-radius: 10px; cursor: pointer; font-weight: 600;
                  font-size: .92rem; background: var(--accent); color: #fff; transition: filter .15s, transform .05s;
                }
                .add button:hover { filter: brightness(1.07); }
                .add button:active { transform: translateY(1px); }

                .meta { display: flex; align-items: center; justify-content: space-between; gap: 1rem;
                        padding: .9rem 1rem .2rem; }
                .summary { font-size: .82rem; color: var(--muted); font-weight: 500; }
                .progress { flex: 1; height: 6px; background: var(--line); border-radius: 999px; overflow: hidden; max-width: 220px; }
                .progress > span { display: block; height: 100%; background: var(--ok); border-radius: 999px;
                                   transition: width .3s ease; }

                .filters { display: flex; gap: .3rem; padding: .6rem 1rem 1rem; }
                .tab { display: inline-flex; align-items: center; gap: .4rem; text-decoration: none;
                       font-size: .82rem; font-weight: 600; color: var(--muted);
                       padding: .35rem .7rem; border-radius: 8px; transition: background .15s, color .15s; }
                .tab:hover { background: var(--bg); color: var(--text); }
                .tab.active { background: var(--accent-soft); color: var(--accent); }
                .tab .badge { font-size: .72rem; font-weight: 700; background: rgba(0,0,0,.06);
                              color: inherit; padding: .05rem .4rem; border-radius: 999px; }
                .tab.active .badge { background: rgba(79,70,229,.16); }

                ul.list { list-style: none; margin: 0; padding: 0 .5rem .5rem; }
                .item { display: flex; align-items: center; gap: .75rem; padding: .7rem .6rem;
                        border-radius: 10px; transition: background .12s; }
                .item:hover { background: var(--bg); }
                .item + .item { border-top: 1px solid var(--line); }

                .check { flex: 0 0 auto; width: 22px; height: 22px; border-radius: 50%; cursor: pointer;
                         border: 2px solid var(--line); background: #fff; color: #fff; display: grid;
                         place-items: center; padding: 0; transition: background .15s, border-color .15s; }
                .check svg { opacity: 0; transition: opacity .12s; }
                .check:hover { border-color: var(--accent); }
                .item.done .check { background: var(--ok); border-color: var(--ok); }
                .item.done .check svg { opacity: 1; }

                .edit { flex: 1; min-width: 0; margin: 0; }
                .title { width: 100%; border: 1px solid transparent; background: transparent; color: var(--text);
                         font-size: .95rem; padding: .35rem .5rem; border-radius: 8px; outline: none;
                         transition: border-color .15s, background .15s; }
                .title:hover { background: #fff; border-color: var(--line); }
                .title:focus { background: #fff; border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
                .item.done .title { color: var(--muted); text-decoration: line-through; }

                .date { flex: 0 0 auto; font-size: .76rem; color: var(--muted); min-width: 3rem; text-align: right; }

                .del { margin: 0; }
                .del-btn { flex: 0 0 auto; border: 0; background: transparent; color: var(--muted);
                           cursor: pointer; padding: .35rem; border-radius: 8px; display: grid; place-items: center;
                           opacity: 0; transition: opacity .12s, background .12s, color .12s; }
                .item:hover .del-btn { opacity: 1; }
                .del-btn:hover { background: #fde8ee; color: var(--danger); }

                .empty { text-align: center; color: var(--muted); padding: 3rem 1rem; font-size: .95rem; }

                .foot { text-align: center; margin-top: 1.25rem; }
                .src { font-size: .74rem; color: var(--muted); }
              </style>
            </head>
            <body>
              <div class="wrap">
                <div class="head">
                  <span class="logo">
                    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M4 12l5 5L20 6"/></svg>
                  </span>
                  <div>
                    <h1>Tasks</h1>
                    <p>Stay on top of what matters.</p>
                  </div>
                </div>

                <div class="card">
                  <form class="add" action="/add" method="post">
                    <input type="text" name="title" placeholder="Add a new task…" maxlength="280" required autofocus autocomplete="off">
                    <button type="submit">Add</button>
                  </form>

                  <div class="meta">
                    <span class="summary"><!--SUMMARY--></span>
                    <span class="progress"><span style="width:<!--PERCENT-->%"></span></span>
                  </div>

                  <nav class="filters"><!--FILTERS--></nav>

                  <ul class="list"><!--ROWS--></ul>
                </div>

                <div class="foot"><!--SOURCE--></div>
              </div>
            </body>
            </html>
            """;
}
