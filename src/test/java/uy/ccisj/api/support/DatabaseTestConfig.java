package uy.ccisj.api.support;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

public final class DatabaseTestConfig {
    private DatabaseTestConfig() {}

    public static DriverManagerDataSource dataSource(String schema) {
        String url = System.getProperty("testJdbcUrl");
        return new DriverManagerDataSource(
                url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema + ",public",
                System.getProperty("testJdbcUser", "ccisj_test"),
                System.getProperty("testJdbcPassword", "ccisj_test"));
    }
}
