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
                  from MinecraftEvent#ext_timed(java.sql.Timestamp.valueOf(its).getTime(), 3 sec)
            """;
        String task1Query = """
            @name('answer') SELECT ore, sum(amount) as sumAmount
                  from MinecraftEvent#ext_timed(java.sql.Timestamp.valueOf(its).getTime(), 60 sec) GROUP BY ore
            """;
        String task2Query = """
            @name('answer') SELECT amount, depth, ets, its
                  from MinecraftEvent#ext_timed(java.sql.Timestamp.valueOf(its).getTime(), 3 sec) 
                  WHERE amount > 6 AND depth > 12 AND ore='diamond'
            """;
        String task3Query = """
            @name('answer') SELECT ore, amount, depth, ets, its
                  FROM MinecraftEvent#ext_timed(java.sql.Timestamp.valueOf(its).getTime(), 60 sec) 
                  GROUP BY ore
                  HAVING amount >= 1.5 * AVG(amount)
            """;

        EPCompiled epCompiled = getEPCompiled(config, task3Query);

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

        taskRunner(2, 120, runtime);

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

