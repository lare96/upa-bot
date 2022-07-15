package me.upa.sql;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import me.upa.UpaBot;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;

public class SqlConnectionManager {
    private static final Logger logger = LogManager.getLogger();

    private final ListeningExecutorService pool = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()/2));
    private final SqlConnectionPool connectionPool;

    public Connection take() throws SQLException {
        return connectionPool.take();
    }

    public <V> ListenableFuture<V> execute(SqlTask<V> task) {
        return pool.submit(task);
    }

    public <V> void execute(SqlTask<V> task, Consumer<V> success) {
        execute(task, success, logger::catching);
    }

    public <V> void execute(SqlTask<V> task, Consumer<V> success, Consumer<Throwable> failure) {
        Futures.addCallback(execute(task), new FutureCallback<>() {
            @Override
            public void onSuccess(V result) {
                success.accept(result);
            }

            @Override
            public void onFailure(Throwable t) {
                failure.accept(t);
            }
        }, pool);
    }

    private static final SqlConnectionManager instance = new SqlConnectionManager();

    public static SqlConnectionManager getInstance() {
        return instance;
    }

    private SqlConnectionManager() {
        SqlConnectionPool connectionPoolTemp;
        try {
            connectionPoolTemp = new SqlConnectionPool.Builder()
                    .poolName("UpaData")
                    .database("upa")
                    .build();
        } catch (SQLException e) {
           logger.error("Could not start SQL connection pool.", e);
            connectionPoolTemp = null;
        }
        connectionPool = connectionPoolTemp;
    }
}
