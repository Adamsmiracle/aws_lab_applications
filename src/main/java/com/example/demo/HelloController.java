package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;


@RestController
public class HelloController {

    public HelloController() {
    }
    
@GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String returnHtml() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Service Status</title>
                <style>
                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        background-color: #f4f7f6;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        height: 100vh;
                        margin: 0;
                    }
                    .card {
                        background: white;
                        padding: 2rem;
                        border-radius: 12px;
                        box-shadow: 0 4px 6px rgba(0,0,0,0.1);
                        text-align: center;
                        max-width: 400px;
                    }
                    .status-icon {
                        font-size: 3rem;
                        color: #2ecc71;
                        margin-bottom: 1rem;
                    }
                    h1 { color: #2c3e50; margin: 0; }
                    p { color: #7f8c8d; line-height: 1.6; }
                    .btn {
                        display: inline-block;
                        margin-top: 1.5rem;
                        padding: 10px 20px;
                        background-color: #3498db;
                        color: white;
                        text-decoration: none;
                        border-radius: 5px;
                        transition: background 0.3s;
                    }
                    .btn:hover { background-color: #2980b9; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h1>NAME: Adams Miracle lab revision v2</h1>
                    <h2>STATUS: SUCCESS</h2>
                    <h2>Tag test</h2>
                    <h3>LAB NAME: ECS CI/CD</h3>
                </div>
            </body>
            </html>
            """;
    }

}
