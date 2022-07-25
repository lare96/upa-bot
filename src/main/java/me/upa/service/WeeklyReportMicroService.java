package me.upa.service;

import me.upa.UpaBotContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

public final class WeeklyReportMicroService extends MicroService{
    private static final Logger logger = LogManager.getLogger();

    private final UpaBotContext ctx;
    public WeeklyReportMicroService(UpaBotContext ctx) {
        super(Duration.ofMinutes(1));
        this.ctx = ctx;
    }

    @Override
    public void run() throws Exception {
        ctx.variables().reports().accessValue(reportData -> {
            try {
                reportData.process(ctx);
            } catch (Exception e) {
                logger.catching(e);
                return false;
            }
            return true;
        });
    }
}
