package io.github.retrooper.staffpasswords.packet;

import com.amdelamar.jhash.Hash;
import com.amdelamar.jhash.algorithms.Type;
import com.amdelamar.jhash.exception.InvalidHashException;
import io.github.retrooper.packetevents.event.PacketListenerDynamic;
import io.github.retrooper.packetevents.event.impl.PacketReceiveEvent;
import io.github.retrooper.packetevents.packettype.PacketType;
import io.github.retrooper.packetevents.packetwrappers.play.in.chat.WrappedPacketInChat;
import io.github.retrooper.staffpasswords.Main;
import io.github.retrooper.staffpasswords.api.bukkit.events.StaffLoginSuccessEvent;
import io.github.retrooper.staffpasswords.api.bukkit.events.StaffLoginFailureEvent;
import io.github.retrooper.staffpasswords.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.Event;

import java.util.UUID;

public class PacketProcessor extends PacketListenerDynamic {
    public PacketProcessor(final byte priority) {
        super(priority);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PlayerData data = Main.getInstance().getUserData(uuid);
        if (!data.loggedIn && data.hasPassword) {
            switch (event.getPacketId()) {
                case PacketType.Client.USE_ENTITY:
                case PacketType.Client.UPDATE_SIGN:
                case PacketType.Client.BLOCK_PLACE:
                case PacketType.Client.USE_ITEM:
                case PacketType.Client.BLOCK_DIG:
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(ChatColor.DARK_RED
                            + "You are not allowed to do that action. Please login by typing your password!");
                    break;
                case PacketType.Client.CHAT:
                    WrappedPacketInChat chat = new WrappedPacketInChat(event.getNMSPacket());
                    String message = chat.getMessage();
                    String hashedPassword = Main.getInstance().getHashedPasswordsMap().get(uuid.toString());
                    try {
                        final Event staffEvent;
                        if (Hash.password(message.toCharArray()).algorithm(Type.PBKDF2_SHA256).verify(hashedPassword)) {
                            data.loggedIn = true;
                            Main.getInstance().updateUserData(event.getPlayer().getUniqueId(), data);
                            event.getPlayer().sendMessage(ChatColor.GREEN + "You successfully logged in!");
                            staffEvent = new StaffLoginSuccessEvent(event.getPlayer());
                        } else {
                            event.getPlayer().sendMessage(ChatColor.RED + "Incorrect password...");
                            staffEvent = new StaffLoginFailureEvent(event.getPlayer());
                        }
                        Bukkit.getScheduler().runTask(Main.getInstance(), new Runnable() {
                            @Override
                            public void run() {
                                Bukkit.getPluginManager().callEvent(staffEvent);
                            }
                        });
                    } catch (InvalidHashException e) {
                        e.printStackTrace();
                    }
                    event.setCancelled(true);
                    break;
            }
        }
    }
}
