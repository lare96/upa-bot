package me.upa.discord.event;

import me.upa.UpaBotContext;
import me.upa.service.MicroService;

import java.time.Duration;
import java.time.Instant;

public final class UpaEventMicroService extends MicroService {

    private final UpaBotContext ctx;

    public UpaEventMicroService(UpaBotContext ctx) {
        super(Duration.ofMinutes(5));
        this.ctx = ctx;
    }

    @Override
    public void startUp() throws Exception {
        ctx.variables().event().accessValue(event -> {
            event.start(ctx);
            return true;
        });
    }

    @Override
    public void run() throws Exception {
       ctx.variables().event().accessValue(event -> {
           if (event.getHandler() != null) {
               Instant now = Instant.now();
               Instant end = event.getEndAfter();
               if (now.isAfter(end)) {
                   event.end(ctx);
               } else {
                   event.getHandler().onLoop(ctx);
               }
               return true;
           }
           return false;
       });
    }
}
