package com.cauverystore;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class
})
@EnableCaching
@EnableScheduling
public class CauveryStoreApplication {
    public static void main(String[] args) {
        // The store sells in India, but the server runs on UTC. Times are saved without a zone
        // (LocalDateTime.now()), so an order placed at 2:30 pm showed as 9:00 am, and an order
        // between midnight and 5:30 am got the previous day's invoice date. Run on Indian time.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(CauveryStoreApplication.class, args);
    }
}
