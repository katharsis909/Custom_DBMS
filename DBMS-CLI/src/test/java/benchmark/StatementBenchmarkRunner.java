package benchmark;

import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.CommandLineOptionException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public final class StatementBenchmarkRunner {
    private StatementBenchmarkRunner() {
    }

    public static void main(String[] args) throws RunnerException, CommandLineOptionException {
        Options options;
        if (args.length > 0) {
            options = new OptionsBuilder()
                    .parent(new CommandLineOptions(args))
                    .forks(0)
                    .build();
        } else {
            options = new OptionsBuilder()
                    .include(StatementBenchmark.class.getSimpleName())
                    .shouldDoGC(true)
                    .forks(0)
                    .build();
        }

        new Runner(options).run();
    }
}
