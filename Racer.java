import sx.blah.discord.handle.obj.IUser;

/**
 * Copyright 2018 kavoc
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

public class Racer {
    String name;
    String discriminator;
    State state = State.Unready;
    IUser user;
    long time = Long.MAX_VALUE;
    int previousRating = 1500;
    int wins = 0;

    enum State {Unready, Ready, Racing, Forfeit, Finished}

    public Racer(IUser user){
        this.user = user;
        name = user.getName();
        this.discriminator = user.getDiscriminator();
    }

    public String BoldName()
    {
        return "**" + name + "**" + discriminator;
    }

    public State getState(){
        return state;
    }

    public void setState(State s){
        state = s;
    }
}
