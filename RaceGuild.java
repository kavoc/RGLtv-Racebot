import sx.blah.discord.handle.obj.IChannel;

import java.util.ArrayList;

/**
 * Copyright 2018 kavoc
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

public class RaceGuild {
    private ArrayList<RaceChannel> channels = new ArrayList<RaceChannel>();

    public void addChannel(IChannel chan){
        channels.add(new RaceChannel(chan));
    }

    public IChannel getOpenChannel(IChannel channel){
        for (RaceChannel chan : channels){
            if (chan.getID() == channel.getLongID() && !chan.isOccupied()){
                chan.setOccupied(true);
                return chan.getChannel();
            }
        }

        for (RaceChannel chan : channels){
            if (!chan.isOccupied()) {
                chan.setOccupied(true);
                return chan.getChannel();
            }
        }

        return null;
    }

    public void freeChannel(IChannel channel){
        for (RaceChannel chan : channels){
            if (chan.getChannel().getLongID() == channel.getLongID()){
                chan.setOccupied(false);
            }
        }
    }



    private class RaceChannel{
        boolean occupied = false;
        long id = 0;
        IChannel channel;

        public RaceChannel(IChannel channel){
            this.channel = channel;
            id = channel.getLongID();

        }

        public boolean isOccupied(){
            return occupied;
        }

        public void setOccupied(boolean b){
            occupied = b;
        }

        public long getID(){
            return id;
        }

        public IChannel getChannel(){
            return channel;
        }
    }
}
