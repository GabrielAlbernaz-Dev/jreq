package com.jreq.request.infrastructure.persistence;

import com.jreq.request.application.CookieRepository;
import com.jreq.request.domain.CookieExpiration;
import com.jreq.request.domain.StoredCookie;
import com.jreq.shared.database.JdbcTransactionManager;
import com.jreq.shared.database.SqliteConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class JdbcCookieRepository implements CookieRepository {
    private final SqliteConnectionFactory connectionFactory;
    private final JdbcTransactionManager transactionManager;

    public JdbcCookieRepository(
            SqliteConnectionFactory connectionFactory,
            JdbcTransactionManager transactionManager
    ) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
    }

    @Override
    public List<StoredCookie> findAll(Instant now) {
        Objects.requireNonNull(now, "now");
        String sql = """
                SELECT id, cookie_name, cookie_value, domain, cookie_path,
                       host_only, secure, http_only, expires_at, created_at, updated_at
                FROM stored_cookie
                ORDER BY domain, cookie_path, cookie_name
                """;
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                List<StoredCookie> cookies = new ArrayList<>();
                while (resultSet.next()) {
                    StoredCookie cookie = map(resultSet);
                    if (!cookie.isExpiredAt(now)) {
                        cookies.add(cookie);
                    }
                }
                return List.copyOf(cookies);
            }
        } catch (SQLException exception) {
            throw PersistenceExceptionMapper.database(exception, "Unable to load the cookie jar");
        }
    }

    @Override
    public void replaceAll(List<StoredCookie> cookies) {
        List<StoredCookie> persistent = Objects.requireNonNull(cookies, "cookies").stream()
                .filter(cookie -> !cookie.isSession())
                .toList();
        try {
            transactionManager.run(connection -> {
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM stored_cookie")) {
                    delete.executeUpdate();
                }
                insertAll(connection, persistent);
            });
        } catch (SQLException exception) {
            throw PersistenceExceptionMapper.database(exception, "Unable to save the cookie jar");
        }
    }

    private void insertAll(Connection connection, List<StoredCookie> cookies) throws SQLException {
        String sql = """
                INSERT INTO stored_cookie (
                    id, cookie_name, cookie_value, domain, cookie_path,
                    host_only, secure, http_only, expires_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (StoredCookie cookie : cookies) {
                CookieExpiration.At expiration = (CookieExpiration.At) cookie.expiration();
                statement.setString(1, cookie.id().toString());
                statement.setString(2, cookie.name());
                statement.setString(3, cookie.value());
                statement.setString(4, cookie.domain());
                statement.setString(5, cookie.path());
                statement.setInt(6, cookie.hostOnly() ? 1 : 0);
                statement.setInt(7, cookie.secure() ? 1 : 0);
                statement.setInt(8, cookie.httpOnly() ? 1 : 0);
                statement.setString(9, expiration.instant().toString());
                statement.setString(10, cookie.createdAt().toString());
                statement.setString(11, cookie.updatedAt().toString());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private StoredCookie map(ResultSet resultSet) throws SQLException {
        return new StoredCookie(
                UUID.fromString(resultSet.getString("id")),
                resultSet.getString("cookie_name"),
                resultSet.getString("cookie_value"),
                resultSet.getString("domain"),
                resultSet.getString("cookie_path"),
                resultSet.getInt("host_only") == 1,
                resultSet.getInt("secure") == 1,
                resultSet.getInt("http_only") == 1,
                CookieExpiration.at(Instant.parse(resultSet.getString("expires_at"))),
                Instant.parse(resultSet.getString("created_at")),
                Instant.parse(resultSet.getString("updated_at")));
    }
}
