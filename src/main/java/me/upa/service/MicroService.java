package me.upa.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;

/**
 * A low-priority service that does not rely on its own thread, with a higher chance of delayed/late execution. This is used to
 * reduce resource usage.
 *
 * @author lare96
 */
public abstract class MicroService {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The intended delay between executions.
     */
    private final long delayMinutes;

    /**
     * The current delay. Only intended to be used in {@link MicroServiceProcessor}.
     */
    private int currentDelay;

    /**
     * Creates a new {@link MicroService}.
     *
     * @param duration The duration in-between executions.
     */
    public MicroService(Duration duration) {
        long suppliedMinutes = duration.toMinutes();
        if (suppliedMinutes < 1) {
            delayMinutes = 1;
        } else {
            delayMinutes = suppliedMinutes;
        }
    }

    /**
     * Determines if this service is ready to execute and handles the execution if so.
     */
    final void handleRun() {
        try {
            if (++currentDelay >= delayMinutes) {
                run();
                currentDelay = 0;
            }
        } catch (Exception e) {
            logger.error("Error running service in INSTANT mode.", e);
        }
    }

    /**
     * Called when the microservice is first loaded (not executed).
     */
    public void startUp() throws Exception {

    }

    /**
     * Run the main logic for this service.
     */
    public abstract void run() throws Exception;

    /**
     * @return The current delay.
     */
    int getCurrentDelay() {
        return currentDelay;
    }

    /**
     * @return The execution delay minutes.
     */
    public long getDelayMinutes() {
        return delayMinutes;
    }
}
