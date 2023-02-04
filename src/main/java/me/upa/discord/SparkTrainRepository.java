package me.upa.discord;

import me.upa.service.SparkTrainMicroService.SparkTrainMember;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SparkTrainRepository implements Serializable {

    private static final long serialVersionUID = 7617243290700656562L;

    private final List<SparkTrainMember> hollisTrain = new CopyOnWriteArrayList<>();
    private final List<SparkTrainMember> globalTrain = new CopyOnWriteArrayList<>();
    private volatile String sparkTrainHollis;
    private volatile String sparkTrainGlobal;
    private volatile String partialSparkTrainHollis;
    private volatile String partialSparkTrainGlobal;
    public boolean update(SortedSet<SparkTrainMember> members, String fullTrain, String partialTrain, boolean global) {
        if(global) {
            globalTrain.clear();
            globalTrain.addAll(members);
            sparkTrainGlobal = fullTrain;
            partialSparkTrainGlobal = partialTrain;
        } else {
            hollisTrain.clear();
            hollisTrain.addAll(members);
            sparkTrainHollis = fullTrain;
            partialSparkTrainHollis = partialTrain;
        }
        return true;
    }

    public List<SparkTrainMember> getHollisTrain() {
        return hollisTrain;
    }

    public List<SparkTrainMember> getGlobalTrain() {
        return globalTrain;
    }

    public String getSparkTrainGlobal() {
        return sparkTrainGlobal;
    }

    public String getSparkTrainHollis() {
        return sparkTrainHollis;
    }

    public String getPartialSparkTrainGlobal() {
        return partialSparkTrainGlobal;
    }

    public String getPartialSparkTrainHollis() {
        return partialSparkTrainHollis;
    }
}
