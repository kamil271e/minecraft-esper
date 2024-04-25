package com.example.bigdata;

import com.espertech.esper.common.client.EPCompiled;
import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.client.configuration.Configuration;
import com.espertech.esper.compiler.client.CompilerArguments;
import com.espertech.esper.compiler.client.EPCompileException;
import com.espertech.esper.compiler.client.EPCompiler;
import com.espertech.esper.compiler.client.EPCompilerProvider;
import com.espertech.esper.runtime.client.*;
import net.datafaker.Faker;
import net.datafaker.fileformats.Format;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

public class EsperClient {
    public static void main(String[] args) throws InterruptedException {
        int noOfRecordsPerSec;
        int howLongInSec;

        if (args.length < 2) {
            noOfRecordsPerSec = 2;
            howLongInSec = 10;
        } else {
            noOfRecordsPerSec = Integer.parseInt(args[0]);
            howLongInSec = Integer.parseInt(args[1]);
        }

        Configuration config = new Configuration();
        String sampleQuery = """
            @name('answer') SELECT ore, depth, amount, ets, its
                  from MinecraftEvent#ext_timed(java.sql.Timestamp.valueOf(its).getTime(), 60 sec)
            """;
        String task1Query = """
            @name('answer') SELECT ore, sum(amount) as sumAmount
                  from MinecraftEvent#ext_timed(java.sql.Timestamp.valueOf(its).getTime(), 60 sec) GROUP BY ore
            """;
        String task2Query = """
            @name('answer') SELECT amount, depth, ets, its
                  from MinecraftEvent(amount > 6 AND depth > 12 AND ore='diamond');
            """;
        String task3Query = """
            @name('answer') SELECT ore, amount, depth, ets, its
                  FROM MinecraftEvent#ext_timed(java.sql.Timestamp.valueOf(its).getTime(), 60 sec) 
                  GROUP BY ore
                  HAVING amount >= 1.5 * AVG(amount)
            """;

        String task4Query = """
            @name('answer')
            SELECT hll.ore, SUM(hv.amount) as sumAmountHeaven, SUM(hll.amount) as sumAmountHell
            FROM MinecraftEvent(depth < 10)#ext_timed(java.sql.Timestamp.valueOf(its).getTime(), 60 sec) AS hll,
                MinecraftEvent(depth > 20)#ext_timed(java.sql.Timestamp.valueOf(its).getTime(), 60 sec) AS hv
            WHERE hv.ore = hll.ore
            GROUP BY hll.ore;
        """;

        String task5Query = """
             @name('answer') select s[0].ore as ore, s[0].depth as depth, s[0].amount as amount, 
             s[0].its as startEts, e.its as endEts from
             pattern[ every (s=MinecraftEvent until e=MinecraftEvent(amount > 5 and ore = 'diamond')
             where timer:within(30 seconds))];
        """;

//        String task6Query = """
//            @name('answer') SELECT *
//            FROM MinecraftEvent
//            MATCH_RECOGNIZE (
//                PARTITION BY ore
//                MEASURES
//                    e1.ore AS ore
//                    e1.amount AS amount1,
//                    LAST(e2.amount) AS amount2,
//                    LAST(e3.amount) AS amount3,
//                PATTERN (e1 e2 e3)
//                DEFINE
//                    e1 as e1.amount > 5,
//                    e2 AS e2.amount ! PREV(e1.amount),
//                    e3 AS e3.amount > PREV(e2.amount)
//            )
//        """;

//        String task6Query = """
//                @name('answer') select e1.ore, e2.ore, e3.ore, e1.amount, e2.amount, e3.amount, e1.its, e2.its, e3.its
//                from pattern [ every e1=MinecraftEvent(amount>5) -> ( e2=MinecraftEvent(ore=e1.ore and amount > e1.amount) and not MinecraftEvent(ore!=e1.ore or amount <= e1.amount) ) -> ( e3=MinecraftEvent(ore=e2.ore and amount > e2.amount) and not MinecraftEvent(ore!=e2.ore or amount <= e2.amount) ) ];
//                """;

        String task6Query = """
                @name('answer') select ore, amount1, amount2, amount3 from MinecraftEvent
                                match_recognize (
                                measures
                                    f.ore as ore,
                                    f.amount as amount1,
                                    s.amount as amount2,
                                    t.amount as amount3
                                after match skip to next row
                                pattern ( x f s t )
                                define
                                     f as f.amount > 5,
                                     s as s.amount > f.amount,
                                     t as t.amount > s.amount
                                )
                """;

        String task7Query = """
                @name('answer') select startIts, endIts, sumAmount
                                from MinecraftEvent
                                match_recognize (
                                measures
                                    f1.amount + f2.amount + f3.amount + s1.amount + s2.amount + s3.amount as sumAmount,
                                    f1.its as startIts,
                                    s3.its as endIts
                                pattern (x f1 f2 f3 y* s1 s2 s3)
                                define
                                    f1 as f1.ore != prev(f1.ore),
                                    f2 as f2.ore != f1.ore,
                                    f3 as (f3.ore != f2.ore and f3.ore != f1.ore),
                                    s1 as s1.ore != prev(s1.ore),
                                    s2 as s2.ore != s1.ore,
                                    s3 as (s3.ore != s2.ore and s3.ore != s1.ore)
                                )
            """;


        EPCompiled epCompiled = getEPCompiled(config, task6Query);

        // Connect to the EPRuntime server and deploy the statement
        EPRuntime runtime = EPRuntimeProvider.getRuntime("http://localhost:port", config);
        EPDeployment deployment;
        try {
            deployment = runtime.getDeploymentService().deploy(epCompiled);
        }
        catch (EPDeployException ex) {
            // handle exception here
            throw new RuntimeException(ex);
        }

        EPStatement resultStatement = runtime.getDeploymentService().getStatement(deployment.getDeploymentId(), "answer");

        // Add a listener to the statement to handle incoming events
        resultStatement.addListener( (newData, oldData, stmt, runTime) -> {
            for (EventBean eventBean : newData) {
                System.out.printf("R: %s%n", eventBean.getUnderlying());
            }
        });

        taskRunner(1000, 120, runtime);

    }

    private static EPCompiled getEPCompiled(Configuration config, String query) {
        CompilerArguments compilerArgs = new CompilerArguments(config);
        String jsonSchema = "@public @buseventtype create json schema MinecraftEvent(ore string, depth int, amount int, ets string, its string);";
        // Compile the EPL statement
        EPCompiler compiler = EPCompilerProvider.getCompiler();
        EPCompiled epCompiled;
        try {
            epCompiled = compiler.compile(jsonSchema + query, compilerArgs);
        }
        catch (EPCompileException ex) {
            // handle exception here
            throw new RuntimeException(ex);
        }
        return epCompiled;
    }

    static void waitToEpoch() throws InterruptedException {
        long millis = System.currentTimeMillis();
        Instant instant = Instant.ofEpochMilli(millis) ;
        Instant instantTruncated = instant.truncatedTo( ChronoUnit.SECONDS ) ;
        long millis2 = instantTruncated.toEpochMilli() ;
        TimeUnit.MILLISECONDS.sleep(millis2+1000-millis);
    }

    public static void taskRunner(int noOfRecordsPerSec, int howLongInSec, EPRuntime runtime) throws InterruptedException {
        Faker faker = new Faker();
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() < startTime + (1000L * howLongInSec)) {
            waitToEpoch();
            for (int i = 0; i < noOfRecordsPerSec; i++) {
                Timestamp eTimestamp = faker.date().past(60, TimeUnit.SECONDS);
                eTimestamp.setNanos(0);
                Timestamp iTimestamp = Timestamp.valueOf(LocalDateTime.now().withNano(0));
                String[] ores = {"coal", "iron", "gold", "diamond", "emerald"};
                String record = Format.toJson()
                        .set("ore", () -> ores[faker.number().numberBetween(0, ores.length)])
                        .set("depth", () -> String.valueOf(faker.number().numberBetween(1, 36)))
                        .set("amount", () -> String.valueOf(faker.number().numberBetween(1, 10)))
                        .set("ets", eTimestamp::toString)
                        .set("its", iTimestamp::toString)
                        .build().generate();
                runtime.getEventService().sendEventJson(record, "MinecraftEvent");
            }
        }
    }
}

