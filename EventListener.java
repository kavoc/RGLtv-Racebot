import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageUpdateEvent;
import sx.blah.discord.handle.obj.*;

/**
 * Copyright 2018 kavoc
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

public class EventListener {
    private Racebot racebot;

    public EventListener(Racebot rb){
        racebot = rb;
    }

    @EventSubscriber
    public void onMessageReceived(MessageReceivedEvent e) {
        handleMessage(e.getGuild(), e.getChannel(), e.getAuthor(), e.getMessage());
    }

    public void handleMessage(IGuild guild, IChannel channel, IUser author, IMessage im){
        String message = im.getContent();
        String mesL = message.toLowerCase();
        boolean botCommander = false;

        for (IRole role : author.getRolesForGuild(guild)){
            if (role.getName().equalsIgnoreCase("bot commander")) botCommander = true;
        }

        if (mesL.startsWith(".race "))
        {
            racebot.raceCommands(im, message, botCommander);
        }
        else if (mesL.equals(".races"))
        {
            racebot.listRaces(channel);
        }
        else if (mesL.startsWith(".start"))
        {
            if (!racebot.restrictedMode || botCommander){
                racebot.starts(channel, message, botCommander);
            } else {
                channel.sendMessage("Currently in tournament mode.  Only authorized users may start races.");
            }


        }
        else if (mesL.equals(".rbhelp"))
        {
            rbHelp(channel);
        }
        else if (mesL.equals(".rbadmin"))
        {
            rbAdmin(channel);
        }
        else if (mesL.equals(".join") || mesL.equals(".enter") || mesL.equals(".j"))
        {
            racebot.joins(author, channel);
        }
        else if (mesL.equals(".unjoin") || mesL.equals(".leave"))
        {
            racebot.unjoins(author, channel);
        }
        else if (mesL.startsWith(".runner"))
        {
            racebot.runners(im, message);
        }
        else if (mesL.equals(".ready") || mesL.equals(".r"))
        {
            racebot.readies(author, channel);
        }
        else if (mesL.equals(".unready"))
        {
            racebot.unreadies(author, channel);
        }
        else if (mesL.startsWith(".done"))
        {
            racebot.dones(author, channel);
        }
        else if (mesL.equals(".undone"))
        {
            racebot.undones(author, channel);
        }
        else if (mesL.equals(".forfeit") || mesL.equals(".quit"))
        {
            racebot.forfeits(author, channel);
        }
        else if (mesL.equals(".forcefinish") && botCommander)
        {
            racebot.finishes(channel);
        }
        else if (mesL.startsWith(".comment"))
        {
            while (Racebot.softBlocking || Racebot.hardBlocking){
                try {
                    Thread.sleep(100);
                } catch (InterruptedException err) {
                    err.printStackTrace();
                }
            }
            channel.sendMessage("Comments not supported.  Write your excuses in your own diary.");
        }
        else if (mesL.startsWith(".time"))
        {
            racebot.times(channel);
        }
        else if (mesL.startsWith(".stream "))
        {
            racebot.streams(author, channel, message);
        }
        else if (mesL.startsWith(".multi"))
        {
            racebot.multis(channel, message);
        }
        else if (mesL.startsWith(".mode ") && botCommander){
            racebot.modes(channel, message);
        }
        else if (mesL.equals(".rbstatus")){
            while (Racebot.softBlocking || Racebot.hardBlocking){
                try {
                    Thread.sleep(100);
                } catch (InterruptedException err) {
                    err.printStackTrace();
                }
            }
            channel.sendMessage(racebot.rbStatus());
        }
        else if (mesL.equals(".register") && botCommander) {
            while (Racebot.softBlocking || Racebot.hardBlocking){
                try {
                    Thread.sleep(100);
                } catch (InterruptedException err) {
                    err.printStackTrace();
                }
            }
            channel.sendMessage("Registering race channel");
            racebot.registerChannel(channel);
        }
        else if (mesL.startsWith(".shortcut ") && botCommander) {
            racebot.addShortcut(channel, message);
        }
        else if (mesL.equals(".ping")) {
            while (Racebot.softBlocking || Racebot.hardBlocking){
                try {
                    Thread.sleep(100);
                } catch (InterruptedException err) {
                    err.printStackTrace();
                }
            }
            channel.sendMessage(".pong");
        }
    }

    @EventSubscriber
    public void onMessageUpdate(MessageUpdateEvent e){
        handleMessage(e.getGuild(), e.getChannel(), e.getAuthor(), e.getNewMessage());
    }

    public void rbHelp(IChannel channel){
        while (Racebot.softBlocking || Racebot.hardBlocking){
            try {
                Thread.sleep(100);
            } catch (InterruptedException err) {
                err.printStackTrace();
            }
        }
        channel.sendMessage("**.race new *name*, *mode*** - creates a new race"
                +"\n**.race info *#*** - retrieves information about the indicated race)" +
                ""+"\n**.races** - lists the active races and their numbers" +
                ""+"\n**.start *#*** - starts the indicated race (-f required when not all readies recieved" +
                ""+"\n**.join *#***  - joins the numbered race" +
                ""+"\n**.unjoin** - leaves the race you are registered in" +
                ""+"\n**.ready** - indicates you are ready to race" +
                ""+"\n**.unready** - indicates you are no longer ready to race" +
                ""+"\n**.done** - indicates completion of a race" +
                ""+"\n**.undone** - indicates your done was a mistake" +
                ""+"\n**.forfeit** - indicates that you wish to forfeit" +
                ""+"\n**.stream *twitch_name*** - adds a reference to your twitch stream for kadgar links (not required)");

    }

    public void rbAdmin(IChannel channel){
        while (Racebot.softBlocking || Racebot.hardBlocking){
            try {
                Thread.sleep(100);
            } catch (InterruptedException err) {
                err.printStackTrace();
            }
        }
        channel.sendMessage("**.forcefinish** - Forfeits all remaining racers in the race and finishes the race."
                +"\n**.race remove  *#*** - removes the indicated race from the list of active races"
                +"\n**.start -f** - Force starts a race where runners have not readied."
                +"\n**.register** - Registers channel as a race channel."
                +"\n**.mode *(normal or tournament)*** - Sets the current mode of the racebot.  Tournament mode prevents non-admins from starting races.");

    }

}
