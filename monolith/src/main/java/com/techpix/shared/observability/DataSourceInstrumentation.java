package com.techpix.shared.observability;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Troca o DataSource do Spring pelo DataSource que conta consultas.
 * O HikariCP continua por baixo, com suas métricas de pool intactas: o Spring Boot
 * desembrulha {@code DelegatingDataSource} ao registrar {@code hikaricp.*}.
 */
@Configuration
public class DataSourceInstrumentation {

    @Bean
    static BeanPostProcessor queryCountingDataSourcePostProcessor(ObjectProvider<MeterRegistry> registry) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof HikariDataSource hikari) {
                    return new QueryCountingDataSource(hikari, registry.getObject());
                }
                return bean;
            }
        };
    }
}
