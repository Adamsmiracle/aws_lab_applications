package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.net.InetAddress;
import java.net.UnknownHostException;

@SpringBootApplication
public class QRCodeApplication {

	private static final Logger log = LoggerFactory.getLogger(QRCodeApplication.class);

	@Value("${server.port:8080}")
	private String serverPort;

	public static void main(String[] args) {
		SpringApplication.run(QRCodeApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onReady() {
		String hostname;
		try {
			hostname = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			hostname = "unknown";
		}
		log.info("===========================================");
		log.info("  QRCODE is running");
		log.info("  Port     : {}", serverPort);
		log.info("  Hostname : {}", hostname);
		log.info("  URL      : http://{}:{}", hostname, serverPort);
		log.info("===========================================");
	}
}
