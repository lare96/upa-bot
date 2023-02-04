package me.upa.discord;

import com.google.common.collect.ImmutableList;
import me.upa.UpaBotContext;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class UpaInformationRepository implements Serializable {
    private static final long serialVersionUID = 2673979459408714135L;


    public enum UpaInformationType {
        INFO_GETTING_STARTED {
            @Override
            public String getOriginal() {
                return "__**Creating an account\n**__" +
                        "1. Click on \"Create Account\" below\n" +
                        "2. Follow the steps in order to register\n" +
                        "3. Verify that the registration was successful by using /account\n\n" +
                        "Once you are registered you will receive the <@&999013596111581284> role and your property data will begin synchronizing. I will also assign you the <@&956793551230996502> role if you own at least 1 property in Hollis, which entitles you to even more benefits.\n\n" +
                        "__**Learning the basics\n**__" +
                        "Now that you're a <@&999013596111581284>\n" +
                        "- You can use the command '/pac daily' to claim your dividend every 24h\n" +
                        "- You can use the command '/account' to see an overview of your UPA account\n" +
                        "- You can go <#1000173221326364722> to purchase additional PAC and redeem your existing PAC for rewards\n" +
                        "- You can participate in events and giveaways held by the UPA staff team!\n\n" +
                        "All members can join the <#1026633109367685131> spark train and if you are a <@&956793551230996502>, you can join the <#963108957784772659> spark train which offers more benefits. Our spark trains have 0 UPX fees and no minimum spark requirement.";
            }
        },
        INFO_JOINING_THE_SPARK_TRAIN {
            @Override
            public String getOriginal() {
                return "Stake with us and receive a build on your property at no UPX cost! " +
                        "Once you have accrued enough Spark Share Hours (SSH), all of our members will contribute spark to your build!\n\n" +
                        "__**To get started\n**__" +
                        "If you are a <@&999013596111581284> and want to join the global spark train, submit a build request and stake your spark at the most recent building in <#1026633109367685131>\n" +
                        "IF you are a <@&956793551230996502> and want to join the hollis spark train, do the same thing but for the building in this channel <#963108957784772659>\n\n" +
                        "Once you have staked, that's it! After a few minutes you are automatically registered to the spark train and can use the buttons to either submit a build request, view your place in the queue, or view the queue in its entirety.\n" +
                        "As your structure gets built, you will lose SSH. This means you will have to continue staking and/or earning PAC in order to get another build.";
            }
        },
        INFO_UNDERSTANDING_PAC {
            @Override
            public String getOriginal() {
                return "null";
            }
        },
        INFO_VIDEOS {
            @Override
            public String getOriginal() {
                return "null";
            }
        },
        HOLLIS_QUEENS_GUIDELINES {
            @Override
            public String getOriginal() {
                return "null";
            }
        },
        GLOBAL_GUIDELINES {
            @Override
            public String getOriginal() {
                return "null";
            }
        };

        public abstract String getOriginal();

        public static final ImmutableList<UpaInformationType> ALL = ImmutableList.copyOf(values());
    }

    private transient final UpaBotContext ctx;

    public UpaInformationRepository(UpaBotContext ctx) {
        this.ctx = ctx;
    }

    private final Map<UpaInformationType, String> informationMap = new ConcurrentHashMap<>();

    public void update(UpaInformationType type, String newText) {
        informationMap.put(type, newText);
    }

    public String get(UpaInformationType type) {
        return informationMap.get(type);
    }

    public void resetAll() {
        for (UpaInformationType type : UpaInformationType.ALL) {
            informationMap.put(type, type.getOriginal());
        }
    }
}
