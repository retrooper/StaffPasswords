package io.github.retrooper.staffpasswords.api.bukkit.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class StaffLoginFailureEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final long timestamp;
    public StaffLoginFailureEvent(final Player player) {
        this.player = player;
        this.timestamp = System.currentTimeMillis();
    }

    public Player getPlayer() {
        return player;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
