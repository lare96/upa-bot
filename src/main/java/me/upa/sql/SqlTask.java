package me.upa.sql;

import java.sql.Connection;
import java.util.concurrent.Callable;

public abstract class SqlTask<V> implements Callable<V> {

    @Override
    public final V call() throws Exception {
        try (var connection = SqlConnectionManager.getInstance().take()) {
            return execute(connection);
        }
    }

    public abstract V execute(Connection connection) throws Exception;
}
