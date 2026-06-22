package com.example.demo;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class GalleryController {

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
    public String gallery() {
        StringBuilder cards = new StringBuilder();
        for (Photo p : repo.findAllNewestFirst()) {
            String url = "https://" + settings.getCloudFrontDomain() + "/" + p.s3Key();
            cards.append("<figure class='card'>")
                 .append("<img loading='lazy' src='").append(url).append("' alt='")
                 .append(escape(p.description())).append("'>")
                 .append("<figcaption>").append(escape(p.description())).append("</figcaption>")
                 .append("</figure>");
        }
        if (cards.length() == 0) {
            cards.append("<p class='empty'>No photos yet — upload the first one!</p>");
        }
        return PAGE.replace("<!--CARDS-->", cards.toString());
    }

    /** Handle an upload: image bytes -> S3, description -> RDS, then back to the gallery. */
    @PostMapping("/upload")
    public String upload(@RequestParam("image") MultipartFile image,
                         @RequestParam(value = "description", required = false) String description)
            throws Exception {
        if (image != null && !image.isEmpty()) {
            String key = images.upload(image);
            repo.save(key, description == null ? "" : description);
        }
        return "redirect:/";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static final String PAGE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Photo Gallery</title>
              <style>
                * { box-sizing: border-box; }
                body { font-family: 'Segoe UI', system-ui, sans-serif; margin: 0;
                       background: #0f1226; color: #e8e8f0; }
                header { padding: 1.5rem; text-align: center; background: #171a33; }
                header h1 { margin: 0; font-weight: 600; }
                .upload { display: flex; gap: .5rem; flex-wrap: wrap; justify-content: center;
                          padding: 1rem; background: #14172b; }
                .upload input[type=text] { flex: 1 1 240px; padding: .6rem .8rem; border-radius: 8px;
                          border: 1px solid #2a2f55; background: #0f1226; color: #e8e8f0; }
                .upload input[type=file] { color: #b9bce0; }
                .upload button { padding: .6rem 1.2rem; border: 0; border-radius: 8px;
                          background: #5468ff; color: #fff; font-weight: 600; cursor: pointer; }
                .upload button:hover { background: #3f53e6; }
                .grid { display: grid; gap: 1rem; padding: 1.5rem;
                        grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); }
                .card { margin: 0; background: #171a33; border-radius: 12px; overflow: hidden;
                        box-shadow: 0 4px 14px rgba(0,0,0,.3); }
                .card img { width: 100%; height: 200px; object-fit: cover; display: block; }
                .card figcaption { padding: .7rem .8rem; font-size: .9rem; color: #c7cae8; }
                .empty { text-align: center; color: #8c90bf; padding: 3rem; grid-column: 1 / -1; }
              </style>
            </head>
            <body>
              <header><h1>📸 Photo Gallery</h1></header>
              <form class="upload" action="/upload" method="post" enctype="multipart/form-data">
                <input type="file" name="image" accept="image/*" required>
                <input type="text" name="description" placeholder="Add a description…" maxlength="280">
                <button type="submit">Upload</button>
              </form>
              <main class="grid"><!--CARDS--></main>
            </body>
            </html>
            """;
}
