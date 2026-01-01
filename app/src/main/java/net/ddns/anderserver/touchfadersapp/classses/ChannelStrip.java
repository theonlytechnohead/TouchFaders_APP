package net.ddns.anderserver.touchfadersapp.classses;

import androidx.annotation.Nullable;

import java.util.Objects;

public class ChannelStrip {

    private static long currentID = 0;

    private final long id;

    public int index;

    public int level;
    public boolean sendMuted;
    public String name;
    public boolean channelMuted;
    public String patch;
    public int colourIndex;
    public int colour;
    public int colourLighter;
    public int colourDarker;

    public boolean group = false;
    public int groupIndex = -1;
    public boolean hide = false;


    public ChannelStrip() {
        this.id = currentID++;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof ChannelStrip) {
            return ((ChannelStrip) obj).index == index;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(index);
    }

    public long stableID() {
        return this.id;
    }
}
