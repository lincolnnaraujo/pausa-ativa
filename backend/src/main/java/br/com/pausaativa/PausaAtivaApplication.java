package br.com.pausaativa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PausaAtivaApplication {

    public static void main(String[] args) {
        SpringApplication.run(PausaAtivaApplication.class, args);
    }
}
