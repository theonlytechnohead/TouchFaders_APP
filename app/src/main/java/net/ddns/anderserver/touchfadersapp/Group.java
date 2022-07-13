package net.ddns.anderserver.touchfadersapp;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Objects;

public class Group {

    public int index;

    public String name;
    public int colourIndex;
    public int groupIndex = -1;

    public HashMap<Integer, Integer> channels = new HashMap<>();

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof Group) {
            return ((Group) obj).index == index;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(index);
    }
}
