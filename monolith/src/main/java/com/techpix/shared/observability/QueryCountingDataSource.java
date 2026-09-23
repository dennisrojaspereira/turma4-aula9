package com.techpix.shared.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Envolve o DataSource real e intercepta cada execução de Statement.
 * <p>
 * Não altera nenhuma consulta. Apenas conta. Sem dependência extra: proxies dinâmicos do JDK.
 */
public class QueryCountingDataSource extends DelegatingDataSource {

    private static final Set<String> EXECUTE_METHODS = Set.of(
            "execute", "executeQuery", "executeUpdate", "executeLargeUpdate", "executeBatch", "executeLargeBatch");
    private static final Set<String> STATEMENT_FACTORIES = Set.of("createStatement", "prepareStatement", "prepareCall");

    private final Counter total;

    public QueryCountingDataSource(DataSource target, MeterRegistry registry) {
        super(target);
        this.total = Counter.builder("db.queries.total")
                .description("Total de instrucoes SQL executadas pela aplicacao")
                .register(registry);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrap(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrap(super.getConnection(username, password));
    }

    private Connection wrap(Connection connection) {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                new ConnectionHandler(connection));
    }

    private final class ConnectionHandler implements InvocationHandler {
        private final Connection target;

        ConnectionHandler(Connection target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = invokeTarget(target, method, args);
            if (STATEMENT_FACTORIES.contains(method.getName()) && result instanceof Statement statement) {
                Class<?>[] interfaces = {method.getReturnType()};
                return Proxy.newProxyInstance(getClass().getClassLoader(), interfaces, new StatementHandler(statement));
            }
            return result;
        }
    }

    private final class StatementHandler implements InvocationHandler {
        private final Statement target;

        StatementHandler(Statement target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (EXECUTE_METHODS.contains(method.getName())) {
                QueryCounter.increment();
                total.increment();
            }
            return invokeTarget(target, method, args);
        }
    }

    private static Object invokeTarget(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
