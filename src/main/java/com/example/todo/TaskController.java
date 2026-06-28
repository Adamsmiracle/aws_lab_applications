package com.example.todo;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class TaskController {

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
    public String index() {
        boolean cacheHit = true;
        List<Task> tasks = cache.read();
        if (tasks == null) {                 // cache miss / outage -> read RDS
            cacheHit = false;
            tasks = repo.findAllNewestFirst();
            cache.put(tasks);
        }

        long open = tasks.stream().filter(t -> !t.completed()).count();

        StringBuilder rows = new StringBuilder();
        for (Task t : tasks) {
            String title = escape(t.title());
            long id = t.id();
            String done = t.completed() ? " done" : "";
            rows.append("<li class='item").append(done).append("'>")
                // toggle completed
                .append("<form class='toggle' action='/toggle' method='post'>")
                .append("<input type='hidden' name='id' value='").append(id).append("'>")
                .append("<input type='hidden' name='completed' value='").append(!t.completed()).append("'>")
                .append("<button type='submit' class='check' title='Toggle complete'>")
                .append(t.completed() ? "✓" : "").append("</button>")
                .append("</form>")
                // inline title edit
                .append("<form class='edit' action='/update' method='post'>")
                .append("<input type='hidden' name='id' value='").append(id).append("'>")
                .append("<input type='text' name='title' value='").append(title)
                .append("' maxlength='280' aria-label='Task title'>")
                .append("<button type='submit' title='Save'>Save</button>")
                .append("</form>")
                // delete
                .append("<form class='del' action='/delete' method='post' onsubmit=\"return confirm('Delete this task?')\">")
                .append("<input type='hidden' name='id' value='").append(id).append("'>")
                .append("<button type='submit' class='danger' title='Delete'>✕</button>")
                .append("</form>")
                .append("</li>");
        }
        String body = tasks.isEmpty()
                ? "<li class='empty'>Nothing to do yet — add your first task above. ✨</li>"
                : rows.toString();

        String source = cacheHit
                ? "<span class='src cache'>⚡ served from Redis cache</span>"
                : "<span class='src db'>🗄️ loaded from RDS (cache populated)</span>";

        return PAGE.replace("<!--ROWS-->", body)
                   .replace("<!--OPEN-->", String.valueOf(open))
                   .replace("<!--SOURCE-->", source);
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
              <title>To-Do</title>
              <style>
                :root {
                  --bg: #0b0e1f; --panel: #161a33; --panel-2: #1d2244;
                  --text: #ececf5; --muted: #8a8fc0; --line: #2a2f55; --accent: #6b7bff;
                  --ok: #36d399;
                }
                * { box-sizing: border-box; }
                body {
                  font-family: 'Segoe UI', system-ui, -apple-system, sans-serif; margin: 0;
                  background: radial-gradient(1200px 600px at 50% -10%, #1a1f44 0%, var(--bg) 55%);
                  color: var(--text); min-height: 100vh;
                }
                header {
                  position: sticky; top: 0; z-index: 5;
                  display: flex; align-items: center; justify-content: space-between;
                  gap: 1rem; padding: 1rem 1.5rem;
                  background: rgba(11,14,31,.78); backdrop-filter: blur(10px);
                  border-bottom: 1px solid var(--line);
                }
                header h1 { margin: 0; font-size: 1.25rem; font-weight: 700; letter-spacing: .2px; }
                .count { font-size: .85rem; color: var(--muted);
                         background: var(--panel-2); padding: .3rem .7rem; border-radius: 999px;
                         border: 1px solid var(--line); }
                main { max-width: 720px; margin: 1.25rem auto; padding: 0 1.25rem 3rem; }
                .add {
                  display: flex; gap: .6rem; align-items: center;
                  padding: 1rem 1.25rem; background: var(--panel);
                  border: 1px solid var(--line); border-radius: 14px;
                }
                .add input[type=text] {
                  flex: 1; padding: .65rem .85rem; border-radius: 10px;
                  border: 1px solid var(--line); background: var(--bg); color: var(--text);
                }
                .add input[type=text]:focus { outline: 2px solid var(--accent); border-color: transparent; }
                .add button {
                  padding: .65rem 1.4rem; border: 0; border-radius: 10px; cursor: pointer;
                  background: linear-gradient(135deg, #6b7bff, #8a63ff); color: #fff; font-weight: 700;
                }
                .add button:hover { filter: brightness(1.08); }
                .src { display: inline-block; margin: .9rem .2rem 0; font-size: .82rem;
                       padding: .25rem .65rem; border-radius: 999px; border: 1px solid var(--line); }
                .src.cache { color: #cfe9ff; background: #14233a; }
                .src.db { color: #ffe9c2; background: #2e2410; }
                ul.list { list-style: none; margin: 1rem 0 0; padding: 0; display: flex; flex-direction: column; gap: .6rem; }
                .item {
                  display: flex; gap: .5rem; align-items: center;
                  background: var(--panel); border: 1px solid var(--line);
                  border-radius: 12px; padding: .55rem .7rem;
                }
                .item.done .edit input[type=text] { text-decoration: line-through; color: var(--muted); }
                .check {
                  width: 28px; height: 28px; flex: 0 0 auto; border-radius: 50%;
                  border: 1px solid var(--line); background: var(--bg); color: var(--ok);
                  cursor: pointer; font-weight: 800;
                }
                .item.done .check { background: var(--ok); color: #04210f; border-color: var(--ok); }
                .edit { display: flex; gap: .4rem; flex: 1; min-width: 0; }
                .edit input[type=text] {
                  flex: 1; min-width: 0; padding: .42rem .55rem; border-radius: 8px;
                  border: 1px solid var(--line); background: var(--bg); color: var(--text); font-size: .92rem;
                }
                .item button[type=submit] {
                  padding: .42rem .75rem; border: 0; border-radius: 8px; cursor: pointer;
                  font-size: .8rem; font-weight: 600; background: var(--panel-2); color: var(--text);
                }
                .item .danger { background: #4a1f2d; color: #ffb3c6; }
                .item .danger:hover { background: #6e2a40; }
                .empty { text-align: center; color: var(--muted); padding: 3rem 1rem; font-size: 1.05rem;
                         background: var(--panel); border: 1px dashed var(--line); border-radius: 12px; }
              </style>
            </head>
            <body>
              <header>
                <h1>✅ To-Do</h1>
                <span class="count"><!--OPEN--> open</span>
              </header>

              <main>
                <form class="add" action="/add" method="post">
                  <input type="text" name="title" placeholder="What needs doing?" maxlength="280" required autofocus>
                  <button type="submit">Add</button>
                </form>

                <!--SOURCE-->

                <ul class="list"><!--ROWS--></ul>
              </main>
            </body>
            </html>
            """;
}
