package me.upa.service;

import com.google.common.util.concurrent.AbstractScheduledService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class MicroServiceProcessor extends AbstractScheduledService {
    private static final Logger logger = LogManager.getLogger();

    private final List<MicroService> microServices = new ArrayList<>();

    @Override
    protected void startUp() throws Exception {
        for(MicroService service : microServices) {
            service.startUp();
        }
    }

    @Override
    protected void runOneIteration() throws Exception {
        for(MicroService service : microServices) {
            try {
                service.handleRun();
            } catch(Exception e) {
               logger.catching(e);
            }
        }
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedDelaySchedule(1, 1, TimeUnit.MINUTES);
    }

    public void addService(MicroService ms) {
        microServices.add(ms);
    }
}
