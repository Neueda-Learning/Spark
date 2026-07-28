package com.portfolio.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class JdbcAiAssistantRepositoryTest {

    @Mock
    private JdbcTemplate jdbc;

    @Test
    void reusesResolvedSchemaMetadataUntilAQueryFails() {
        AtomicInteger metadataQueries = new AtomicInteger();
        AtomicInteger cashQueries = new AtomicInteger();

        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            Object[] arguments = invocation.getArguments();
            Object[] parameters;
            if (arguments.length == 3 && arguments[2] instanceof Object[] array) {
                parameters = array;
            } else {
                parameters = java.util.Arrays.copyOfRange(arguments, 2, arguments.length);
            }
            if (sql.contains("information_schema.tables")) {
                metadataQueries.incrementAndGet();
                return "portfolio".equals(parameters[0])
                        ? List.of("portfolio")
                        : List.of();
            }
            if (sql.contains("information_schema.columns")) {
                metadataQueries.incrementAndGet();
                String candidate = String.valueOf(parameters[1]);
                return List.of(candidate);
            }
            if (sql.contains("AS cash_balance")) {
                if (cashQueries.incrementAndGet() == 2) {
                    throw new DataAccessResourceFailureException("schema changed");
                }
                return List.of(BigDecimal.TEN);
            }
            throw new AssertionError("Unexpected SQL: " + sql);
        }).when(jdbc).query(
                anyString(),
                any(RowMapper.class),
                any(Object[].class)
        );

        JdbcAiAssistantRepository repository = new JdbcAiAssistantRepository(jdbc);

        assertThat(repository.findPortfolioCashBalance(1L)).contains(BigDecimal.TEN);
        assertThat(repository.findPortfolioCashBalance(1L)).isEmpty();
        assertThat(repository.findPortfolioCashBalance(1L)).contains(BigDecimal.TEN);
        assertThat(metadataQueries).hasValue(6);
        assertThat(cashQueries).hasValue(3);
    }
}
