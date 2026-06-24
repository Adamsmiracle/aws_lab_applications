package com.example.demo;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;


import java.util.List;
import java.util.Set;

@Controller
public class GalleryController {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final PhotoRepository repo;
    private final S3ImageService images;
    private final AppSettings settings;

    
    public GalleryController(PhotoRepository repo, S3ImageService images, AppSettings settings) {
        this.repo = repo;
        this.images = images;
        this.settings = settings;
    }

    /** Gallery UI. Doubles as the ALB health check (returns 200 even when empty). */
    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String gallery(@RequestParam(value = "error", required = false) String error) {
        List<Photo> photos = repo.findAllNewestFirst();

        StringBuilder cards = new StringBuilder();
        for (Photo p : photos) {
            String url = "https://" + settings.getCloudFrontDomain() + "/" + p.s3Key();
            String desc = escape(p.description());
            long id = p.id();
            cards.append("<figure class='card'>")
                 .append("<div class='thumb'><img loading='lazy' src='").append(url)
                 .append("' alt='").append(desc).append("' data-desc='").append(desc).append("'></div>")
                 .append("<div class='actions'>")
                 .append("<form class='edit' action='/update' method='post'>")
                 .append("<input type='hidden' name='id' value='").append(id).append("'>")
                 .append("<input type='text' name='description' value='").append(desc)
                 .append("' maxlength='280' placeholder='Add a description…'>")
                 .append("<button type='submit' title='Save description'>Save</button>")
                 .append("</form>")
                 .append("<form class='del' action='/delete' method='post' onsubmit=\"return confirm('Delete this photo?')\">")
                 .append("<input type='hidden' name='id' value='").append(id).append("'>")
                 .append("<button type='submit' class='danger' title='Delete photo'>Delete</button>")
                 .append("</form>")
                 .append("</div>")
                 .append("</figure>");
        }
        String body = photos.isEmpty()
                ? "<p class='empty'>No photos yet — upload the first one above. ✨</p>"
                : cards.toString();

        String banner = "type".equals(error)
                ? "<div class='banner error'>Unsupported image type. Please upload JPEG, PNG, WebP, or GIF — HEIC (iPhone) isn't displayable in browsers.</div>"
                : "";

        return PAGE.replace("<!--CARDS-->", body)
                   .replace("<!--COUNT-->", String.valueOf(photos.size()))
                   .replace("<!--BANNER-->", banner);
    }

    /** Upload: validate type, image bytes -> S3, description -> RDS, back to gallery. */
    @PostMapping("/upload")
    public String upload(@RequestParam("image") MultipartFile image,
                         @RequestParam(value = "description", required = false) String description)
            throws Exception {
        if (image != null && !image.isEmpty()) {
            String ct = image.getContentType();
            if (ct == null || !ALLOWED_TYPES.contains(ct.toLowerCase())) {
                return "redirect:/?error=type";   // reject HEIC and anything non-renderable
            }
            String key = images.upload(image);
            repo.save(key, description == null ? "" : description);
        }
        return "redirect:/";
    }

    /** Edit a photo's description. */
    @PostMapping("/update")
    public String update(@RequestParam("id") long id,
                         @RequestParam(value = "description", required = false) String description) {
        repo.updateDescription(id, description == null ? "" : description);
        return "redirect:/";
    }

    /** Delete a photo: remove the S3 object, then the metadata row. */
    @PostMapping("/delete")
    public String delete(@RequestParam("id") long id) {
        Photo p = repo.findById(id);
        if (p != null) {
            images.delete(p.s3Key());
            repo.delete(id);
        }
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
              <title>Photo Gallery</title>
              <style>
                :root {
                  --bg: #0b0e1f; --panel: #161a33; --panel-2: #1d2244;
                  --text: #ececf5; --muted: #8a8fc0; --line: #2a2f55; --accent: #6b7bff;
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
                .banner { max-width: 1100px; margin: 1rem auto 0; padding: .8rem 1rem;
                          border-radius: 10px; font-size: .92rem; }
                .banner.error { background: #3a1d2a; border: 1px solid #7d2b46; color: #ffd6e3; }
                .upload {
                  display: flex; gap: .6rem; flex-wrap: wrap; align-items: center;
                  max-width: 1100px; margin: 1.25rem auto 0; padding: 1rem 1.25rem;
                  background: var(--panel); border: 1px solid var(--line); border-radius: 14px;
                }
                .upload input[type=text] {
                  flex: 1 1 240px; padding: .65rem .85rem; border-radius: 10px;
                  border: 1px solid var(--line); background: var(--bg); color: var(--text);
                }
                .upload input[type=text]:focus { outline: 2px solid var(--accent); border-color: transparent; }
                .upload input[type=file] { color: var(--muted); font-size: .9rem; }
                .upload button {
                  padding: .65rem 1.4rem; border: 0; border-radius: 10px; cursor: pointer;
                  background: linear-gradient(135deg, #6b7bff, #8a63ff); color: #fff; font-weight: 700;
                  transition: transform .12s ease, filter .12s ease;
                }
                .upload button:hover { filter: brightness(1.08); transform: translateY(-1px); }
                .preview {
                  max-width: 1100px; margin: .8rem auto 0; padding: .8rem 1rem;
                  display: flex; align-items: center; gap: .9rem;
                  background: var(--panel); border: 1px dashed var(--line); border-radius: 12px;
                }
                .preview[hidden] { display: none; }
                .preview img { width: 88px; height: 88px; object-fit: cover; border-radius: 10px;
                               border: 1px solid var(--line); }
                .preview .meta { font-size: .9rem; color: var(--muted); }
                .grid {
                  display: grid; gap: 1.1rem; max-width: 1100px; margin: 1.25rem auto; padding: 0 1.25rem 3rem;
                  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
                }
                .card {
                  margin: 0; background: var(--panel); border: 1px solid var(--line);
                  border-radius: 14px; overflow: hidden;
                  transition: transform .16s ease, box-shadow .16s ease, border-color .16s ease;
                }
                .card:hover { transform: translateY(-4px); box-shadow: 0 12px 30px rgba(0,0,0,.45);
                              border-color: var(--accent); }
                .thumb { overflow: hidden; }
                .card img {
                  width: 100%; height: 210px; object-fit: cover; display: block; cursor: zoom-in;
                  transition: transform .35s ease;
                }
                .card:hover img { transform: scale(1.06); }
                .actions { padding: .65rem .7rem; display: flex; flex-direction: column; gap: .5rem; }
                .edit { display: flex; gap: .4rem; }
                .edit input[type=text] {
                  flex: 1; min-width: 0; padding: .42rem .55rem; border-radius: 8px;
                  border: 1px solid var(--line); background: var(--bg); color: var(--text); font-size: .85rem;
                }
                .actions button {
                  padding: .42rem .75rem; border: 0; border-radius: 8px; cursor: pointer;
                  font-size: .8rem; font-weight: 600; background: var(--panel-2); color: var(--text);
                }
                .actions button:hover { filter: brightness(1.18); }
                .actions .danger { background: #4a1f2d; color: #ffb3c6; width: 100%; }
                .actions .danger:hover { background: #6e2a40; }
                .empty { text-align: center; color: var(--muted); padding: 4rem 1rem; grid-column: 1 / -1;
                         font-size: 1.05rem; }

                /* Lightbox */
                .lightbox {
                  position: fixed; inset: 0; z-index: 50; display: flex; flex-direction: column;
                  align-items: center; justify-content: center; gap: 1rem; padding: 2rem;
                  background: rgba(5,7,18,.86); backdrop-filter: blur(6px);
                  animation: fade .18s ease; cursor: zoom-out;
                }
                .lightbox[hidden] { display: none; }
                .lightbox img {
                  max-width: min(92vw, 1100px); max-height: 78vh; border-radius: 12px;
                  box-shadow: 0 20px 60px rgba(0,0,0,.6); cursor: default;
                }
                .lightbox figcaption { color: var(--text); max-width: 80vw; text-align: center; font-size: 1rem; }
                .lb-close {
                  position: absolute; top: 1rem; right: 1.25rem; font-size: 2rem; line-height: 1;
                  color: #fff; cursor: pointer; opacity: .8;
                }
                .lb-close:hover { opacity: 1; }
                .lb-nav {
                  position: absolute; top: 50%; transform: translateY(-50%);
                  background: rgba(255,255,255,.12); border: 0; color: #fff;
                  font-size: 3rem; line-height: 1; padding: .4rem 1rem;
                  border-radius: 10px; cursor: pointer; transition: background .15s ease;
                }
                .lb-nav:hover { background: rgba(255,255,255,.25); }
                .lb-prev { left: 1.25rem; }
                .lb-next { right: 1.25rem; }
                @keyframes fade { from { opacity: 0 } to { opacity: 1 } }
              </style>
            </head>
            <body>
              <header>
                <h1>📸 Photo Gallery</h1>
                <span class="count"><!--COUNT--> photos</span>
              </header>

              <!--BANNER-->

              <form class="upload" action="/upload" method="post" enctype="multipart/form-data">
                <input type="file" id="file" name="image"
                       accept="image/jpeg,image/png,image/webp,image/gif" required>
                <input type="text" name="description" placeholder="Add a description…" maxlength="280">
                <button type="submit">Upload</button>
              </form>

              <!-- Client-side preview of the selected file, before uploading -->
              <div id="preview" class="preview" hidden>
                <img id="preview-img" alt="preview">
                <span class="meta" id="preview-meta"></span>
              </div>

              <main class="grid"><!--CARDS--></main>

              <div id="lightbox" class="lightbox" hidden>
                <span class="lb-close" aria-label="Close">&times;</span>
                <button class="lb-nav lb-prev" aria-label="Previous">&#8249;</button>
                <img id="lb-img" src="" alt="">
                <button class="lb-nav lb-next" aria-label="Next">&#8250;</button>
                <figcaption id="lb-cap"></figcaption>
              </div>

              <script>
                // --- Preview before upload ---
                (function () {
                  var input = document.getElementById("file");
                  var box = document.getElementById("preview");
                  var img = document.getElementById("preview-img");
                  var meta = document.getElementById("preview-meta");
                  input.addEventListener("change", function () {
                    var f = input.files && input.files[0];
                    if (!f) { box.hidden = true; return; }
                    if (img.src) URL.revokeObjectURL(img.src);
                    img.src = URL.createObjectURL(f);
                    meta.textContent = f.name + " — " + Math.round(f.size / 1024) + " KB";
                    box.hidden = false;
                  });
                })();

                // --- Lightbox with prev/next navigation ---
                (function () {
                  var lb     = document.getElementById("lightbox");
                  var lbImg  = document.getElementById("lb-img");
                  var lbCap  = document.getElementById("lb-cap");
                  var lbPrev = document.querySelector(".lb-prev");
                  var lbNext = document.querySelector(".lb-next");
                  var slides = [];
                  var current = 0;

                  function buildSlides() {
                    slides = Array.from(document.querySelectorAll(".grid .card img")).map(function (img) {
                      return { src: img.src, cap: img.getAttribute("data-desc") || "" };
                    });
                  }

                  function show(index) {
                    current = (index + slides.length) % slides.length;
                    lbImg.src = slides[current].src;
                    lbCap.textContent = slides[current].cap;
                    var single = slides.length <= 1;
                    lbPrev.hidden = single;
                    lbNext.hidden = single;
                  }

                  function open(index) {
                    buildSlides();
                    show(index);
                    lb.hidden = false;
                    document.body.style.overflow = "hidden";
                  }

                  function close() {
                    lb.hidden = true; lbImg.src = ""; document.body.style.overflow = "";
                  }

                  document.querySelector(".grid").addEventListener("click", function (e) {
                    var img = e.target.closest(".card img");
                    if (!img) return;
                    open(Array.from(document.querySelectorAll(".grid .card img")).indexOf(img));
                  });

                  lb.addEventListener("click", function (e) { if (e.target === lb) close(); });
                  document.querySelector(".lb-close").addEventListener("click", function (e) {
                    e.stopPropagation(); close();
                  });
                  lbImg.addEventListener("click", function (e) { e.stopPropagation(); });
                  lbPrev.addEventListener("click", function (e) { e.stopPropagation(); show(current - 1); });
                  lbNext.addEventListener("click", function (e) { e.stopPropagation(); show(current + 1); });

                  document.addEventListener("keydown", function (e) {
                    if (lb.hidden) return;
                    if (e.key === "Escape")     close();
                    if (e.key === "ArrowLeft")  show(current - 1);
                    if (e.key === "ArrowRight") show(current + 1);
                  });
                })();
              </script>
            </body>
            </html>
            """;
}
