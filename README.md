# RGLtv-Racebot
Discord bot used for timing and recording speedrun races

In order to use this bot, you must register a new application with Discord.  This can be done at https://discordapp.com/developers/applications/me.  Discord will then provide you with an OAuth token that needs to be written in the racebot.ini file.

## Setup:
1. https://discordapp.com/developers/applications/me (sign in if necissary).
2. Click "New App".
3. Name the app, add a description and picture if you desire (picture will be displayed next to the bot in Discord) then click "Create App".
4. Click "Create Bot User" then "Yes, do it" when the warning pops up.
5. Reveal the token and copy that token into the racebot.ini file next to "token = ".
6. Click "Generate OAuth2 URL".
7. Ensure that only the "bot" scope is selected, and the permissions include at least "Manage Channels", "View Channels", and "Send Messages" (If you prefer to manage permissions through your own roles you may skip adding the permissions here, but the bot ultimately requires these permissions in the race channels you wish it to operate).
8. Copy the generated URL and visit it.
9. Select the server you wish to add the bot to and authorize it.
10. Create a "Bot Commander" role on the server and apply it to users that will have admin access to the racebot.  This role does not require any Discord privledges, it is only checked by name
11. Start the racebot (eg. "java -jar RGLtv-Racebot.jar")
12. ".register" in all channels where races may occur.  The racebot will overwrite channel names and will not revert them to their original name.  All inactive race channels will be renamed to "inactive_race_channel" when not in use.

## Commands
### Everyone
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

### Bot Commanders
.forcefinish - Forfeits all remaining racers in the race and finishes the race.  
.race remove  # - removes the indicated race from the list of active races  
.start -f - Force starts a race where runners have not readied.  
.register - Registers channel as a race channel.  
.mode (normal or tournament) - Sets the current mode of the racebot.  Tournament mode prevents non-admins from starting races.  
