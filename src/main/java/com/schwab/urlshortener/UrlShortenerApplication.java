package com.schwab.urlshortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class UrlShortenerApplication {
    public static void main(String[] args) {
        boolean cliMode = isCliMode(args);
        SpringApplication application = new SpringApplication(UrlShortenerApplication.class);
        if (cliMode) {
            application.setWebApplicationType(WebApplicationType.NONE);
        }
        ConfigurableApplicationContext context = application.run(args);
        if (cliMode) {
            context.close();
        }
    }

    static boolean isCliMode(String[] args) {
        return java.util.Arrays.stream(args)
                .anyMatch(arg -> "--metrics-report".equals(arg) || arg.startsWith("--scenario="));
    }
}
