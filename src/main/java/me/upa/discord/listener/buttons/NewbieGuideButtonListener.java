package me.upa.discord.listener.buttons;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.jetbrains.annotations.NotNull;

public class NewbieGuideButtonListener extends ListenerAdapter {

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getButton().getId() == null)
            return;
        switch (event.getButton().getId()) {
            case "visitor_guide":
                event.reply(new MessageCreateBuilder().
                        addContent("__**< 10,000 UPX Networth**__\n").
                        addContent("Make sure you are in Queens as right now as that is the best city in terms of profit for a new player. Use this tool if you don't know how to get there https://upland.travel/route-directory/\n\n").
                        addContent("Next thing you want to learn is what properties to buy. You will follow this until you achieve 100,000 networth. Go to https://upxland.me/properties/?status=unlocked&fsa_option=fsa&sort_by=mint_price&sort_dir=asc&city=4 \n").
                        addContent("You will see that the \"city\" is \"Queens,\" the \"status\" is \"Unlocked\", the \"FSA Option\" is \"Only FSA\" and it's sorted in order of the cheapest mint. You want to buy as many of the cheapest ones as you can so you can flip for the highest amount of profit later.\n\n").
                        addContent("So you've bought some properties, what now? The main thing you want to focus on is getting sends on your properties. The most efficient way to do this is by going to the <#975506360231948288> channel and becoming a scholar.\n\n").
                        addContent("If you're willing to make an investment to skip the visitor stage, I recommend anywhere between $10-50. You do not want your networth to go over 100,000 yet as you will not be able to mint FSA properties. Make sure you always level up during spark week.").build()).setEphemeral(true).queue();
                break;
            case "uplander_guide":
                event.reply(new MessageCreateBuilder().
                        addContent("__**< 100,000 UPX Networth**__\n").
                        addContent("Now that you're an Uplander you can finally sell all those cheap mints. Go to https://upxland.me/properties/statistics/?city=4\n\n").
                        addContent("The main thing you want to focus on for now is \"Floor Price (UPX),\" That is around the price that you can flip your cheap mints for. If it's lower than the floor, it'll sell faster and vice versa. But we can't use statistics for the whole city of Queens! Some properties are located in areas that are apart of collections, have a lot of completed structures, have a very small amount of properties or hold some sort of sentimental value. You also want to buy cheap FSA's in these areas if you can, because you will make even more money when you flip.\n\n").
                        addContent("In order to check what neighbourhood/collection its in, go to https://upxland.me/users/ and enter your username. Find the property you want to sell and take note of the neighborhood it's in, OR if it's any in a non-standard (non-blue) collections. Make sure you ignore the \"City pro\" purple collection as well.\n\n").
                        addContent("Go back to the statistics page, and input the non-standard collection OR the neighborhood. Now you have the UPX floor price for your property and know exactly what to sell for! Once you've reached around 30-50k networth, it's a good idea to go to the <#966799964703428608> channel and make sure you're on the right track.\n\n").
                        addContent("You should also consider joining our spark train (see <#956799638260830238>) so you can get structures built on your properties. \n").build()).setEphemeral(true).queue();

                break;
            case "pro_guide":
                event.reply(new MessageCreateBuilder().
                        addContent("__**< 1,000,000 Networth**__\n").
                        addContent("At this point, the possibilities for increasing your networth really start to open up. These are not limited to\n").
                        addContent("- Treasure hunting\n").
                        addContent("- Secondary UPX/USD market flipping\n").
                        addContent("- Slumlording\n").
                        addContent("- Loaning spark/flipping houses/neighborhood construction (nodes)\n").
                        addContent("- Flipping legits/block explorers, starting your own metaventure").build()).setEphemeral(true).queue();
                break;
            case "more_help":
                event.reply(new MessageCreateBuilder().
                        addContent("__**Advisors**__\n").
                        addContent("Please use these tags in <#956790034097373204>\n").
                        addContent("<@&978148612339019877> for treasure hunting advice\n").
                        addContent("<@&982486805192507412> for property flipping advice\n").
                        addContent("<@&982504114107842620> for buy n' hold earnings advice\n\n").build()
                ).setEphemeral(true).queue();
                break;
        }
    }
}
