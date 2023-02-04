package me.upa.service;

import me.upa.UpaBotContext;
import me.upa.discord.NodeListing;
import me.upa.discord.NodeListingRepository;
import me.upa.discord.UpaMember;
import me.upa.discord.UpaProperty;
import me.upa.fetcher.ProfileDataFetcher;
import me.upa.fetcher.PropertyDataFetcher;
import me.upa.game.Profile;
import me.upa.game.Property;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class ListingRemovalMicroService extends MicroService {
    private final UpaBotContext ctx;

    public ListingRemovalMicroService(UpaBotContext ctx) {
        super(Duration.ofMinutes(30));
        this.ctx = ctx;
    }

    @Override
    public void run() throws Exception {
        ctx.variables().listings().accessValue(listings -> {
            boolean removed = false;
            List<NodeListing> all = listings.getAll();
            for(int index = 0; index < all.size(); index++) {
                NodeListing listing = all.get(index);
                UpaProperty property = ctx.databaseCaching().getProperties().get(listing.getPropertyId());
                UpaMember upaMember = ctx.databaseCaching().getMembers().get(listing.getMemberId());
                if (property != null && upaMember != null && property.getMemberKey() == upaMember.getKey()) {
                    continue;
                }
                all.remove(index);
                ctx.variables().fastListings().accessValue(fastListings -> {
                    fastListings.remove(listing.getPropertyId());
                    return false;
                });
                removed = true;
            }
            if(removed){
                ctx.variables().fastListings().save();
            }
            return removed;
        });
    }
}
