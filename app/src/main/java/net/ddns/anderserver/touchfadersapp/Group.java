package net.ddns.anderserver.touchfadersapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Group {

    public int index;

    public String name;
    public int colourIndex;

    public final Map<Integer, Integer> channels = new HashMap<>();

    @NonNull
    @Override
    public String toString() {
        return "Group {" + name + ", channels: " + channels + "}";
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof Group other) {
            if (index != other.index) return false;
            return channels == other.channels;
        }
        return false;

    }

    @Override
    public int hashCode() {
        return Objects.hash(index);
    }

    Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("index", index);
        map.put("name", name);
        map.put("channels", channels);
        return map;
    }
}
