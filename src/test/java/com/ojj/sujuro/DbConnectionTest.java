package com.ojj.sujuro;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DbConnectionTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void connect() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            assertThat(conn.isValid(2)).isTrue();
            System.out.println("DB 연결 성공: " + conn.getMetaData().getURL()
                    + " / " + conn.getMetaData().getUserName());
        }
    }
}