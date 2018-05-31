import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IUser;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by kavoc on 5/25/2017.
 */
public class Race {
    private int ID;
    private IChannel channel;

    private String game;
    private String mode;
    private boolean casual;

    private HashMap<String, Racer> racers = new HashMap<String, Racer>();
    private State state = State.Prerace;
    private long startTime = 0;
    private Timer f;

    public enum State {Prerace, Counting_Down, In_Progress, Finished, Finalized}

    private Racebot racebot;

    public Race(int ID, String gameName, String modeName, IChannel channel, boolean casual, Racebot racebot){
        this.ID = ID;
        this.casual = casual;
        game = gameName;
        mode = modeName;
        this.channel = channel;
        this.racebot = racebot;
    }

    public void editRace(String game, String mode){
        this.game = game;
        this.mode = mode;
        channel.changeName(ID + "_" +Racebot.channelNameCleaner(game)+"_-_"+Racebot.channelNameCleaner(mode));
    }

    public void start(){

        while ((Racebot.softBlocking || Racebot.hardBlocking)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Racebot.log(e.getMessage());
                return;
            }
        }

        Timer t = new Timer("start race timer");

        TimerTask task = new TimerTask() {
            int seconds = 10;
            @Override
            public void run() {
                String ats = "";
                for (String r : racers.keySet()){
                    ats += " " + racers.get(r).user.mention();
                }

                if (seconds == 10) {
                    channel.sendMessage("**Starting Race "+ID+" in 10 seconds!**" + ats);
                    state = State.Counting_Down;
                    Racebot.softBlocking = true;
                }
                else if (seconds == 0) {
                    channel.sendMessage("**Race "+ID+" GO!**");
                    startTime = System.nanoTime();
                    state = State.In_Progress;
                    t.cancel();
                    for (String r : racers.keySet()){
                        racers.get(r).setState(Racer.State.Racing);
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Racebot.log(e.getMessage());
                    }

                    Racebot.softBlocking = false;
                    Racebot.hardBlocking = false;
                }
                else if (seconds <= 3) {
                    Racebot.hardBlocking = true;
                    channel.sendMessage("**Race "+ID+" in "+seconds+ "!**");
                }
                seconds--;
            }
        };
        t.scheduleAtFixedRate(task, 0, 1000);
    }

    public int addRacer(IUser racer){
        String name = racer.getName() + racer.getDiscriminator();
        if (!racers.containsKey(name)){
            racers.put(name, new Racer(racer));
            return 0;
        } else {
            return 1;
        }
    }

    public int removeRacer(IUser racer){
        String name = racer.getName() + racer.getDiscriminator();
        if (racers.containsKey(name)){
            racers.remove(name);
            return 0;
        } else {
            return 1;
        }
    }

    public int readyRacer(IUser racer){
        String name = racer.getName() + racer.getDiscriminator();
        if (racers.containsKey(name)){
            if (racers.get(name).getState() == Racer.State.Unready) {
                racers.get(name).setState(Racer.State.Ready);
                return 0;
            } else {
                return 2;
            }
        } else {
            return 1;
        }
    }

    public int unreadyRacer(IUser racer){
        String name = racer.getName() + racer.getDiscriminator();
        if (racers.containsKey(name)){
            if (racers.get(name).getState() == Racer.State.Ready) {
                racers.get(name).setState(Racer.State.Unready);
                return 0;
            } else {
                return 2;
            }
        } else {
            return 1;
        }
    }

    public String doneRacer(IUser racer){
        String name = racer.getName() + racer.getDiscriminator();
        if (racers.containsKey(name)){
            Racer r = racers.get(name);
            if (r.getState() == Racer.State.Racing) {
                r.time = System.nanoTime() - startTime;
                r.setState(Racer.State.Finished);
                int place = finishCount();

                while (Racebot.softBlocking || Racebot.hardBlocking){
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Racebot.log(e.getMessage());
                    }
                }

                if (place == 1) return Racebot.boldUsername(racer) + " scores a **1st Place** knockout, with a time of " + Racebot.timeFormatter(r.time) + "!";
                else if (place == 2) return Racebot.boldUsername(racer) + " finishes in **2nd place**, with a time of " + Racebot.timeFormatter(r.time) + "!";
                else if (place == 3) return Racebot.boldUsername(racer) + " takes a weak **3rd place**, with a time of " + Racebot.timeFormatter(r.time) + "!";
                else if (place == 4) return Racebot.boldUsername(racer) + " IS IN ANOTHER TIMEZONE with a **4th place** finish time of " + Racebot.timeFormatter(r.time) + "!";
                else return Racebot.boldUsername(racer) + " finishes in **" + Racebot.placeFormatter(place) + " place**, with a time of " + Racebot.timeFormatter(r.time) + ".";
            }
            return null;
        } else {
            return Racebot.boldUsername(racer) + "is not currently registered to Race "+getID()+".";
        }

        //TODO check for race done
    }

    public int undoneRacer(IUser racer){
        String name = racer.getName() + racer.getDiscriminator();
        if (racers.containsKey(name)){
            if (racers.get(name).getState() == Racer.State.Finished) {
                racers.get(name).setState(Racer.State.Racing);
                if (state == State.Finished){
                    state = State.In_Progress;
                    if (f != null) f.cancel();
                    channel.sendMessage("Race " + getID() + " is longer finished.  Retracting GGs.");
                }
                return 0;
            } else {
                return 2;
            }
        } else {
            return 1;
        }
    }

    public int forfeitRacer(IUser racer){
        String name = racer.getName() + racer.getDiscriminator();
        if (racers.containsKey(name)){
            if (racers.get(name).getState() == Racer.State.Racing) {
                racers.get(name).setState(Racer.State.Forfeit);
                return 0;
            } else if (racers.get(name).getState() == Racer.State.Finished){
                return 2;
            } else if (racers.get(name).getState() == Racer.State.Forfeit){
                return 3;
            } else {
                return 4;
            }
        } else {
            return 1;
        }
    }

    public int forceFinish(){
        int forfeits = 0;
        for (String r : racers.keySet()){
            Racer racer = racers.get(r);

            if (racer.getState() == Racer.State.Racing) {
                forfeitRacer(racer.user);
                forfeits++;
            }

        }
        checkForRaceFinish();
        return forfeits;
    }

    public void checkForRaceFinish(){
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Racebot.log(e.getMessage());
        }

        boolean undone = false;
        for (String r : racers.keySet()){
            Racer racer = racers.get(r);
            if (racer.getState() != Racer.State.Finished && racer.getState() != Racer.State.Forfeit) undone = true;
        }

        if (undone == false){
            state = State.Finished;
            while (Racebot.softBlocking || Racebot.hardBlocking){
                try {
                    Thread.sleep(100);
                } catch (InterruptedException err) {
                    err.printStackTrace();
                    Racebot.log(err.getMessage());
                }
            }
            channel.sendMessage("Race "+getID()+" has finished.  GG to all the racers!");

            TimerTask task = new TimerTask() {

                @Override
                public void run() {
                    if (state == State.Finished) {
                        try {
                            DateFormat df = new SimpleDateFormat("MM/dd/yyyy");
                            DateFormat tod = new SimpleDateFormat("HH:mm");
                            Date date = new Date();
                            int casualInt = 0;
                            if (casual) casualInt = 1;

                            String sql = "INSERT into races (number, date, time, game, mode, casual) values (" + getID() + ", '" + df.format(date) + "', '" + tod.format(date) + "', '" + sqlName(game) + "', '" + sqlName(mode) + "', "+casualInt+")";
                            Statement statement = Racebot.database.createStatement();
                            statement.execute(sql);

                            sql = "create table race" + getID() + "(name varchar(255) PRIMARY KEY, time INTEGER)";
                            statement.execute(sql);

                            String dbGameMode =  sqlName(game + " - " + mode);

                            for (String r : racers.keySet()) {
                                Racer racer = racers.get(r);
                                sql = "INSERT into race" + getID() + "(name, time) values ('" + r + "', " + racer.time + ")";
                                statement.execute(sql);

                                String dbName = racer.name.replace(" ","-")+racer.discriminator;
                                sql = "create table if not exists "+ dbName +"(gamemode varchar(255) PRIMARY KEY, best_time INTEGER NOT NULL, score INTEGER NOT NULL)";
                                statement.execute(sql);

                                sql = "insert or replace into runner_translations (name, proper) values ('" + dbName.toLowerCase() + "', '" + dbName + "')";
                                statement.execute(sql);

                                sql = "select * from " + dbName + " where gamemode ='"+ dbGameMode + "'";
                                ResultSet rs = statement.executeQuery(sql);

                                boolean result = false;
                                while (rs.next()){
                                    result = true;
                                    long bestTime = rs.getLong("best_time");
                                    racer.previousRating = rs.getInt("score");

                                    if (racer.time < bestTime){
                                        sql = "update " + dbName + " set best_time=" + racer.time + " where gamemode='" + dbGameMode + "'";
                                        statement.execute(sql);
                                    }
                                }

                                if (!result){
                                    sql = "insert into " + dbName + "(gamemode, best_time, score) values('" + dbGameMode + "', " + racer.time + ", 1500)";
                                    statement.execute(sql);
                                }
                            }


                            if (!casual){
                                for (String r : racers.keySet()){
                                    Racer r1 = racers.get(r);
                                    double changeRating = 0;
                                    for (String s : racers.keySet()){
                                        Racer r2 = racers.get(s);

                                        double expected = 1d / (1d + Math.pow(10d, ((r2.previousRating - r1.previousRating) / 400d)));
                                        if (r1.time < r2.time) {
                                            changeRating += (1d - expected) * 32d;
                                        } else if (r1.time > r2.time){
                                            changeRating += (0d - expected) * 32d;
                                        } else {
                                            changeRating += 0;
                                        }
                                    }

                                    String dbName = r1.name+r1.discriminator;
                                    long score = r1.previousRating + Math.round(changeRating);
                                    sql = "update " + dbName + " set score= " + score + " where gamemode='" + dbGameMode + "'";
                                    statement.execute(sql);

                                }
                            }

                        } catch (SQLException e) {
                            e.printStackTrace();
                            Racebot.log(e.getMessage());
                            System.out.println(e.getMessage());
                        }


                        state = State.Finalized;

                        while (Racebot.softBlocking || Racebot.hardBlocking){
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException err) {
                                err.printStackTrace();
                                Racebot.log(err.getMessage());
                            }
                        }


                        channel.sendMessage("Race " + getID() + " finalized.");

                        channel.changeName("inactive_race_channel");

                        racebot.removeRace(getID());
                    }
                }
            };

            f = new Timer("Finalization Timer");
            f.schedule(task, 10000);
        }
    }

    public String getTime(){
        if (startTime != 0){
            long elapsed = System.nanoTime()-startTime;
            return "Race "+getID()+" elapsed time: " + Racebot.timeFormatter(elapsed);
        } else {
            return "Race has not yet begun.";
        }
    }

    public String getInfo(){
        String info = "Race "+getID()+": "+getGame()+" - "+ getMode()
                +"\nStatus: "+getState()+"\n";
        for (String r : racers.keySet()){
            info += racers.get(r).BoldName() + " ";
            if (racers.get(r).getState() == Racer.State.Finished){
                info += Racebot.timeFormatter(racers.get(r).time)+ " ";
            }
            info += racers.get(r).getState()+"\n";
        }

        return info;
    }

    public int getID(){
        return ID;
    }

    public String getGame(){
        return game;
    }

    public String getMode(){
        return mode;
    }

    public IChannel getChannel(){
        return channel;
    }

    public HashMap<String, Racer> getRacers(){
        return racers;
    }

    public State getState(){
        return state;
    }

    public int unreadyCount(){
        int count = 0;
        for (String r : racers.keySet()){
            if (racers.get(r).getState() == Racer.State.Unready) count++;
        }
        return count;
    }

    public int finishCount(){
        int count = 0;
        for (String r : racers.keySet()){
            if (racers.get(r).getState() == Racer.State.Finished) count++;
        }
        return count;
    }

    public String getMulti(){
        String link = "http://kadgar.net/live/";
        boolean atLeastOne = false;

        for (String r : racers.keySet()){
            String sql = "SELECT stream FROM streams WHERE name='" + r + "'";
            try {
                Statement statement = Racebot.database.createStatement();
                ResultSet rs = statement.executeQuery(sql);

                while (rs.next()){
                    atLeastOne = true;
                    link += rs.getString("stream") + "/";
                }

            } catch (SQLException e) {
                e.printStackTrace();
                Racebot.log(e.getMessage());
            }
        }

        if (atLeastOne) return link;
        else return "No users in this race have registered their streams.";
    }

    public String sqlName(String s){
        return  s.replace("'", "''");
    }
}
