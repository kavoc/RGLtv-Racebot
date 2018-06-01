# RGLtv-Racebot
Discord bot used for timing and recording speedrun races

In order to use this bot, you must register a new application with Discord.  This can be done at https://discordapp.com/developers/applications/me.  Discord will then provide you with an OAuth token that needs to be written in the racebot.ini file.

.race new name, mode - creates a new race
.race info # - retrieves information about the indicated race)
.races - lists the active races and their numbers
.start # - starts the indicated race (-f required when not all readies recieved
.join #  - joins the numbered race
.unjoin - leaves the race you are registered in
.ready - indicates you are ready to race
.unready - indicates you are no longer ready to race
.done - indicates completion of a race
.undone - indicates your done was a mistake
.forfeit - indicates that you wish to forfeit
.quit - same as forfeit
.stream twitch_name - adds a reference to your twitch stream for kadgar links (not required)

.forcefinish - Forfeits all remaining racers in the race and finishes the race.
.race remove  # - removes the indicated race from the list of active races
.start -f - Force starts a race where runners have not readied.
.register - Registers channel as a race channel.
.mode (normal or tournament) - Sets the current mode of the racebot.  Tournament mode prevents non-admins from starting races.
