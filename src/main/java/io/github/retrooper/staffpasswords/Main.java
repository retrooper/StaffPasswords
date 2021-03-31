package io.github.retrooper.staffpasswords;

import com.amdelamar.jhash.Hash;
import com.amdelamar.jhash.algorithms.Type;
import io.github.retrooper.staffpasswords.api.bukkit.events.StaffLoginFailureEvent;
import io.github.retrooper.staffpasswords.api.bukkit.events.StaffLoginSuccessEvent;
import io.github.retrooper.staffpasswords.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Main extends JavaPlugin implements CommandExecutor, Listener {
    private static Main instance;
    private final ConcurrentHashMap<UUID, PlayerData> userData = new ConcurrentHashMap<>();

    public static Main getInstance() {
        return instance;
    }

    @Override
    public void onLoad() {
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        instance = this;

        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private void handle(Player player, Cancellable cancellable) {
        UUID uuid = player.getUniqueId();
        PlayerData data = Main.getInstance().getUserData(uuid);
        if (!data.loggedIn && data.hasPassword) {
            cancellable.setCancelled(true);
            player.sendMessage(ChatColor.DARK_RED
                    + "You are not allowed to do that action. Please login by typing your password!");
        }
    }

    private void handleCmdPreProcess(Player player, PlayerCommandPreprocessEvent event) {
        UUID uuid = player.getUniqueId();
        PlayerData data = Main.getInstance().getUserData(uuid);
        if (!data.loggedIn && data.hasPassword) {
            String msg = event.getMessage();
            String loweredMsg = msg.toLowerCase();
            if (!loweredMsg.startsWith("/staffpasswords login ") && loweredMsg.startsWith("/staffpassword login ")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager().getType() == EntityType.PLAYER) {
            handle((Player) event.getDamager(), event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockDig(BlockBreakEvent event) {
        handle(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        handle(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUpdateSign(SignChangeEvent event) {
        handle(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMessage(AsyncPlayerChatEvent event) {
        handle(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreProcessCmdEvent(PlayerCommandPreprocessEvent event) {
        handleCmdPreProcess(event.getPlayer(), event);
    }

    @Override
    public void onDisable() {

    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("staffpasswords")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (!player.hasPermission("staffpasswords.staff")) {
                    player.sendMessage(ChatColor.RED + "You need the permission staffpasswords.staff to use this command.");
                    return true;
                }
                if (args.length != 3 && args.length != 2) {
                    player.sendMessage(ChatColor.RED + "Invalid arguments.");
                    player.sendMessage(ChatColor.RED + "Usage: /staffpasswords setup <password> <repeat password>");
                    return true;
                }
                if (args[0].equalsIgnoreCase("setup")) {
                    PlayerData data = getUserData(player.getUniqueId());
                    if (data.loggedIn || !data.hasPassword) {
                        //In such a case, casing does matter.
                        if (!(args[1].equals(args[2]))) {
                            player.sendMessage(ChatColor.RED + "The passwords do not match...");
                            return true;
                        }
                        String hashedPassword = Hash.password(args[1].toCharArray())
                                .algorithm(Type.PBKDF2_SHA256)
                                .factor(getConfig().getInt("hash_complexity_factor"))
                                .create();
                        HashMap<String, String> hashedPasswordsMap = getHashedPasswordsMap();
                        hashedPasswordsMap.put(player.getUniqueId().toString(), hashedPassword);
                        updateHashedPasswordsMap(hashedPasswordsMap);
                        player.sendMessage(ChatColor.GREEN + "Password successfully setup.");
                        data.hasPassword = true;
                        data.loggedIn = true;
                        updateUserData(player.getUniqueId(), data);
                    } else {
                        player.sendMessage(ChatColor.RED + "You need to login before updating your password.");
                    }
                }
                else if (args[0].equalsIgnoreCase("login")) {
                    UUID uuid = player.getUniqueId();
                    PlayerData data = Main.getInstance().getUserData(uuid);
                    if (!data.loggedIn && data.hasPassword) {
                        String hashedPassword = Main.getInstance().getHashedPasswordsMap().get(uuid.toString());
                        String message = args[1];
                        Event staffEvent;
                        try {
                            if (Hash.password(message.toCharArray()).algorithm(Type.PBKDF2_SHA256).verify(hashedPassword)) {
                                data.loggedIn = true;
                                Main.getInstance().updateUserData(uuid, data);
                                player.sendMessage(ChatColor.GREEN + "You successfully logged in!");
                                staffEvent = new StaffLoginSuccessEvent(player);
                            } else {
                                player.sendMessage(ChatColor.RED + "Incorrect password...");
                                staffEvent = new StaffLoginFailureEvent(player);
                            }

                        } catch (Exception ex) {
                            player.sendMessage(ChatColor.RED + "Incorrect password...");
                            staffEvent = new StaffLoginFailureEvent(player);
                        }
                        Bukkit.getPluginManager().callEvent(staffEvent);
                    }
                }
            }
        }
        return true;
    }

    public void registerUser(UUID uuid) {
        userData.put(uuid, new PlayerData());
    }

    public void registerUser(UUID uuid, PlayerData data) {
        userData.put(uuid, data);
    }

    public void unregisterUser(UUID uuid) {
        userData.remove(uuid);
    }

    public PlayerData getUserData(UUID uuid) {
        return userData.get(uuid);
    }

    public void updateUserData(UUID uuid, PlayerData data) {
        userData.put(uuid, data);
    }

    public HashMap<String, String> getHashedPasswordsMap() {
        HashMap<String, String> map = new HashMap<>();
        if (!getConfig().isConfigurationSection("hashedpasswords")) {
            getConfig().createSection("hashedpasswords");
        }
        for (String key : getConfig().getConfigurationSection("hashedpasswords").getKeys(false)) {
            map.put(key, getConfig().getString("hashedpasswords." + key));
        }
        return map;
    }

    public void updateHashedPasswordsMap(HashMap<String, String> map) {
        for (String key : map.keySet()) {
            getConfig().set("hashedpasswords." + key, map.get(key));
        }
        saveConfig();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerData data = new PlayerData();
        data.loggedIn = false;
        HashMap<String, String> hashedPasswordsMap = getHashedPasswordsMap();
        data.hasPassword = hashedPasswordsMap.containsKey(event.getPlayer().getUniqueId().toString());
        registerUser(event.getPlayer().getUniqueId(), data);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        unregisterUser(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        double deltaX = Math.abs(event.getTo().getX() - event.getFrom().getX());
        double deltaZ = Math.abs(event.getTo().getZ() - event.getFrom().getZ());
        if (deltaX > 0.0 || deltaZ > 0.0) {
            UUID uuid = event.getPlayer().getUniqueId();
            PlayerData data = Main.getInstance().getUserData(uuid);
            if (!data.loggedIn && data.hasPassword) {
                event.getPlayer().sendMessage(ChatColor.DARK_RED
                        + "You are not allowed to do that action. Please login by typing your password!");
                Location fromLoc = event.getFrom();
                fromLoc.setPitch(event.getTo().getPitch());
                fromLoc.setYaw(event.getTo().getYaw());
                event.getPlayer().teleport(fromLoc);
            }
        }
    }

    @EventHandler
    public void onStaffLoginSuccess(StaffLoginSuccessEvent event) {
        Player player = event.getPlayer();
        long timestamp = event.getTimestamp();
    }

    @EventHandler
    public void onStaffLoginFailure(StaffLoginFailureEvent event) {
        Player player = event.getPlayer();
        long timestamp = event.getTimestamp();
        //You can use the API to execute a custom punishment...
        //player.kickPlayer("Entered password wrong!");
    }
}
