package org.test

import groovy.json.JsonBuilder
import groovy.transform.CompileStatic
import com.qmetric.spark.authentication.AuthenticationDetails
import com.qmetric.spark.authentication.BasicAuthenticationFilter
import groovy.json.JsonSlurper
import groovy.sql.Sql
import groovy.text.SimpleTemplateEngine
import groovy.time.TimeCategory
import groovy.time.TimeDuration
import groovy.util.logging.Slf4j
import spark.Request

import static spark.Spark.*

@Slf4j
class Main {

    static final void main(String[] args) {
        State state = new State()
        try {
            log.info("Starting app at port: $Config.PORT for $Config.APP_USERNAME")
            init(state)
            initDBConnection(state)
            healhCheck(state)
            createServer(state)
        } catch (Throwable e) {
            log.error("ERROR", e)
            state.db.close()
            System.exit(-1)
        }
    }

    private static final void init(State state) {
        log.info("Loading script for db from $Config.SCRIPT_PATH")
        state.createScriptBody = new File("$Config.SCRIPT_PATH/create_db.sql").getText("UTF-8")
        state.dropScriptBody = new File("$Config.SCRIPT_PATH/drop_db.sql").getText("UTF-8")
        state.mapper = new JsonSlurper()
    }

    private static final void initDBConnection(State state) {
        log.info("Creating db services")
        DB db = new Oracle(state)
        Sql sql = db.init()

        state.sql = sql
        state.db = db
    }

    private static final void healhCheck(State state) {
        int healthCounter = 1
        new Timer().schedule({
            state.db.ping(healthCounter++)
        } as TimerTask, 1000, Config.HEALTH_CHECK * 1000)

        int renewCounter = 1
        new Timer().schedule({
            state.db.renew(renewCounter++)
        } as TimerTask, Config.RENEW * 1000, Config.RENEW * 1000)
    }

    private static final void createServer(State state) {
        port(Config.PORT)

        before(new BasicAuthenticationFilter("/create-db",
                new AuthenticationDetails(Config.APP_USERNAME, Config.APP_PASSWORD)))

        before("/*", { request, response ->
            log.info("Received api call from {}, body {}", request.ip(), request.body())
        })

        post("/create-db", { request, response ->
            List<String> queries = Util.generateQueries(state, request, State.Type.CREATE)
            return Util.measure('create') {
                state.db.executeQueries(queries)
            }
        })

        post("/drop-db", { request, response ->
            List<String> queries = Util.generateQueries(state, request, State.Type.DROP)
            return Util.measure('drop') {
                state.db.executeQueries(queries)
            }
        })

        get("/health", { request, response ->
            return "OK"
        })

        exception(Throwable.class, { e, request, response ->
            log.error("ERROR", e)

            def output = [:]
            output.request = request.body()
            output.exception = Util.chain(e)

            response.status(500)
            response.body(new JsonBuilder(output).toString())
        })
    }

    private static interface DB {

        Sql init()

        void executeQueries(List queries)

        void ping(int counter)

        void renew(int counter)

        void close()

    }

    private static final class Oracle implements DB {

        private final Main.State state

        Oracle(State state) {
            log.info("Creating oracle db service")
            this.state = state
        }

        @Override
        Sql init() {
            log.info("Initializing connection to db with url: $Config.DB_URL, user: $Config.DB_USERNAME")
            def sql = Sql.newInstance(Config.DB_URL, Config.DB_USERNAME, Config.DB_PASSWORD, Config.DB_DRIVER)
            def firstRow = sql.firstRow("SELECT 1 as result FROM DUAL")
            if(firstRow.result == 1) {
                log.info("Got success response from db, connection established")
            }

            return sql
        }

        @Override
        void executeQueries(List queries) {
            log.info("Prepare to execute ${queries.size()} statements")
            queries.each { query ->
                state.sql.execute(query)
            }
            log.info("Executed ${queries.size()} statements")
        }

        @Override
        void ping(int counter) {
            state.sql.eachRow('SELECT sysdate as result FROM dual') { row ->
                println "Hello from db number #$counter: $row.result"
            }
        }

        @Override
        void renew(int counter) {
            log.info("Renewal connection to db")
            state.sql.close()
            state.sql = init()
        }

        @Override
        void close() {
            log.info("Closing connection to db")
            state.sql.close()
        }

    }

    @CompileStatic
    private static final class State {

        DB db
        Sql sql
        String createScriptBody
        String dropScriptBody
        JsonSlurper mapper

        String byType(State.Type type) {
            switch (type) {
                case Type.CREATE: return createScriptBody
                case Type.DROP: return dropScriptBody
            }

            throw new IllegalStateException("Unknown type")
        }

        static final enum Type {

            CREATE,
            DROP

        }

    }

    @CompileStatic
    private static final class Config {

        public static final Integer PORT = Integer.valueOf(System.getenv("PORT") ?: "8080")
        public static final String APP_USERNAME = System.getenv("APP_USERNAME") ?: "admin"
        public static final String APP_PASSWORD = System.getenv("APP_PASSWORD") ?: "admin"
        public static final Integer HEALTH_CHECK = Integer.valueOf(System.getenv("HEALTH_CHECK") ?: "30")
        public static final Integer RENEW = Integer.valueOf(System.getenv("RENEW") ?: "600")

        public static final String DB_URL = System.getenv("DB_URL") ?: "jdbc:oracle:thin:@localhost:1521:XE"
        public static final String DB_USERNAME = System.getenv("DB_USERNAME") ?: "scott"
        public static final String DB_PASSWORD = System.getenv("DB_PASSWORD") ?: "tiger"
        public static final String DB_DRIVER = System.getenv("DB_DRIVER") ?: "oracle.jdbc.driver.OracleDriver"

        public static final String SCRIPT_PATH = System.getenv("SCRIPT_PATH") ?: "/scripts"

    }

    private static final class Util {

        private static final TimeDuration measure(String name, Closure closure) {
            def timeStart = new Date()
            closure()
            def timeStop = new Date()
            TimeDuration duration = TimeCategory.minus(timeStop, timeStart)
            log.info("Operation $name took $duration")

            duration
        }

        private static final String chain(Throwable throwable) {
            List<String> result = new ArrayList<String>()
            while (throwable != null && throwable.getMessage() != null) {
                result.add(throwable.getMessage())
                throwable = throwable.getCause()
            }

            result.join(" -> ")
        }

        private static final List<String> generateQueries(State state, Request request, State.Type type) {
            String body = Objects.requireNonNull(request.body(), "Request body can't be null, url: ${request.url()}")
            def json = state.mapper.parseText(body)
            def engine = new SimpleTemplateEngine()
            def template = engine.createTemplate(state.byType(type))
            def binding = [ DATABASE_NAME: json.databaseName ]
            def queries = (template.make(binding).toString()
                    .replaceAll(";\n", ";")
                    .split(';') as List<String>)
                    .findAll { !it.startsWith("--") }
            queries
        }

    }

}
