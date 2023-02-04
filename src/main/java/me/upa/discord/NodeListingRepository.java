package me.upa.discord;

import com.google.common.collect.ImmutableList;
import me.upa.game.Node;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public final class NodeListingRepository implements Serializable {

    private static final long serialVersionUID = -668003631101740662L;
    private final List<NodeListing> listings = new CopyOnWriteArrayList<>();

    public void add(NodeListing listing) {
        listings.add(listing);
    }

    public NodeListing get(int index) {
        return listings.get(index);
    }

    public void remove(int index) {
        listings.remove(index);
    }

    public int size() {
        return listings.size();
    }
    public List<NodeListing> getAllForNode(Node node) {
        return listings.stream().filter(listing -> listing.getNode() == node).collect(Collectors.toList());
    }

    public List<NodeListing> getAll() {
        return listings;
    }
}
