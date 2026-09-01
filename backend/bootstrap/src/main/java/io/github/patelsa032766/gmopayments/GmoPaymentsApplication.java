package io.github.patelsa032766.gmopayments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Process entry point. Business behavior lives in domain and application modules. */
@SpringBootApplication
public class GmoPaymentsApplication {
    public static void main(String[] args) {
        SpringApplication.run(GmoPaymentsApplication.class, args);
    }
}
