import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventDispatcher;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.DiscordException;

import java.io.*;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

/**
 * Copyright 2018 kavoc
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

public class Racebot {
    public static final boolean debug = false; // flag to turn on debug messages
    public static final String rbVersion = "1.09"; //version
    public static FileWriter logger;  //used to log errors to a file

    private IDiscordClient client; //main discord client for interacting with discord
    private  String token = "";

    public static Connection database = null;  //database connection
    long racebotStartTime = 0; //when the racebot first came online

    private int nextRaceID = 1; //tracks the next available race id
    private HashMap<Integer, Race> races = new HashMap<Integer, Race>(); //map of races with their id
    private HashMap<Long, RaceGuild> raceGuilds = new HashMap<Long, RaceGuild>(); //map of servers and their ids

    public static boolean softBlocking = false; //used for blocking unimportant messages due to discord rate limiting
    public static boolean hardBlocking = false; //blocks all messages and race starts due to rate limiting

    static DateFormat dateFormat; //how dates should be formatted

    public boolean restrictedMode = false;  //used to block certain functions during tournaments (like preventing a participant from starting a race)

    public static void main(String[] args){
        new Racebot();
    }

    public Racebot() {
        dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm");
        File logFile = new File("racebot.log");
        try {
            logger = new FileWriter(logFile, true);
        } catch (IOException e) {
            e.printStackTrace();
        }

        log("Initializing");

        try {
            File iniFile = new File("racebot.ini");

            BufferedReader br = new BufferedReader(new FileReader(iniFile));

            String line;

            while ((line = br.readLine()) != null) {
                //Read the token in from the ini file
                if (line.startsWith("token")){
                    String[] tokenParts = line.split("=");
                    if (tokenParts.length > 1) token = tokenParts[1].trim();
                }
            }
        } catch (IOException ioErr){
            log("INI file not found or other read error");
            log(ioErr.getMessage());
        }

        if (token.equals("")){
            log("Token was not properly set.  Please ensure the 'racebot.ini' is present and that a valid 'token = (secret token)' field has been set");
            return;
        }


        SetupDB();

        client = createClient(token, true);
        EventDispatcher dispatcher = client.getDispatcher();
        dispatcher.registerListener(new EventListener(this));

        //wait until client is connected and ready
        while (!client.isReady()) try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        SetupRaceChannels();

        //Sets the status of the racebot in the member lists.  This will show racebot as "Watching casual races"
        client.changePresence(StatusType.ONLINE, ActivityType.WATCHING, "casual races");

        //Set the time when the racebot was turned on and set up
        racebotStartTime = System.currentTimeMillis();


    }

    //Defines the client and connects it
    public static IDiscordClient createClient(String token, boolean login) {
        ClientBuilder clientBuilder = new ClientBuilder();
        clientBuilder.withToken(token);
        try {
            if (login) {
                return clientBuilder.login();
            } else {
                return clientBuilder.build();
            }
        } catch (DiscordException e) {
            e.printStackTrace();
            log(e.getErrorMessage());
            return null;
        }
    }

    //Setting up databases
    //TODO database structure really could be improved a lot
    public void SetupDB(){
        try{
            database = DriverManager.getConnection("jdbc:sqlite:Racebot.sqlite");

            //new tables
            //race table
            String sql = "create table if not exists races (number INTEGER PRIMARY KEY, date TEXT NOT NULL, time TEXT NOT NULL, game TEXT NOT NULL, mode TEXT NOT NULL, casual BOOLEAN)";
            Statement statement = database.createStatement();
            statement.execute(sql);

            //streams table (connecting a racer with a twitch.tv url for kadgar links and whatnot)
            sql = "create table if not exists streams(name varchar(255) PRIMARY KEY, stream varchar(255) NOT NULL)";
            statement.execute(sql);

            //table translating a runner's discord name with a unique identifier based on their discord name
            sql = "create table if not exists runner_lookup(runner_key varchar(255) PRIMARY KEY, name varchar(255) NOT NULL)";
            statement.execute(sql);

            //table of race channels that we monitor
            sql = "create table if not exists race_channels(guild INTEGER, channel INTEGER UNIQUE)";
            statement.execute(sql);

            //read next available race id
            sql = "SELECT * FROM races ORDER BY number DESC LIMIT 1";
            ResultSet  results = statement.executeQuery(sql);

            while (results.next()){
                nextRaceID = results.getInt("number")+1;
                if (debug) System.out.println("Next race ID = "+nextRaceID);
            }

        } catch (SQLException e) {
            System.out.println(e.getMessage());
            log(e.getMessage());
        }

    }

    // Sets up race channels by reading the race channel database
    public void SetupRaceChannels(){
        String sql = "SELECT * FROM race_channels";

        try {
            Statement statement = database.createStatement();
            ResultSet rs = statement.executeQuery(sql);
            while (rs.next()){
                long gID = rs.getLong("guild");
                IChannel chan = client.getChannelByID(rs.getLong("channel"));

                //TODO remove channel from DB if it has been deleted
                if (chan !=null) {
                    if (raceGuilds.containsKey(gID)) {
                        raceGuilds.get(gID).addChannel(chan);
                    } else {
                        RaceGuild rg = new RaceGuild();
                        raceGuilds.put(gID, rg);
                        raceGuilds.get(gID).addChannel(client.getChannelByID(rs.getLong("channel")));
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            log(e.getMessage());
        }

    }

    //.race command was received, handle it
    public void raceCommands(IMessage im, String message, boolean botCommander){
        //Divide commands into a max of 3 parts
        String[] commands = message.split(" ", 3);

        //flag used if a mention occurs inside a command
        boolean ated = false;


        if (commands.length > 1){
            if ((commands[1].equalsIgnoreCase("new") || commands[1].equalsIgnoreCase("casual"))  && commands.length > 2) {
                boolean casual = false;
                if (commands[1].equalsIgnoreCase("casual")) casual = true;

                //@ indicates mentions.  Flag this as containing a mention then strip the first @ and everything after.  Raw text of a discord mention is actually <@username...
                if (message.contains("<@")) {
                    message = message.split("<@")[0];
                    commands = message.split(" ", 3);
                    ated = true;
                }


                if (message.split(",").length > 1) {
                    String gameName = gameShortcuts(commands[2].split(",")[0].trim());
                    String modeName = modeShortcuts(commands[2].split(",")[1].trim());

                    int thisID = nextRaceID;
                    nextRaceID++;

                    IChannel chan = getChannel(im.getGuild(), im.getChannel());
                    if (chan != null) {
                        races.put(thisID, new Race(thisID, gameName, modeName, chan, casual, this));

                        while (Racebot.softBlocking || Racebot.hardBlocking){
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException err) {
                                err.printStackTrace();
                                log(err.getMessage());
                            }
                        }

                        im.getChannel().sendMessage("Race "+thisID+" created: "+gameName+", "+modeName);
                        chan.changeName(thisID + "_" + channelNameCleaner(gameName) + "_-_" + channelNameCleaner(modeName));

                        String ats = "";

                        if (ated){

                            for (IUser user : im.getMentions()){
                                    races.get(thisID).addRacer(user);
                                    ats += " "+user.mention();
                            }
                        }

                        chan.sendMessage("Race " + thisID + " - " + gameName + ", " + modeName + " - is happening here! " + ats);
                    } else {

                        while (Racebot.softBlocking || Racebot.hardBlocking){
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException err) {
                                err.printStackTrace();
                                log(err.getMessage());
                            }
                        }
                        im.getChannel().sendMessage("All race channels for this server are currently occupied.\nPlease wait for one to free, or have an admin register a new channel");
                    }

                }
            } else if (commands[1].equalsIgnoreCase("info")){
                if (commands.length > 2 && commands[2].replace("#","").matches("[0-9]+")){
                    int raceID = Integer.parseInt(commands[2].replace("#",""));
                    if (races.containsKey(raceID)){
                        while (Racebot.softBlocking || Racebot.hardBlocking){
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException err) {
                                err.printStackTrace();
                                log(err.getMessage());
                            }
                        }
                        im.getChannel().sendMessage(races.get(raceID).getInfo());
                    } else {
                        String sql = "SELECT * from races where number=" + raceID;
                        try {
                            Statement statement = database.createStatement();
                            ResultSet rs = statement.executeQuery(sql);

                            String infoResponse = "";
                            boolean result = false;

                            while (rs.next()){
                                result = true;
                                infoResponse = "Race "+rs.getInt("number")+ ": "+rs.getString("game") + ", " + rs.getString("mode") + " | Finalized\n"
                                + rs.getString("date") + " " + rs.getString("time") + "\n"
                                + "Participants:\n";
                            }

                            if (result){
                                sql = "SELECT * from race" + raceID + " ORDER BY time ASC";
                                rs = statement.executeQuery(sql);

                                while (rs.next()){
                                    String user = rs.getString("name");
                                    user = "**" + user.substring(0, user.length()-4) + "**" + user.substring(user.length()-4);
                                    if (rs.getLong("time") == Long.MAX_VALUE) infoResponse += user + " - Forfeit\n";
                                    else infoResponse += user + " - Finished - " + timeFormatter(rs.getLong("time")) + "\n";
                                }

                                im.getChannel().sendMessage(infoResponse);
                            } else {
                                im.getChannel().sendMessage("No race found with ID " + raceID);
                            }

                        } catch (SQLException e1) {
                            e1.printStackTrace();
                            log(e1.getMessage());
                        }

                    }
                } else if( commands.length == 2){
                    for (Integer i : races.keySet()) {
                        if (races.get(i).getChannel().getLongID() == im.getChannel().getLongID()) {
                            Race r = races.get(i);
                            while (Racebot.softBlocking || Racebot.hardBlocking){
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException err) {
                                    err.printStackTrace();
                                    log(err.getMessage());
                                }
                            }
                            im.getChannel().sendMessage(r.getInfo());
                        } else {
                            while (Racebot.softBlocking || Racebot.hardBlocking){
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException err) {
                                    err.printStackTrace();
                                    log(err.getMessage());
                                }
                            }
                            im.getChannel().sendMessage("No race currently assigned to this channel.");
                        }
                    }
                }
            } else if (commands[1].equalsIgnoreCase("remove")){
                if (botCommander) {
                    if (commands.length > 2 && commands[2].replace("#", "").matches("[0-9]+")) {
                        int raceID = Integer.parseInt(commands[2].replace("#", ""));
                        if (races.containsKey(raceID)) {
                            if (races.get(raceID).getState() == Race.State.Prerace) {
                                if (raceID == nextRaceID - 1) nextRaceID = raceID; // This was the last race created, safe to reuse the ID
                                IChannel chan = races.get(raceID).getChannel();
                                if (chan.getLongID() == 393230283937415189L) chan.changeName("openracechannel_inactive");
                                else chan.changeName("inactive_race_channel");
                                removeRace(raceID);

                            }

                            while (Racebot.softBlocking || Racebot.hardBlocking) {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException err) {
                                    err.printStackTrace();
                                    log(err.getMessage());
                                }
                            }
                            im.getChannel().sendMessage("Race "+raceID+" successfully removed.");
                        } else {
                            while (Racebot.softBlocking || Racebot.hardBlocking) {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException err) {
                                    err.printStackTrace();
                                    log(err.getMessage());
                                }
                            }
                            im.getChannel().sendMessage("No active race with id "+raceID+" found.");
                        }
                    }
                } else {
                    while (Racebot.softBlocking || Racebot.hardBlocking) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException err) {
                            err.printStackTrace();
                            log(err.getMessage());
                        }
                    }
                    im.getChannel().sendMessage("User not authorized to perform this action");
                }
            } else if (commands[1].equalsIgnoreCase("edit")) {
                if (botCommander){
                    commands = message.split(" ", 4);
                    if (commands.length > 2 && commands[2].replace("#", "").matches("[0-9]+")) {
                        int raceID = Integer.parseInt(commands[2].replace("#", ""));
                        if (races.containsKey(raceID)) {
                            Race thisRace = races.get(raceID);


                            if (commands.length == 4 && commands[3].contains(",")) {
                                String game = commands[3].split(",")[0].trim();
                                String mode = commands[3].split(",")[1].trim();
                                game = gameShortcuts(game);
                                mode = modeShortcuts(mode);

                                thisRace.editRace(game, mode);



                                while (Racebot.softBlocking || Racebot.hardBlocking) {
                                    try {
                                        Thread.sleep(100);
                                    } catch (InterruptedException err) {
                                        err.printStackTrace();
                                        log(err.getMessage());
                                    }
                                }
                                im.getChannel().sendMessage("Race " + raceID + " successfully modified.");
                            } else {
                                while (Racebot.softBlocking || Racebot.hardBlocking) {
                                    try {
                                        Thread.sleep(100);
                                    } catch (InterruptedException err) {
                                        err.printStackTrace();
                                        log(err.getMessage());
                                    }
                                }
                                im.getChannel().sendMessage("Malformed edit command.  Please type better.");
                            }
                        } else {
                            while (Racebot.softBlocking || Racebot.hardBlocking) {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException err) {
                                    err.printStackTrace();
                                    log(err.getMessage());
                                }
                            }
                            im.getChannel().sendMessage("No active race with id "+raceID+" found.");
                        }
                    }
                } else {
                    while (Racebot.softBlocking || Racebot.hardBlocking) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException err) {
                            err.printStackTrace();
                            log(err.getMessage());
                        }
                    }
                    im.getChannel().sendMessage("User not authorized to perform this action");
                }
            }
        }
    }

    public void joins(IUser user, IChannel channel){
        for (Integer i : races.keySet()){
            if (races.get(i).getChannel().getLongID() == channel.getLongID()){
                int returnCode = 0;
                if (races.get(i).getState() == Race.State.Prerace) {
                    returnCode = races.get(i).addRacer(user);

                    while (Racebot.softBlocking || Racebot.hardBlocking){
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException err) {
                            err.printStackTrace();
                            log(err.getMessage());
                        }
                    }

                    if (returnCode == 1) channel.sendMessage(boldUsername(user) + " is already entered into this race.");
                    else channel.sendMessage(boldUsername(user) + " has joined **Race "+races.get(i).getID()+"**");
                } else {
                    while (Racebot.softBlocking || Racebot.hardBlocking){
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException err) {
                            err.printStackTrace();
                            log(err.getMessage());
                        }
                    }

                    channel.sendMessage("Race "+races.get(i).getID()+" is not currently joinable.");
                }

            }
        }

    }

    public void unjoins(IUser user, IChannel channel){
        for (Integer i : races.keySet()){
            if (races.get(i).getChannel().getLongID() == channel.getLongID()){
                int returnCode = 0;
                if (races.get(i).getState() == Race.State.Prerace) {
                    returnCode = races.get(i).removeRacer(user);
                    while (Racebot.softBlocking || Racebot.hardBlocking){
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException err) {
                            err.printStackTrace();
                            log(err.getMessage());
                        }
                    }
                    if (returnCode == 1) channel.sendMessage(boldUsername(user) + " is not currently entered into this race.");
                    else channel.sendMessage(boldUsername(user) + " has left **Race "+races.get(i).getID()+"**");
                } else {
                    while (Racebot.softBlocking || Racebot.hardBlocking){
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException err) {
                            err.printStackTrace();
                            log(err.getMessage());
                        }
                    }
                    channel.sendMessage("Race "+races.get(i).getID()+" is not currently leaveable.");
                }

            }
        }
    }

    public void readies(IUser user, IChannel channel){
        for (Integer i : races.keySet()){
            if (races.get(i).getChannel().getLongID() == channel.getLongID()){
                int returnCode = 0;
                if (races.get(i).getState() == Race.State.Prerace) {
                    returnCode = races.get(i).readyRacer(user);
                    while (Racebot.softBlocking || Racebot.hardBlocking){
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException err) {
                            err.printStackTrace();
                            log(err.getMessage());
                        }
                    }
                    if (returnCode == 0) channel.sendMessage(boldUsername(user) + " is ready. "+races.get(i).unreadyCount()+" of "+races.get(i).getRacers().size()+" remaining.");
                    else if (returnCode == 2) channel.sendMessage(boldUsername(user) + " is already ready. "+races.get(i).unreadyCount()+" remaining.");
                    else channel.sendMessage(boldUsername(user) + " is not currently registered to **Race "+races.get(i).getID()+"**");
                } else {
                    while (Racebot.softBlocking || Racebot.hardBlocking){
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException err) {
                            err.printStackTrace();
                            log(err.getMessage());
                        }
                    }
                    channel.sendMessage("Race "+races.get(i).getID()+" is already "+races.get(i).getState()+".  Unable to ready.");
                }

            }
        }
    }

    public void unreadies(IUser user, IChannel channel){
        for (Integer i : races.keySet()){
            if (races.get(i).getChannel().getLongID() == channel.getLongID()){
                int returnCode = 0;
                if (races.get(i).getState() == Race.State.Prerace) {
                    returnCode = races.get(i).unreadyRacer(user);
                    while (Racebot.softBlocking || Racebot.hardBlocking){
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException err) {
                            err.printStackTrace();
                            log(err.getMessage());
                        }
                    }
                    if (returnCode == 0) channel.sendMessage(boldUsername(user) + " is no longer ready. "+races.get(i).unreadyCount()+" racers not ready.");
                    else if (returnCode == 2) channel.sendMessage(boldUsername(user) + " is already not ready. "+races.get(i).unreadyCount()+" racers not ready.");
                    else channel.sendMessage(boldUsername(user) + " is not currently registered to **Race "+races.get(i).getID()+"**");
                } else {
                    while (Racebot.softBlocking || Racebot.hardBlocking){
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException err) {
                            err.printStackTrace();
                            log(err.getMessage());
                        }
                    }
                    channel.sendMessage("Race "+races.get(i).getID()+" is already "+races.get(i).getState()+".  Unable to unready.");
                }

            }
        }
    }

    public void starts(IChannel channel, String message, boolean botCommander){
        boolean forced = false;
        String[] parts = message.split(" ");
        if (botCommander && parts.length > 1 && parts[1].equalsIgnoreCase("-f")) forced = true;

        for (Integer i : races.keySet()){
            if (races.get(i).getChannel().getLongID() == channel.getLongID()){
                Race r = races.get(i);
                if (r.getState() == Race.State.Prerace) {
                    if (r.unreadyCount() == 0){
                        r.start();
                    } else {
                        while (Racebot.softBlocking || Racebot.hardBlocking){
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException err) {
                                err.printStackTrace();
                                log(err.getMessage());
                            }
                        }
                        if (forced){
                            r.start();
                        } else {
                            if (r.unreadyCount() == 1) channel.sendMessage("There is " + r.unreadyCount() + " racer currently not ready.  Unable to start.");
                            else channel.sendMessage("There are " + r.unreadyCount() + " racers currently not ready.  Unable to start.");
                        }

                    }
                } else {
                    while (Racebot.softBlocking || Racebot.hardBlocking){
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException err) {
                            err.printStackTrace();
                            log(err.getMessage());
                        }
                    }
                    channel.sendMessage("Race "+r.getID()+" is already "+r.getState()+".  Unable to start.");
                }

            }
        }
    }

    public void dones(IUser user, IChannel channel){
        for (Integer i : races.keySet()){
            if (races.get(i).getChannel().getLongID() == channel.getLongID()){
                Race r = races.get(i);
                if (r.getState() == Race.State.In_Progress) {
                    String finishQuote = r.doneRacer(user);
                    while (Racebot.softBlocking || Racebot.hardBlocking){
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException err) {
                            err.printStackTrace();
                            log(err.getMessage());
                        }
                    }
                    if (finishQuote != null ) channel.sendMessage(finishQuote);
                    r.checkForRaceFinish();
                } else {
                    while (Racebot.softBlocking || Racebot.hardBlocking){
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException err) {
                            err.printStackTrace();
                            log(err.getMessage());
                        }
                    }
                    channel.sendMessage("Race "+races.get(i).getID()+" is not currently in progress.");
                }

            }
        }
    }

    public void undones(IUser user, IChannel channel){
        for (Integer i : races.keySet()){
            if (races.get(i).getChannel().getLongID() == channel.getLongID()){
                int returnCode = 0;
                Race r = races.get(i);
                if (r.getState() == Race.State.In_Progress || r.getState() == Race.State.Finished) {
                    returnCode = r.undoneRacer(user);
                    while (Racebot.softBlocking || Racebot.hardBlocking){
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException err) {
                            err.printStackTrace();
                            log(err.getMessage());
                        }
                    }
                    if (returnCode == 0) channel.sendMessage(boldUsername(user) + " is no longer done.");
                    else if (returnCode == 1) channel.sendMessage(boldUsername(user) + " is not registered to this race.");
                    else if (returnCode == 2) channel.sendMessage(boldUsername(user) + " has not yet finished.");
                } else {
                    while (Racebot.softBlocking || Racebot.hardBlocking){
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException err) {
                            err.printStackTrace();
                            log(err.getMessage());
                        }
                    }
                    channel.sendMessage("Race "+races.get(i).getID()+" is not currently in progress.");
                }

            }
        }
    }

    public void forfeits(IUser user, IChannel channel){
        for (Integer i : races.keySet()){
            if (races.get(i).getChannel().getLongID() == channel.getLongID()){
                int returnCode = 0;
                Race r = races.get(i);
                if (r.getState() == Race.State.In_Progress) {
                    returnCode = r.forfeitRacer(user);
                    while (Racebot.softBlocking || Racebot.hardBlocking){
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException err) {
                            err.printStackTrace();
                            log(err.getMessage());
                        }
                    }
                    if (returnCode == 0) channel.sendMessage(boldUsername(user) + " has forfeit.");
                    else if (returnCode == 1) channel.sendMessage(boldUsername(user) + " is not registered to this race.");
                    else if (returnCode == 2) channel.sendMessage(boldUsername(user) + " has already finished.");
                    else if (returnCode == 3) channel.sendMessage(boldUsername(user) + " has already forfeited.");
                    r.checkForRaceFinish();
                } else {
                    while (Racebot.softBlocking || Racebot.hardBlocking){
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException err) {
                            err.printStackTrace();
                            log(err.getMessage());
                        }
                    }
                    channel.sendMessage("Race "+races.get(i).getID()+" is not currently in progress.");
                }

            }
        }
    }

    public void finishes(IChannel channel){
        for (Integer i : races.keySet()){
            if (races.get(i).getChannel().getLongID() == channel.getLongID()){
                int returnCode = 0;
                Race r = races.get(i);
                if (r.getState() == Race.State.In_Progress) {
                    returnCode = r.forceFinish();

                    if (returnCode == 0){
                        while (Racebot.softBlocking || Racebot.hardBlocking){
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException err) {
                                err.printStackTrace();
                                log(err.getMessage());
                            }
                        }

                        channel.sendMessage(returnCode +" racers forcibly forfeit.");
                    }


                } else {
                    while (Racebot.softBlocking || Racebot.hardBlocking){
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException err) {
                            err.printStackTrace();
                            log(err.getMessage());
                        }
                    }
                    channel.sendMessage("Race "+races.get(i).getID()+" is not currently in progress.");
                }

            }
        }
    }

    public void runners(IMessage e, String message){
        String parts[] = message.split(" ");
        if (parts.length > 1){
            if (parts[1].contains("#")) parts[1] = parts[1].replace("#", "");
            String sql = "select * from runner_lookup where name=?";
            try {
                PreparedStatement ps = database.prepareStatement(sql);
                ps.setString(1, parts[1]);
                ResultSet rs = ps.executeQuery();

                String runnerKey = "";
                String runnerName = "";
                boolean exists = false;
                while (rs.next()){
                    runnerKey = rs.getString("runner_key");
                    runnerName = rs.getString("name");
                    exists = true;
                }

                if (exists){
                    String gameInfos = "";

                    sql = "select * from "+ runnerKey;
                    Statement statement = database.createStatement();

                    rs = statement.executeQuery(sql);

                    while (rs.next()){
                        if (rs.getLong("best_time") == Long.MAX_VALUE) gameInfos += rs.getString("gamemode") + ": Best time - none | Rating: " + rs.getInt("score") + "\n";
                        else gameInfos += rs.getString("gamemode") + ": Best time - " + timeFormatter(rs.getLong("best_time")) + " | Rating: " + rs.getInt("score") + "\n";
                    }

                    sql = "select stream from streams where name='" + runnerName + "'";
                    rs = statement.executeQuery(sql);
                    String stream = "";
                    while (rs.next()){
                        stream = " (stream: " + rs.getString("stream") + ")";
                    }

                    while (Racebot.softBlocking || Racebot.hardBlocking){
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException err) {
                            err.printStackTrace();
                            log(err.getMessage());
                        }
                    }
                    e.getChannel().sendMessage("Racer " + runnerName + stream + ":\n"+gameInfos);

                } else {
                    while (Racebot.softBlocking || Racebot.hardBlocking){
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException err) {
                            err.printStackTrace();
                            log(err.getMessage());
                        }
                    }

                    e.getChannel().sendMessage("No information found for " + parts[1]);
                }


            } catch (SQLException e1) {
                e1.printStackTrace();
                log(e1.getMessage());
            }
        } else {
            runners(e, ".runner "+e.getAuthor().getName()+e.getAuthor().getDiscriminator());
        }

    }

    public void listRaces(IChannel channel){
        if (races.size() > 0) {
            String raceList = "```";
            for (int i : races.keySet()) {
                Race r = races.get(i);
                raceList += "Race "+i+": "+r.getGame()+ " - "+ r.getMode() + " ("+r.getState()+")\n";
            }
            raceList += "```";

            while (Racebot.softBlocking || Racebot.hardBlocking){
                try {
                    Thread.sleep(100);
                } catch (InterruptedException err) {
                    err.printStackTrace();
                    log(err.getMessage());
                }
            }

            channel.sendMessage(raceList);
        } else {
            while (Racebot.softBlocking || Racebot.hardBlocking){
                try {
                    Thread.sleep(100);
                } catch (InterruptedException err) {
                    err.printStackTrace();
                    log(err.getMessage());
                }
            }
            channel.sendMessage("No currently active races.");
        }
    }

    public void registerChannel(IChannel channel){
        try {
            String sql = "create table if not exists race_channels(guild INTEGER, channel INTEGER UNIQUE)";
            Statement statement = database.createStatement();
            statement.execute(sql);

            sql = "insert into race_channels (guild, channel) values ('"+channel.getGuild().getLongID()+"', '"+channel.getLongID()+"')";
            statement.execute(sql);

            while (Racebot.softBlocking || Racebot.hardBlocking){
                try {
                    Thread.sleep(100);
                } catch (InterruptedException err) {
                    err.printStackTrace();
                    log(err.getMessage());
                }
            }

            if (!raceGuilds.containsKey(channel.getGuild().getLongID())) raceGuilds.put(channel.getGuild().getLongID(), new RaceGuild());

            raceGuilds.get(channel.getGuild().getLongID()).addChannel(channel);

            if (channel.getLongID() == 393230283937415189L) channel.changeName("OpenRaceRoom_inactive");
            else channel.changeName("inactive_race_channel");

            channel.sendMessage("Channel successfully registered as a race channel");


        } catch (SQLException e) {
            if (e.getMessage().startsWith("[SQLITE_CONSTRAINT_UNIQUE]")){
                while (Racebot.softBlocking || Racebot.hardBlocking){
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException err) {
                        err.printStackTrace();
                        log(err.getMessage());
                    }
                }

                channel.sendMessage("Channel is already registered");
            } else {
                e.printStackTrace();
                System.out.println(e.getMessage());
            }
        }
    }

    public IChannel getChannel(IGuild guild, IChannel chan){
        long id = guild.getLongID();
        IChannel channel;
        if (raceGuilds.containsKey(id)){
            return raceGuilds.get(id).getOpenChannel(chan);
        }

        return null;
    }

    public void streams(IUser user, IChannel channel, String message){
        if (message.contains("twitch.tv/")) message = message.replace("twitch.tv/", "");
        if (message.contains("http://")) message = message.replace("http://", "");
        if (message.contains("www.")) message = message.replace("www.", "");

        String[] parts = message.split(" ", 2);
        if (parts.length > 1){
            String stream = parts[1];
            String name = user.getName()+user.getDiscriminator();
            String sql = "insert or replace into streams(name, stream) values (?,?)";
            try {
                PreparedStatement ps = database.prepareStatement(sql);
                ps.setString(1, name);
                ps.setString(2, stream);
                ps.executeUpdate();

                while (Racebot.softBlocking || Racebot.hardBlocking){
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException err) {
                        err.printStackTrace();
                        log(err.getMessage());
                    }
                }
                channel.sendMessage("Stream **" + stream + "** set for " +boldUsername(user) + ".");
            } catch (SQLException e) {
                e.printStackTrace();
                log(e.getMessage());
            }
        }
    }

    public void multis(IChannel channel, String message){
        String[] parts = message.split(" ");

        if (parts.length == 1) {
            boolean matchFound = false;
            for (Integer i : races.keySet()){
                if (races.get(i).getChannel().getLongID() == channel.getLongID()){
                    Race r = races.get(i);
                    matchFound = true;
                    while (Racebot.softBlocking || Racebot.hardBlocking){
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException err) {
                            err.printStackTrace();
                            log(err.getMessage());
                        }
                    }
                    channel.sendMessage(r.getMulti());
                }
            }

            if (!matchFound) {
                while (Racebot.softBlocking || Racebot.hardBlocking){
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException err) {
                        err.printStackTrace();
                        log(err.getMessage());
                    }
                }
                channel.sendMessage("No active race in this channel.");
            }
        } else if (parts.length > 1 && parts[1].matches("[0-9]+")){
            int id = Integer.parseInt(parts[1]);
            while (Racebot.softBlocking || Racebot.hardBlocking){
                try {
                    Thread.sleep(100);
                } catch (InterruptedException err) {
                    err.printStackTrace();
                    log(err.getMessage());
                }
            }
            if (races.containsKey(id)) channel.sendMessage(races.get(id).getMulti());
            else channel.sendMessage("No active race with that number.");
        } else {
            while (Racebot.softBlocking || Racebot.hardBlocking){
                try {
                    Thread.sleep(100);
                } catch (InterruptedException err) {
                    err.printStackTrace();
                    log(err.getMessage());
                }
            }
            channel.sendMessage("Malformed request.  Please use .rbhelp for assistance in formatting commands");
        }
    }

    public void modes(IChannel channel, String message){
        String parts[] = message.split(" ");

        if (parts.length > 1){
            if (parts[1].equalsIgnoreCase("tournament")){
                restrictedMode = true;

                client.changePresence(StatusType.ONLINE, ActivityType.WATCHING, "Tournament");
            } else if (parts[1].equalsIgnoreCase("normal")){
                restrictedMode = false;

                client.changePresence(StatusType.ONLINE, ActivityType.WATCHING, "Casual Races");
            }

            while (Racebot.softBlocking || Racebot.hardBlocking){
                try {
                    Thread.sleep(100);
                } catch (InterruptedException err) {
                    err.printStackTrace();
                    log(err.getMessage());
                }
            }

            String mode;
            if (restrictedMode) mode = "tournament";
            else mode = "normal";
            channel.sendMessage("Now in "+mode+" mode.");
        }
    }

    public void removeRace(int id){
        IChannel channel = races.get(id).getChannel();
        raceGuilds.get(channel.getGuild().getLongID()).freeChannel(channel);
        races.remove(id);
    }

    public void times(IChannel channel){
        boolean matchFound = false;
        for (Integer i : races.keySet()){
            if (races.get(i).getChannel().getLongID() == channel.getLongID()){
                Race r = races.get(i);
                matchFound = true;
                while (Racebot.softBlocking || Racebot.hardBlocking){
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException err) {
                        err.printStackTrace();
                        log(err.getMessage());
                    }
                }
                channel.sendMessage(r.getTime());
            }
        }
        if (!matchFound) {
            while (Racebot.softBlocking || Racebot.hardBlocking){
                try {
                    Thread.sleep(100);
                } catch (InterruptedException err) {
                    err.printStackTrace();
                    log(err.getMessage());
                }
            }
            channel.sendMessage("No active race in this channel.");
        }
    }

    public String rbStatus(){
        String status = "```RGLtv Racebot by kavoc"
                +"\nVersion: " + rbVersion
                + "\nTournament mode: "+ restrictedMode
                + "\nActive races: " + races.size()
                + "\nServer count: "+client.getGuilds().size()
                + "\nUptime: "+getUptime()+"```";

        return status;
    }

    public String gameShortcuts(String longName){
        String lowered = longName.toLowerCase();
        switch(lowered){
            case "cnd":
            case "chip n dale": return "Chip 'n Dale";
            case "loz":
            case "z1": return "The Legend of Zelda";
            case "alttp":
            case "lttp":
            case "z3": return "The Legend of Zelda: A Link to the Past";
            case "dt": return "Ducktales";
            case "dt2": return "Ducktales 2";
            case "cnt":
            case "cnt2": return "Chip 'n Tales 2";
            case "smw": return "Super Mario World";
            case "smb":
            case "smb1": return "Super Mario Bros.";
            case "smb2": return "Super Mario Bros. 2";
            case "smb3": return "Super Mario Bros. 3";
            case "smbj": return "Super Mario Bros: The Lost Levels";
            case "mm1": return "Mega Man 1";
            case "mm2": return "Mega Man 2";
            case "mm3": return "Mega Man 3";
            case "mm4": return "Mega Man 4";
            case "mm5": return "Mega Man 5";
            case "mm6": return "Mega Man 6";
            case "cv":
            case "cv1": return "Castlevania";
            case "cv2": return "Castlevania";
            case "cv3": return "Castlevania";
            case "ng": return "Ninja Gaiden";
            case "ml1": return "Mystery League Season 1";

        }
        return longName;
    }

    public String modeShortcuts(String longName){
        String lowered = longName.toLowerCase();
        switch(lowered){
            case "any":
            case "beat the game": return "any%";
            case "rando":
            case "random":
            case "randomizer": return "random%";
        }

        return longName;
    }

    public static String timeFormatter(long timeInNanos){
        String time = "";
        long t = timeInNanos / 1000000;

        long hours = t / 3600000;
        t = t - (3600000 * hours);
        long minutes = t / 60000;
        t = t - (60000 * minutes);
        long seconds = t / 1000;
        t = t - (1000 * seconds);
        long tenths = t / 100;

        if (hours > 0) time = time + hours + ":";

        if (minutes < 10) time = time + "0" + minutes + ":";
        else time = time + minutes + ":";

        if (seconds < 10) time = time + "0" + seconds + ".";
        else time = time + seconds + ".";

        time = time + tenths;

        return time;
    }

    public static String placeFormatter(int p)
    {
        String place = "";
        if (p == 11 || p == 12 || p == 13) place = p + "th";
        else if (p % 10 == 1) place = p + "st";
        else if (p % 10 == 2) place = p + "nd";
        else if (p % 10 == 3) place = p + "rd";
        else place = p + "th";
        return place;
    }

    public static String channelNameCleaner(String name){
        return name.toLowerCase().replace(" ", "_").replaceAll("[^^A-Za-z0-9_-]", "");
    }

    public static String boldUsername(IUser user){

        return "**" + user.getName() + "**" + user.getDiscriminator();
    }

    public String getUptime(){
        String time = "";

        long t = System.currentTimeMillis()-racebotStartTime;

        long days = t / 86400000;
        t = t - (86400000 * days);
        long hours = t / 3600000;
        t = t - (3600000 * hours);
        long minutes = t / 60000;
        t = t - (60000 * minutes);
        long seconds = t / 1000;
        t = t - (1000 * seconds);
        long tenths = t / 100;

        if (days > 0) time = time + days + " days ";

        if (hours > 0) time = time + hours + ":";

        if (minutes < 10) time = time + "0" + minutes + ":";
        else time = time + minutes + ":";

        if (seconds < 10) time = time + "0" + seconds + ".";
        else time = time + seconds + ".";

        time = time + tenths;

        return time;
    }

    public static void log(String message){
        try {
            logger.write("\r\n" + dateFormat.format(new Date()) + " - " + message);
            logger.flush();
        } catch (IOException e) {
            e.printStackTrace();

        }
    }
}


