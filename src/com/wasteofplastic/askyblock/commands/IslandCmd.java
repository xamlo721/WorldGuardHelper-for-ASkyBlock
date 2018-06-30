package com.wasteofplastic.askyblock.commands;

import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.wasteofplastic.askyblock.ASLocale;
import com.wasteofplastic.askyblock.ASkyBlock;
import com.wasteofplastic.askyblock.CoopPlay;
import com.wasteofplastic.askyblock.DeleteIslandChunk;
import com.wasteofplastic.askyblock.GridManager;
import com.wasteofplastic.askyblock.Island;
import com.wasteofplastic.askyblock.LevelCalcByChunk;
import com.wasteofplastic.askyblock.Messages;
import com.wasteofplastic.askyblock.PlayerCache;
import com.wasteofplastic.askyblock.Settings;
import com.wasteofplastic.askyblock.TopTen;
import com.wasteofplastic.askyblock.WarpSigns;
import com.wasteofplastic.askyblock.events.IslandJoinEvent;
import com.wasteofplastic.askyblock.events.IslandLeaveEvent;
import com.wasteofplastic.askyblock.events.IslandNewEvent;
import com.wasteofplastic.askyblock.events.IslandResetEvent;
import com.wasteofplastic.askyblock.listeners.ChatListener;
import com.wasteofplastic.askyblock.listeners.PlayerEvents;
import com.wasteofplastic.askyblock.panels.BiomesPanel;
import com.wasteofplastic.askyblock.panels.ControlPanel;
import com.wasteofplastic.askyblock.panels.SchematicsPanel;
import com.wasteofplastic.askyblock.panels.SettingsPanel;
import com.wasteofplastic.askyblock.panels.WarpPanel;
import com.wasteofplastic.askyblock.schematics.Schematic;
import com.wasteofplastic.askyblock.util.Util;
import com.wasteofplastic.askyblock.util.VaultHelper;

import community.xamlo.wg.WGHelper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Sign;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class IslandCmd
implements CommandExecutor,
TabCompleter {
    private static final boolean DEBUG = false;
    public boolean levelCalcFreeFlag = true;
    private static HashMap<String, Schematic> schematics = new HashMap();
    private ASkyBlock plugin;
    private HashMap<UUID, Boolean> confirm = new HashMap();
    Location last = null;
    private Set<UUID> pendingNewIslandSelection = new HashSet<UUID>();
    private Set<UUID> resettingIsland = new HashSet<UUID>();
    private final HashMap<UUID, UUID> inviteList = new HashMap();
    private HashMap<UUID, Long> resetWaitTime = new HashMap();
    private HashMap<UUID, Long> levelWaitTime = new HashMap();
    BukkitTask checker = null;
    private final Random random = new Random();
    private HashMap<UUID, Location> islandSpot = new HashMap();
    private List<UUID> leavingPlayers = new ArrayList<UUID>();
    
    public IslandCmd(ASkyBlock aSkyBlock) {
        this.plugin = aSkyBlock;
        this.loadSchematics();
    }

    public void loadSchematics() {
        Schematic netherIsland;
        File schematicFolder = new File(this.plugin.getDataFolder(), "schematics");
        if (!schematicFolder.exists()) {
            schematicFolder.mkdir();
        }
        schematics.clear();
        File schematicFile = new File(schematicFolder, "island.schematic");
        File netherFile = new File(schematicFolder, "nether.schematic");
        if (!schematicFile.exists()) {
            if (this.plugin.getResource("schematics/island.schematic") != null) {
                this.plugin.getLogger().info("Default schematic does not exist, saving it...");
                this.plugin.saveResource("schematics/island.schematic", false);
                try {
                    schematics.put("default", new Schematic(this.plugin, schematicFile));
                }
                catch (IOException e) {
                    this.plugin.getLogger().severe("Could not load default schematic!");
                    e.printStackTrace();
                }
            } else {
                schematics.put("default", new Schematic(this.plugin));
            }
            this.plugin.getLogger().info("Loaded default nether schematic");
        } else {
            try {
                schematics.put("default", new Schematic(this.plugin, schematicFile));
                this.plugin.getLogger().info("Loaded default island schematic.");
            }
            catch (IOException e) {
                this.plugin.getLogger().severe("Could not load default schematic!");
                e.printStackTrace();
            }
        }
        if (!netherFile.exists()) {
            if (this.plugin.getResource("schematics/nether.schematic") != null) {
                this.plugin.saveResource("schematics/nether.schematic", false);
                try {
                    netherIsland = new Schematic(this.plugin, netherFile);
                    netherIsland.setVisible(false);
                    schematics.put("nether", netherIsland);
                    this.plugin.getLogger().info("Loaded default nether schematic.");
                }
                catch (IOException e) {
                    this.plugin.getLogger().severe("Could not load default nether schematic!");
                    e.printStackTrace();
                }
            } else {
                this.plugin.getLogger().severe("Could not find default nether schematic!");
            }
        } else {
            try {
                netherIsland = new Schematic(this.plugin, netherFile);
                netherIsland.setVisible(false);
                schematics.put("nether", netherIsland);
                this.plugin.getLogger().info("Loaded default nether schematic.");
            }
            catch (IOException e) {
                this.plugin.getLogger().severe("Could not load default nether schematic!");
                e.printStackTrace();
            }
        }
        if (schematics.containsKey("default")) {
            schematics.get("default").setName("Island");
            schematics.get("default").setDescription("");
            schematics.get("default").setPartnerName("nether");
            schematics.get("default").setBiome(Settings.defaultBiome);
            schematics.get("default").setIcon(Material.GRASS);
            if (Settings.chestItems.length == 0) {
                schematics.get("default").setUseDefaultChest(false);
            }
            schematics.get("default").setOrder(0);
        }
        if (schematics.containsKey("nether")) {
            schematics.get("nether").setName("NetherBlock Island");
            schematics.get("nether").setDescription("Nether Island");
            schematics.get("nether").setPartnerName("default");
            schematics.get("nether").setBiome(Biome.HELL);
            schematics.get("nether").setIcon(Material.NETHERRACK);
            schematics.get("nether").setVisible(false);
            schematics.get("nether").setPasteEntities(true);
            if (Settings.chestItems.length == 0) {
                schematics.get("nether").setUseDefaultChest(false);
            }
        }
        ConfigurationSection schemSection = this.plugin.getConfig().getConfigurationSection("schematicsection");
        if (this.plugin.getConfig().contains("general.schematics")) {
            this.tip();
            int count = 1;
            for (String perms : this.plugin.getConfig().getConfigurationSection("general.schematics").getKeys(true)) {
                String fileName = this.plugin.getConfig().getString("general.schematics." + perms);
                File schem = new File(this.plugin.getDataFolder(), fileName);
                if (!schem.exists()) continue;
                this.plugin.getLogger().info("Loading schematic " + fileName + " for permission " + perms);
                try {
                    Schematic schematic = new Schematic(this.plugin, schem);
                    schematic.setPerm(perms);
                    schematic.setHeading(perms);
                    schematic.setName("#" + count++);
                    if (!schematic.isVisible()) {
                        this.plugin.getLogger().info("Schematic " + fileName + " will not be shown on the GUI");
                    }
                    schematics.put(perms, schematic);
                }
                catch (IOException e) {
                    this.plugin.getLogger().severe("Could not load schematic " + fileName + " due to error. Skipping...");
                }
            }
        } else if (this.plugin.getConfig().contains("schematicsection")) {
            Settings.useSchematicPanel = schemSection.getBoolean("useschematicspanel", false);
            Settings.chooseIslandRandomly = schemSection.getBoolean("chooseislandrandomly", false);
            for (String key : schemSection.getConfigurationSection("schematics").getKeys(false)) {
                try {
                    Schematic newSchem = null;
                    String filename = schemSection.getString("schematics." + key + ".filename", "");
                    if (!filename.isEmpty()) {
                        schematicFile = new File(schematicFolder, filename);
                        if (schematicFile.exists()) {
                            newSchem = new Schematic(this.plugin, schematicFile);
                        } else if (this.plugin.getResource("schematics/" + filename) != null) {
                            this.plugin.saveResource("schematics/" + filename, false);
                            newSchem = new Schematic(this.plugin, schematicFile);
                        }
                    } else if (key.equalsIgnoreCase("default")) {
                        newSchem = schematics.get("default");
                    } else {
                        this.plugin.getLogger().severe("Schematic " + key + " does not have a filename. Skipping!");
                    }
                    if (newSchem != null) {
                        List companionNames;
                        String spawnBlock;
                        List chestItems;
                        newSchem.setHeading(key);
                        newSchem.setOrder(schemSection.getInt("schematics." + key + ".order", 0));
                        try {
                            String iconString = schemSection.getString("schematics." + key + ".icon", "MAP").toUpperCase();
                            String[] split = iconString.split(":");
                            Material icon = StringUtils.isNumeric((String)split[0]) ? Material.getMaterial((int)Integer.parseInt(iconString)) : Material.valueOf((String)split[0]);
                            int damage = 0;
                            if (split.length == 2 && StringUtils.isNumeric((String)split[1])) {
                                damage = Integer.parseInt(split[1]);
                            }
                            newSchem.setIcon(icon, damage);
                        }
                        catch (Exception e) {
                            newSchem.setIcon(Material.MAP);
                        }
                        String name = ChatColor.translateAlternateColorCodes((char)'&', (String)schemSection.getString("schematics." + key + ".name", ""));
                        newSchem.setName(name);
                        int rating = schemSection.getInt("schematics." + key + ".rating", 50);
                        if (rating < 1) {
                            rating = 1;
                        } else if (rating > 100) {
                            rating = 100;
                        }
                        newSchem.setRating(rating);
                        double cost = schemSection.getDouble("schematics." + key + ".cost", 0.0);
                        if (cost < 0.0) {
                            cost = 0.0;
                        }
                        newSchem.setCost(cost);
                        String description = ChatColor.translateAlternateColorCodes((char)'&', (String)schemSection.getString("schematics." + key + ".description", ""));
                        description = description.replace("[rating]", String.valueOf(rating));
                        if (Settings.useEconomy) {
                            description = description.replace("[cost]", String.valueOf(cost));
                        }
                        newSchem.setDescription(description);
                        String perm = schemSection.getString("schematics." + key + ".permission", "");
                        newSchem.setPerm(perm);
                        newSchem.setUseDefaultChest(schemSection.getBoolean("schematics." + key + ".useDefaultChest", true));
                        String biomeString = schemSection.getString("schematics." + key + ".biome", Settings.defaultBiome.toString());
                        Biome biome = null;
                        try {
                            biome = Biome.valueOf((String)biomeString);
                            newSchem.setBiome(biome);
                        }
                        catch (Exception e) {
                            this.plugin.getLogger().severe("Could not parse biome " + biomeString + " using default instead.");
                        }
                        newSchem.setUsePhysics(schemSection.getBoolean("schematics." + key + ".usephysics", Settings.usePhysics));
                        newSchem.setPasteEntities(schemSection.getBoolean("schematics." + key + ".pasteentities", false));
                        newSchem.setVisible(schemSection.getBoolean("schematics." + key + ".show", true));
                        if (biome != null && biome.equals((Object)Biome.HELL)) {
                            newSchem.setPartnerName(schemSection.getString("schematics." + key + ".partnerSchematic", "default"));
                        } else {
                            newSchem.setPartnerName(schemSection.getString("schematics." + key + ".partnerSchematic", "nether"));
                        }
                        List companion = schemSection.getStringList("schematics." + key + ".companion");
                        ArrayList<EntityType> companionTypes = new ArrayList<EntityType>();
                        if (!companion.isEmpty()) {
                            for (String companionType : (List<String>)companion) {
                                if ((companionType = companionType.toUpperCase()).equalsIgnoreCase("NOTHING")) {
                                    companionTypes.add(null);
                                    continue;
                                }
                                try {
                                    EntityType type = EntityType.valueOf((String)companionType);
                                    companionTypes.add(type);
                                }
                                catch (Exception e) {
                                    this.plugin.getLogger().warning("Island companion is not recognized in schematic '" + name + "'.");
                                }
                            }
                            newSchem.setIslandCompanion(companionTypes);
                        }
                        if (!(companionNames = schemSection.getStringList("schematics." + key + ".companionnames")).isEmpty()) {
                            ArrayList<String> names = new ArrayList<String>();
                            for (String companionName : (List<String>)companionNames) {
                                names.add(ChatColor.translateAlternateColorCodes((char)'&', (String)companionName));
                            }
                            newSchem.setCompanionNames(names);
                        }
                        if (!(chestItems = schemSection.getStringList("schematics." + key + ".chestItems")).isEmpty()) {
                            ItemStack[] tempChest = new ItemStack[chestItems.size()];
                            int i = 0;
                            for (String chestItemString : (List<String>)chestItems) {
                                try {
                                    String[] amountdata = chestItemString.split(":");
                                    if (amountdata[0].equals("POTION")) {
                                        Potion chestPotion;
                                        if (amountdata.length == 3) {
                                            chestPotion = new Potion(PotionType.valueOf((String)amountdata[1]));
                                            tempChest[i++] = chestPotion.toItemStack(Integer.parseInt(amountdata[2]));
                                            continue;
                                        }
                                        if (amountdata.length != 4) continue;
                                        if (amountdata[2].equals("EXTENDED")) {
                                            chestPotion = new Potion(PotionType.valueOf((String)amountdata[1])).extend();
                                            tempChest[i++] = chestPotion.toItemStack(Integer.parseInt(amountdata[3]));
                                            continue;
                                        }
                                        if (amountdata[2].equals("SPLASH")) {
                                            chestPotion = new Potion(PotionType.valueOf((String)amountdata[1])).splash();
                                            tempChest[i++] = chestPotion.toItemStack(Integer.parseInt(amountdata[3]));
                                            continue;
                                        }
                                        if (!amountdata[2].equals("EXTENDEDSPLASH")) continue;
                                        chestPotion = new Potion(PotionType.valueOf((String)amountdata[1])).extend().splash();
                                        tempChest[i++] = chestPotion.toItemStack(Integer.parseInt(amountdata[3]));
                                        continue;
                                    }
                                    Material mat = StringUtils.isNumeric((String)amountdata[0]) ? Material.getMaterial((int)Integer.parseInt(amountdata[0])) : Material.getMaterial((String)amountdata[0].toUpperCase());
                                    if (amountdata.length == 2) {
                                        tempChest[i++] = new ItemStack(mat, Integer.parseInt(amountdata[1]));
                                        continue;
                                    }
                                    if (amountdata.length != 3) continue;
                                    tempChest[i++] = new ItemStack(mat, Integer.parseInt(amountdata[2]), Short.parseShort(amountdata[1]));
                                }
                                catch (IllegalArgumentException ex) {
                                    this.plugin.getLogger().severe("Problem loading chest item for schematic '" + name + "' so skipping it: " + chestItemString);
                                    this.plugin.getLogger().severe("Error is : " + ex.getMessage());
                                    this.plugin.getLogger().info("Potential potion types are: ");
                                    for (PotionType c : PotionType.values()) {
                                        this.plugin.getLogger().info(c.name());
                                    }
                                }
                                catch (Exception e) {
                                    this.plugin.getLogger().severe("Problem loading chest item for schematic '" + name + "' so skipping it: " + chestItemString);
                                    this.plugin.getLogger().info("Potential material types are: ");
                                    for (Material c : Material.values()) {
                                        this.plugin.getLogger().info(c.name());
                                    }
                                }
                            }
                            newSchem.setDefaultChestItems(tempChest);
                        }
                        if ((spawnBlock = schemSection.getString("schematics." + key + ".spawnblock")) != null) {
                            try {
                                Material playerSpawnBlock = StringUtils.isNumeric((String)spawnBlock) ? Material.getMaterial((int)Integer.parseInt(spawnBlock)) : Material.valueOf((String)spawnBlock.toUpperCase());
                                if (newSchem.setPlayerSpawnBlock(playerSpawnBlock)) {
                                    this.plugin.getLogger().info("Player will spawn at the " + playerSpawnBlock.toString());
                                } else {
                                    this.plugin.getLogger().severe("Problem with schematic '" + name + "'. Spawn block '" + spawnBlock + "' not found in schematic or there is more than one. Skipping...");
                                }
                            }
                            catch (Exception e) {
                                this.plugin.getLogger().severe("Problem with schematic '" + name + "'. Spawn block '" + spawnBlock + "' is unknown. Skipping...");
                            }
                        }
                        newSchem.setLevelHandicap(schemSection.getInt("schematics." + key + ".levelHandicap", 0));
                        schematics.put(key, newSchem);
                        perm = perm.isEmpty() ? "all players" : "player with " + perm + " permission";
                        this.plugin.getLogger().info("Loading schematic " + name + " (" + filename + ") for " + perm + ", order " + newSchem.getOrder());
                        continue;
                    }
                    this.plugin.getLogger().warning("Could not find " + filename + " in the schematics folder! Skipping...");
                }
                catch (IOException e) {
                    this.plugin.getLogger().info("Error loading schematic in section " + key + ". Skipping...");
                }
            }
            if (schematics.isEmpty()) {
                this.tip();
            }
        }
    }

    private void tip() {
        this.plugin.getLogger().warning("***************************************************************");
        this.plugin.getLogger().warning("* 'schematics' section in config.yml has been deprecated.     *");
        this.plugin.getLogger().warning("* See 'schematicsection' in config.new.yml for replacement.   *");
        this.plugin.getLogger().warning("***************************************************************");
    }

    public boolean addPlayertoTeam(UUID playerUUID, UUID teamLeader) {
        if (!this.plugin.getPlayers().setJoinTeam(playerUUID, teamLeader, this.plugin.getPlayers().getIslandLocation(teamLeader))) {
            return false;
        }
        if (!playerUUID.equals(teamLeader)) {
            this.plugin.getPlayers().clearHomeLocations(playerUUID);
            for (Map.Entry<Integer, Location> homes : this.plugin.getPlayers().getHomeLocations(teamLeader).entrySet()) {
                if (homes.getKey() >= 2) continue;
                this.plugin.getPlayers().setHomeLocation(playerUUID, homes.getValue(), homes.getKey());
            }
            if (this.plugin.getPlayers().getHomeLocation(teamLeader, 1) == null) {
                this.plugin.getPlayers().setHomeLocation(playerUUID, this.plugin.getPlayers().getIslandLocation(teamLeader));
            }
            if (!this.plugin.getPlayers().getMembers(teamLeader).contains(playerUUID)) {
                this.plugin.getPlayers().addTeamMember(teamLeader, playerUUID);
            }
            if (!this.plugin.getPlayers().getMembers(teamLeader).contains(teamLeader)) {
                this.plugin.getPlayers().addTeamMember(teamLeader, teamLeader);
            }
            Island island = this.plugin.getGrid().getIsland(teamLeader);
            IslandJoinEvent event = new IslandJoinEvent(playerUUID, island);
            this.plugin.getServer().getPluginManager().callEvent((Event)event);
            WGHelper.addMember(Bukkit.getOfflinePlayer(playerUUID).getName(), Bukkit.getOfflinePlayer(teamLeader).getName());
        }
        return true;
    }

    public boolean removePlayerFromTeam(UUID playerUUID, UUID teamLeader) {
        return this.removePlayerFromTeam(playerUUID, teamLeader, false);
    }

    public boolean removePlayerFromTeam(UUID playerUUID, UUID teamLeader, boolean makeLeader) {
        this.plugin.getPlayers().removeMember(teamLeader, playerUUID);
        if (teamLeader == null || !playerUUID.equals(teamLeader)) {
            if (!this.plugin.getPlayers().setLeaveTeam(playerUUID)) {
                return false;
            }
            this.plugin.getPlayers().clearHomeLocations(playerUUID);
            this.plugin.getPlayers().setIslandLocation(playerUUID, null);
            this.plugin.getPlayers().setTeamIslandLocation(playerUUID, null);
            WGHelper.RemoveMember(Bukkit.getOfflinePlayer(playerUUID).getName(), Bukkit.getOfflinePlayer(teamLeader).getName());

            if (!makeLeader) {
                int resetsLeft;
                OfflinePlayer offlinePlayer = this.plugin.getServer().getOfflinePlayer(playerUUID);
                if (offlinePlayer.isOnline()) {
                    if (!((Player)offlinePlayer).hasPermission("askyblock.command.leaveexempt")) {
                        IslandCmd.runCommands(Settings.leaveCommands, offlinePlayer);
                    }
                } else {
                    IslandCmd.runCommands(Settings.leaveCommands, offlinePlayer);
                }
                if (Settings.leaversLoseReset && Settings.resetLimit >= 0 && (resetsLeft = this.plugin.getPlayers().getResetsLeft(playerUUID)) > 0) {
                    this.plugin.getPlayers().setResetsLeft(playerUUID, --resetsLeft);
                }
            }
            if (teamLeader != null) {
                Island island = this.plugin.getGrid().getIsland(teamLeader);
                IslandLeaveEvent event = new IslandLeaveEvent(playerUUID, island);
                this.plugin.getServer().getPluginManager().callEvent((Event)event);
            }
        } else if (!this.plugin.getPlayers().setLeaveTeam(playerUUID)) {
            return false;
        }
        WGHelper.RemoveMember(Bukkit.getOfflinePlayer(playerUUID).getName(), Bukkit.getOfflinePlayer(teamLeader).getName());
        return true;
    }

    public List<Schematic> getSchematics(Player player, boolean ignoreNoPermission) {
        ArrayList<Schematic> result = new ArrayList<Schematic>();
        for (Schematic schematic : schematics.values()) {
            if ((ignoreNoPermission || !schematic.getPerm().isEmpty()) && !VaultHelper.checkPerm(player, schematic.getPerm()) || !schematic.isVisible()) continue;
            if (schematic.getBiome().equals((Object)Biome.HELL)) {
                if (!Settings.createNether || !Settings.newNether || ASkyBlock.getNetherWorld() == null) continue;
                result.add(schematic);
                continue;
            }
            result.add(schematic);
        }
        Collections.sort(result, new Comparator<Schematic>(){

            @Override
            public int compare(Schematic o1, Schematic o2) {
                return o2.getOrder() < o1.getOrder() ? 1 : -1;
            }
        });
        return result;
    }

    public static HashMap<String, Schematic> getSchematics() {
        return schematics;
    }

    public void newIsland(Player player) {
        this.newIsland(player, schematics.get("default"));
    }

    public void newIsland(Player player, Schematic schematic) {
        UUID playerUUID = player.getUniqueId();
        boolean firstTime = false;
        if (!this.plugin.getPlayers().hasIsland(playerUUID)) {
            firstTime = true;
        }
        Location next = this.getNextIsland(player.getUniqueId());
        this.plugin.getPlayers().setHasIsland(playerUUID, true);
        this.plugin.getPlayers().clearHomeLocations(playerUUID);
        this.plugin.getPlayers().setIslandLocation(playerUUID, next);
        if (schematic.isPlayerSpawn()) {
            this.plugin.getPlayers().setHomeLocation(playerUUID, schematic.getPlayerSpawn(next), 1);
            this.plugin.getPlayers().setHomeLocation(playerUUID, schematic.getPlayerSpawn(next), -1);
        }
        this.plugin.setNewIsland(true);
        
        if (schematic != null) {
            if (Settings.createNether && schematic.isInNether() && Settings.newNether && ASkyBlock.getNetherWorld() != null) {
                if (!schematic.getPartnerName().isEmpty() && schematics.containsKey(schematic.getPartnerName())) {
                    this.pastePartner(schematics.get(schematic.getPartnerName()), next, player);
                  
                }
                next = next.toVector().toLocation(ASkyBlock.getNetherWorld());
                this.plugin.getPlayers().setIslandLocation(playerUUID, next);
                schematic.pasteSchematic(next, player, true, firstTime ? Schematic.PasteReason.NEW_ISLAND : Schematic.PasteReason.RESET);
            } else {
                schematic.pasteSchematic(next, player, true, firstTime ? Schematic.PasteReason.NEW_ISLAND : Schematic.PasteReason.RESET);
                if (Settings.createNether && Settings.newNether && ASkyBlock.getNetherWorld() != null) {
                    Location netherLoc = next.toVector().toLocation(ASkyBlock.getNetherWorld());
                    if (schematic.getPartnerName().isEmpty()) {
                        this.pastePartner(schematic, netherLoc, player);
                    } else if (schematics.containsKey(schematic.getPartnerName())) {
                        this.pastePartner(schematics.get(schematic.getPartnerName()), netherLoc, player);
                    } else {
                        this.plugin.getLogger().severe("Partner schematic heading '" + schematic.getPartnerName() + "' does not exist");
                    }
                }
            }
            this.plugin.getPlayers().setStartIslandRating(playerUUID, schematic.getRating());
            WGHelper.protectIsland(player);
        }
        this.plugin.setNewIsland(false);
        Island myIsland = this.plugin.getGrid().addIsland(next.getBlockX(), next.getBlockZ(), playerUUID);
        myIsland.setLevelHandicap(schematic.getLevelHandicap());
        this.plugin.getPlayers().save(playerUUID);
        if (!firstTime) {
            this.setResetWaitTime(player);
        }
        int range = Settings.islandProtectionRange;
        for (PermissionAttachmentInfo perms : player.getEffectivePermissions()) {
            if (!perms.getPermission().startsWith("askyblock.island.range.")) continue;
            if (perms.getPermission().contains("askyblock.island.range.*")) {
                range = Settings.islandProtectionRange;
                break;
            }
            String[] spl = perms.getPermission().split("askyblock.island.range.");
            if (spl.length <= 1) continue;
            if (!NumberUtils.isDigits((String)spl[1])) {
                this.plugin.getLogger().severe("Player " + player.getName() + " has permission: " + perms.getPermission() + " <-- the last part MUST be a number! Ignoring...");
                continue;
            }
            range = Math.max(range, Integer.valueOf(spl[1]));
        }
        if (range % 2 != 0) {
            this.plugin.getLogger().warning("Protection range must be even, using " + --range + " for " + player.getName());
        }
        if (range > Settings.islandDistance) {
            this.plugin.getLogger().warning("Player has askyblock.island.range." + range);
            range = Settings.islandDistance;
            this.plugin.getLogger().warning("Island protection range must be " + Settings.islandDistance + " or less. Setting to: " + range);
        }
        myIsland.setProtectionSize(range);
        this.plugin.getGrid().saveGrid();
        IslandNewEvent event = new IslandNewEvent(player, schematic, myIsland);
        this.plugin.getServer().getPluginManager().callEvent((Event)event);
       
    }

    private void pastePartner(final Schematic schematic, final Location loc, final Player player) {
        this.plugin.getServer().getScheduler().runTaskLater((Plugin)this.plugin, new Runnable(){

            @Override
            public void run() {
                schematic.pasteSchematic(loc, player, false, Schematic.PasteReason.PARTNER);
                if (schematic.isPlayerSpawn()) {
                    IslandCmd.this.plugin.getPlayers().setHomeLocation(player.getUniqueId(), schematic.getPlayerSpawn(loc), -2);
                }
            }
        }, 60);
    }

    public void pasteSchematic(Schematic schematic, Location loc, Player player, Schematic.PasteReason reason) {
        schematic.pasteSchematic(loc, player, false, reason);
       
    }

    private Location getNextIsland(UUID playerUUID) {
        Location next;
        if (this.islandSpot.containsKey(playerUUID)) {
            next = this.plugin.getGrid().getClosestIsland(this.islandSpot.get(playerUUID));
            this.islandSpot.remove(playerUUID);
            Island island = this.plugin.getGrid().getIslandAt(next);
            if (island == null || island.getOwner() == null) {
                return next;
            }
        }
        if (this.last == null) {
            this.last = new Location(ASkyBlock.getIslandWorld(), (double)((long)Settings.islandXOffset + Settings.islandStartX), (double)Settings.islandHeight, (double)((long)Settings.islandZOffset + Settings.islandStartZ));
        }
        next = this.last.clone();
        while (this.plugin.getGrid().islandAtLocation(next) || this.islandSpot.containsValue((Object)next)) {
            next = this.nextGridLocation(next);
        }
        this.last = next.clone();
        return next;
    }

    private Location nextGridLocation(Location lastIsland) {
        int x = lastIsland.getBlockX();
        int z = lastIsland.getBlockZ();
        Location nextPos = lastIsland;
        if (x < z) {
            if (-1 * x < z) {
                nextPos.setX(nextPos.getX() + (double)Settings.islandDistance);
                return nextPos;
            }
            nextPos.setZ(nextPos.getZ() + (double)Settings.islandDistance);
            return nextPos;
        }
        if (x > z) {
            if (-1 * x >= z) {
                nextPos.setX(nextPos.getX() - (double)Settings.islandDistance);
                return nextPos;
            }
            nextPos.setZ(nextPos.getZ() - (double)Settings.islandDistance);
            return nextPos;
        }
        if (x <= 0) {
            nextPos.setZ(nextPos.getZ() + (double)Settings.islandDistance);
            return nextPos;
        }
        nextPos.setZ(nextPos.getZ() - (double)Settings.islandDistance);
        return nextPos;
    }

    public boolean calculateIslandLevel(CommandSender sender, UUID targetPlayer) {
        return this.calculateIslandLevel(sender, targetPlayer, false);
    }

    public boolean calculateIslandLevel(CommandSender sender, UUID targetPlayer, boolean report) {
        if (sender instanceof Player) {
            Player asker = (Player)sender;
            if (asker.getUniqueId().equals(targetPlayer) || asker.isOp() || VaultHelper.checkPerm(asker, "askyblock.mod.info")) {
                if (!this.onLevelWaitTime(asker) || Settings.levelWait <= 0 || asker.isOp() || VaultHelper.checkPerm(asker, "askyblock.mod.info")) {
                    Util.sendMessage((CommandSender)asker, (Object)ChatColor.GREEN + this.plugin.myLocale((UUID)asker.getUniqueId()).levelCalculating);
                    this.setLevelWaitTime(asker);
                    new LevelCalcByChunk(this.plugin, targetPlayer, (CommandSender)asker, report);
                } else {
                    Util.sendMessage((CommandSender)asker, (Object)ChatColor.YELLOW + this.plugin.myLocale((UUID)asker.getUniqueId()).islandresetWait.replace("[time]", String.valueOf(this.getLevelWaitTime(asker))));
                }
            } else {
                Util.sendMessage((CommandSender)asker, (Object)ChatColor.GREEN + this.plugin.myLocale((UUID)asker.getUniqueId()).islandislandLevelis + " " + (Object)ChatColor.WHITE + this.plugin.getPlayers().getIslandLevel(targetPlayer));
            }
        } else {
            Util.sendMessage(sender, (Object)ChatColor.GREEN + this.plugin.myLocale().levelCalculating);
            new LevelCalcByChunk(this.plugin, targetPlayer, sender, report);
        }
        return true;
    }

    public static <T, E> T getKeyByValue(Map<T, E> map, E value) {
        for (Map.Entry<T, E> entry : map.entrySet()) {
            if (!value.equals(entry.getValue())) continue;
            return entry.getKey();
        }
        return null;
    }

   public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] split) {
	   
        if (!(sender instanceof Player)) {
            Util.sendMessage(sender, this.plugin.myLocale().errorUseInGame);
            return false;
        }
        
        final Player player = (Player)sender;
        if (!VaultHelper.checkPerm(player, "askyblock.island.create")) {
            Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).islanderrorYouDoNotHavePermission);
            return true;
        }
        
        final UUID playerUUID = player.getUniqueId();
        if (playerUUID == null) {
            this.plugin.getLogger().severe("Player " + sender.getName() + " has a null UUID - this should never happen!");
            Util.sendMessage(sender, ChatColor.RED + this.plugin.myLocale().errorCommandNotReady + " (No UUID)");
            return true;
        }
        
        final UUID teamLeader = this.plugin.getPlayers().getTeamLeader(playerUUID);
        List<UUID> teamMembers = new ArrayList<UUID>();
        if (teamLeader != null) {
            teamMembers = (List<UUID>)this.plugin.getPlayers().getMembers(teamLeader);
        }
        if (split.length <= 1 || !split[0].equalsIgnoreCase("name")) {

            UUID targetPlayer = null;
            Label_14129: {
                switch (split.length) {
                    case 0: {
                        if (this.plugin.getPlayers().getIslandLocation(playerUUID) != null || this.plugin.getPlayers().inTeam(playerUUID)) {
                            if (VaultHelper.checkPerm(player, "askyblock.island.controlpanel") && this.plugin.getPlayers().getControlPanel(playerUUID)) {
                                player.performCommand("island cp");
                            }
                            else {
                                if (!VaultHelper.checkPerm(player, "askyblock.island.go")) {
                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoPermission);
                                    return true;
                                }
                                if (!player.getWorld().getName().equalsIgnoreCase(Settings.worldName) || Settings.allowTeleportWhenFalling || !PlayerEvents.isFalling(playerUUID) || (player.isOp() && !Settings.damageOps)) {
                                    this.plugin.getGrid().homeTeleport(player);
                                    if (Settings.islandRemoveMobs) {
                                        this.plugin.getGrid().removeMobs(player.getLocation());
                                    }
                                }
                                else {
                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorCommandNotReady);
                                }
                            }
                            return true;
                        }
                        if (Settings.maxIslands > 0 && this.plugin.getGrid().getIslandCount() > Settings.maxIslands) {
                            Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorMaxIslands);
                            return true;
                        }
                        if (this.plugin.getPlayers().getResetsLeft(playerUUID) == 0) {
                            Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).islandResetNoMore);
                            return true;
                        }
                        Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).islandnew);
                        this.chooseIsland(player);
                        return true;
                    }
                    case 1: {
                        if (split[0].equalsIgnoreCase("value")) {
                            if (VaultHelper.checkPerm(player, "askyblock.island.value")) {
                                if (!this.plugin.getGrid().playerIsOnIsland(player)) {
                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNotOnIsland);
                                    return true;
                                }
                                final ItemStack item = player.getItemInHand();
                                double multiplier = 1.0;
                                if (item != null && item.getType().isBlock()) {
                                    for (final PermissionAttachmentInfo perms : player.getEffectivePermissions()) {
                                        if (perms.getPermission().startsWith("askyblock.island.multiplier.")) {
                                            final String[] spl = perms.getPermission().split("askyblock.island.multiplier.");
                                            if (spl.length > 1) {
                                                if (!NumberUtils.isDigits(spl[1])) {
                                                    this.plugin.getLogger().severe("Player " + player.getName() + " has permission: " + perms.getPermission() + " <-- the last part MUST be a number! Ignoring...");
                                                }
                                                else {
                                                    multiplier = Math.max(multiplier, Integer.valueOf(spl[1]));
                                                }
                                            }
                                        }
                                        if (multiplier < 1.0) {
                                            multiplier = 1.0;
                                        }
                                    }
                                    if (player.getLocation().getBlockY() < Settings.seaHeight) {
                                        multiplier *= Settings.underWaterMultiplier;
                                    }
                                    int value = 0;
                                    if (Settings.blockValues.containsKey(item.getData())) {
                                        value = (int)(Settings.blockValues.get(item.getData()) * multiplier);
                                    }
                                    else if (Settings.blockValues.containsKey(new MaterialData(item.getType()))) {
                                        value = (int)(Settings.blockValues.get(new MaterialData(item.getType())) * multiplier);
                                    }
                                    if (value > 0) {
                                        Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).islandblockValue.replace("[name]", Util.prettifyText(item.getType().name())).replace("[value]", String.valueOf(value)));
                                    }
                                    else {
                                        Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).islandblockWorthless.replace("[name]", Util.prettifyText(item.getType().name())));
                                    }
                                }
                                else {
                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNotABlock);
                                }
                                return true;
                            }
                        }
                        else if (split[0].equalsIgnoreCase("name")) {
                            if (VaultHelper.checkPerm(player, "askyblock.island.name") && this.plugin.getPlayers().hasIsland(playerUUID)) {
                                Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " name <name>: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandHelpName);
                                return true;
                            }
                        }
                        else if (split[0].equalsIgnoreCase("resetname")) {
                            if (VaultHelper.checkPerm(player, "askyblock.island.name") && this.plugin.getPlayers().hasIsland(playerUUID)) {
                                this.plugin.getGrid().setIslandName(playerUUID, (String)null);
                                Util.sendMessage(sender, this.plugin.myLocale().generalSuccess);
                            }
                            return true;
                        }
                        if (split[0].equalsIgnoreCase("coop")) {
                            if (VaultHelper.checkPerm(player, "askyblock.coop")) {
                                Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " coop <player>: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpCoop);
                                return true;
                            }
                        }
                        else if (split[0].equalsIgnoreCase("uncoop")) {
                            if (VaultHelper.checkPerm(player, "askyblock.coop")) {
                                Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " uncoop <player>: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpUnCoop);
                                return true;
                            }
                        }
                        else if (split[0].equalsIgnoreCase("expel")) {
                            if (VaultHelper.checkPerm(player, "askyblock.island.expel")) {
                                Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " expel <player>: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpExpel);
                                return true;
                            }
                        }
                        else if (split[0].equalsIgnoreCase("teamchat") || split[0].equalsIgnoreCase("tc")) {
                            if (VaultHelper.checkPerm(player, "askyblock.team.chat")) {
                                if (!Settings.teamChat) {
                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale().errorUnknownCommand);
                                    return false;
                                }
                                if (this.plugin.getPlayers().inTeam(playerUUID)) {
                                    boolean online = false;
                                    for (final UUID teamMember : this.plugin.getPlayers().getMembers(playerUUID)) {
                                        if (!teamMember.equals(playerUUID) && this.plugin.getServer().getPlayer(teamMember) != null) {
                                            online = true;
                                        }
                                    }
                                    if (!online) {
                                        Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).teamChatNoTeamAround);
                                        Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(playerUUID).teamChatStatusOff);
                                        this.plugin.getChatListener().unSetPlayer(playerUUID);
                                        return true;
                                    }
                                    if (this.plugin.getChatListener().isTeamChat(playerUUID)) {
                                        Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(playerUUID).teamChatStatusOff);
                                        this.plugin.getChatListener().unSetPlayer(playerUUID);
                                    }
                                    else {
                                        Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(playerUUID).teamChatStatusOn);
                                        this.plugin.getChatListener().setPlayer(playerUUID);
                                    }
                                }
                                else {
                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).teamChatNoTeam);
                                }
                            }
                            else {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).errorNoPermission);
                            }
                            return true;
                        }
                        if (split[0].equalsIgnoreCase("banlist")) {
                            if (VaultHelper.checkPerm(player, "askyblock.island.ban")) {
                                Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(playerUUID).adminInfoBannedPlayers + ":");
                                final List<UUID> bannedList = (List<UUID>)this.plugin.getPlayers().getBanList(playerUUID);
                                if (bannedList.isEmpty()) {
                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).banNone);
                                }
                                else {
                                    for (final UUID bannedPlayers : bannedList) {
                                        Util.sendMessage((CommandSender)player, this.plugin.myLocale(playerUUID).helpColor + this.plugin.getPlayers().getName(bannedPlayers));
                                    }
                                }
                                return true;
                            }
                            Util.sendMessage((CommandSender)player, this.plugin.myLocale(playerUUID).errorNoPermission);
                            return true;
                        }
                        else {
                            if (split[0].equalsIgnoreCase("ban")) {
                                if (VaultHelper.checkPerm(player, "askyblock.island.ban")) {
                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(playerUUID).helpColor + "/" + label + " ban <player>: " + ChatColor.WHITE + this.plugin.myLocale(playerUUID).islandhelpBan);
                                }
                                else {
                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(playerUUID).errorNoPermission);
                                }
                                return true;
                            }
                            if (split[0].equalsIgnoreCase("unban") && VaultHelper.checkPerm(player, "askyblock.island.ban")) {
                                if (VaultHelper.checkPerm(player, "askyblock.island.ban")) {
                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(playerUUID).helpColor + "/" + label + " unban <player>: " + ChatColor.WHITE + this.plugin.myLocale(playerUUID).islandhelpUnban);
                                }
                                else {
                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(playerUUID).errorNoPermission);
                                }
                                return true;
                            }
                            if (split[0].equalsIgnoreCase("make")) {
                                if (!this.pendingNewIslandSelection.contains(playerUUID)) {
                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale().errorUnknownCommand);
                                    return false;
                                }
                                this.pendingNewIslandSelection.remove(playerUUID);
                                final Island oldIsland = this.plugin.getGrid().getIsland(player.getUniqueId());
                                this.newIsland(player);
                                if (this.resettingIsland.contains(playerUUID)) {
                                    this.resettingIsland.remove(playerUUID);
                                    this.resetPlayer(player, oldIsland);
                                }
                                return true;
                            }
                            else {
                                if (split[0].equalsIgnoreCase("lang")) {
                                    if (VaultHelper.checkPerm(player, "askyblock.island.lang")) {
                                        Util.sendMessage((CommandSender)player, "/" + label + " lang <#>");
                                        this.displayLocales(player);
                                    }
                                    else {
                                        Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).errorNoPermission);
                                    }
                                    return true;
                                }
                                if (split[0].equalsIgnoreCase("settings")) {
                                    if (VaultHelper.checkPerm(player, "askyblock.island.settings")) {
                                        try {
                                            player.openInventory(this.plugin.getSettingsPanel().islandGuardPanel(player));
                                        }
                                        catch (Exception e) {
                                            Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorCommandNotReady);
                                        }
                                    }
                                    else {
                                        Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoPermission);
                                    }
                                    return true;
                                }
                                if (split[0].equalsIgnoreCase("lock")) {
                                    if (!VaultHelper.checkPerm(player, "askyblock.island.lock")) {
                                        Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoPermission);
                                        return true;
                                    }
                                    final Island island = this.plugin.getGrid().getIsland(playerUUID);
                                    if (island == null) {
                                        Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoIsland);
                                        return true;
                                    }
                                    if (!island.isLocked()) {
                                        for (final Player target : this.plugin.getServer().getOnlinePlayers()) {
                                            if (!player.equals(target) && !target.isOp() && !VaultHelper.checkPerm(target, "askyblock.mod.bypassprotect") && this.plugin.getGrid().isOnIsland(player, target) && !CoopPlay.getInstance().getCoopPlayers(island.getCenter()).contains(target.getUniqueId())) {
                                                if (this.plugin.getPlayers().inTeam(target.getUniqueId()) || this.plugin.getPlayers().hasIsland(target.getUniqueId())) {
                                                    this.plugin.getGrid().homeTeleport(target);
                                                }
                                                else if (!target.performCommand("spawn")) {
                                                    target.teleport(player.getWorld().getSpawnLocation());
                                                }
                                                Util.sendMessage((CommandSender)target, ChatColor.RED + this.plugin.myLocale(target.getUniqueId()).expelExpelled);
                                                this.plugin.getLogger().info(player.getName() + " expelled " + target.getName() + " from their island when locking.");
                                                Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).expelSuccess.replace("[name]", target.getName()));
                                            }
                                        }
                                        Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(playerUUID).lockLocking);
                                        this.plugin.getMessages().tellOfflineTeam(playerUUID, this.plugin.myLocale(playerUUID).lockPlayerLocked.replace("[name]", player.getName()));
                                        this.plugin.getMessages().tellTeam(playerUUID, this.plugin.myLocale(playerUUID).lockPlayerLocked.replace("[name]", player.getName()));
                                        island.setLocked(true);
                                    }
                                    else {
                                        Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(playerUUID).lockUnlocking);
                                        this.plugin.getMessages().tellOfflineTeam(playerUUID, this.plugin.myLocale(playerUUID).lockPlayerUnlocked.replace("[name]", player.getName()));
                                        this.plugin.getMessages().tellTeam(playerUUID, this.plugin.myLocale(playerUUID).lockPlayerUnlocked.replace("[name]", player.getName()));
                                        island.setLocked(false);
                                    }
                                    return true;
                                }
                                else if (split[0].equalsIgnoreCase("go")) {
                                    if (!VaultHelper.checkPerm(player, "askyblock.island.go")) {
                                        Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoPermission);
                                        return true;
                                    }
                                    if (!this.plugin.getPlayers().hasIsland(playerUUID) && !this.plugin.getPlayers().inTeam(playerUUID)) {
                                        Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).errorNoIsland);
                                        return true;
                                    }
                                    this.plugin.getGrid().homeTeleport(player);
                                    if (Settings.islandRemoveMobs) {
                                        this.plugin.getGrid().removeMobs(player.getLocation());
                                    }
                                    return true;
                                }
                                else {
                                    if (split[0].equalsIgnoreCase("about")) {
                                        Util.sendMessage((CommandSender)player, ChatColor.GOLD + "This plugin is free software: you can redistribute");
                                        Util.sendMessage((CommandSender)player, ChatColor.GOLD + "it and/or modify it under the terms of the GNU");
                                        Util.sendMessage((CommandSender)player, ChatColor.GOLD + "General Public License as published by the Free");
                                        Util.sendMessage((CommandSender)player, ChatColor.GOLD + "Software Foundation, either version 3 of the License,");
                                        Util.sendMessage((CommandSender)player, ChatColor.GOLD + "or (at your option) any later version.");
                                        Util.sendMessage((CommandSender)player, ChatColor.GOLD + "This plugin is distributed in the hope that it");
                                        Util.sendMessage((CommandSender)player, ChatColor.GOLD + "will be useful, but WITHOUT ANY WARRANTY; without");
                                        Util.sendMessage((CommandSender)player, ChatColor.GOLD + "even the implied warranty of MERCHANTABILITY or");
                                        Util.sendMessage((CommandSender)player, ChatColor.GOLD + "FITNESS FOR A PARTICULAR PURPOSE.  See the");
                                        Util.sendMessage((CommandSender)player, ChatColor.GOLD + "GNU General Public License for more details.");
                                        Util.sendMessage((CommandSender)player, ChatColor.GOLD + "You should have received a copy of the GNU");
                                        Util.sendMessage((CommandSender)player, ChatColor.GOLD + "General Public License along with this plugin.");
                                        Util.sendMessage((CommandSender)player, ChatColor.GOLD + "If not, see <http://www.gnu.org/licenses/>.");
                                        Util.sendMessage((CommandSender)player, ChatColor.GOLD + "Souce code is available on GitHub.");
                                        Util.sendMessage((CommandSender)player, ChatColor.GOLD + "(c) 2014 - 2015 by tastybento");
                                        return true;
                                    }
                                    if ((split[0].equalsIgnoreCase("controlpanel") || split[0].equalsIgnoreCase("cp")) && VaultHelper.checkPerm(player, "askyblock.island.controlpanel")) {
                                        if (ControlPanel.controlPanel.containsKey(ControlPanel.getDefaultPanelName())) {
                                        	//LABEL Контрол панелька
                                            player.openInventory((Inventory)ControlPanel.controlPanel.get(ControlPanel.getDefaultPanelName()));
                                            return true;
                                        }
                                        Util.sendMessage((CommandSender)player, this.plugin.myLocale(playerUUID).errorCommandNotReady);
                                        this.plugin.getLogger().severe("There is a problem with the controlpanel.yml file - it is probably corrupted. Delete it and let it be regenerated.");
                                        return true;
                                    }
                                    else {
                                        if (split[0].equalsIgnoreCase("minishop") || split[0].equalsIgnoreCase("ms")) {
                                            if (!Settings.useEconomy || !Settings.useMinishop) {
                                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorMinishopDisabled);
                                                return true;
                                            }
                                            if (this.plugin.getGrid().getIsland(player.getUniqueId()) == null) {
                                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoIsland);
                                                return true;
                                            }
                                            if (!player.getWorld().equals(ASkyBlock.getIslandWorld()) && !player.getWorld().equals(ASkyBlock.getNetherWorld())) {
                                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorWrongWorld);
                                                return true;
                                            }
                                            if (VaultHelper.checkPerm(player, "askyblock.island.minishop")) {
                                                if (ControlPanel.miniShop != null) {
                                                    player.openInventory(ControlPanel.miniShop);
                                                }
                                                else {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(playerUUID).errorCommandNotReady);
                                                    this.plugin.getLogger().severe("Player tried to open the minishop, but it does not exist. Look for errors in the console about the minishop loading.");
                                                }
                                                return true;
                                            }
                                        }
                                        if (split[0].equalsIgnoreCase("warp")) {
                                            if (VaultHelper.checkPerm(player, "askyblock.island.warp")) {
                                                Util.sendMessage((CommandSender)player, ChatColor.YELLOW + "/island warp <player>: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpWarp);
                                                return true;
                                            }
                                            break Label_14129;
                                        }
                                        else if (split[0].equalsIgnoreCase("warps")) {
                                            if (!VaultHelper.checkPerm(player, "askyblock.island.warp")) {
                                                break Label_14129;
                                            }
                                            final Collection<UUID> warpList = (Collection<UUID>)this.plugin.getWarpSignsListener().listWarps();
                                            if (warpList.isEmpty()) {
                                                Util.sendMessage((CommandSender)player, ChatColor.YELLOW + this.plugin.myLocale(player.getUniqueId()).warpserrorNoWarpsYet);
                                                if (VaultHelper.checkPerm(player, "askyblock.island.addwarp") && this.plugin.getGrid().playerIsOnIsland(player)) {
                                                    Util.sendMessage((CommandSender)player, ChatColor.YELLOW + this.plugin.myLocale().warpswarpTip);
                                                }
                                                return true;
                                            }
                                            if (Settings.useWarpPanel) {
                                                player.openInventory(this.plugin.getWarpPanel().getWarpPanel(0));
                                            }
                                            else {
                                                Boolean hasWarp = false;
                                                String wlist = "";
                                                for (final UUID w : warpList) {
                                                    if (w == null) {
                                                        continue;
                                                    }
                                                    if (wlist.isEmpty()) {
                                                        wlist = this.plugin.getPlayers().getName(w);
                                                    }
                                                    else {
                                                        wlist = wlist + ", " + this.plugin.getPlayers().getName(w);
                                                    }
                                                    if (!w.equals(playerUUID)) {
                                                        continue;
                                                    }
                                                    hasWarp = true;
                                                }
                                                Util.sendMessage((CommandSender)player, ChatColor.YELLOW + this.plugin.myLocale(player.getUniqueId()).warpswarpsAvailable + ": " + ChatColor.WHITE + wlist);
                                                if (!hasWarp && VaultHelper.checkPerm(player, "askyblock.island.addwarp")) {
                                                    Util.sendMessage((CommandSender)player, ChatColor.YELLOW + this.plugin.myLocale().warpswarpTip);
                                                }
                                            }
                                            return true;
                                        }
                                        
                                        else 
                                        
                                        	if (split[0].equalsIgnoreCase("restart") || split[0].equalsIgnoreCase("reset")) {
                                        		
                                        	//исключение пермишена
                                            if (!VaultHelper.checkPerm(player, "askyblock.island.reset")) {
                                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoPermission);
                                                return true;
                                            }
                                            //исключение отсутствия острова
                                            if (!this.plugin.getPlayers().hasIsland(playerUUID)) {
                                                player.performCommand("island");
                                                return true;
                                            }
                                            //исключение наличия игрока в команде
                                            if (this.plugin.getPlayers().inTeam(playerUUID)) {
                                                if (!this.plugin.getPlayers().getTeamLeader(playerUUID).equals(playerUUID)) {
                                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).islandresetOnlyOwner);
                                                }
                                                else {
                                                    Util.sendMessage((CommandSender)player, ChatColor.YELLOW + this.plugin.myLocale(player.getUniqueId()).islandresetMustRemovePlayers);
                                                }
                                                return true;
                                            }
                                            //исключение
                                            if (this.plugin.getPlayers().getResetsLeft(playerUUID) == 0) {
                                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).islandResetNoMore);
                                                return true;
                                            }//исключение
                                            if (this.plugin.getPlayers().getResetsLeft(playerUUID) > 0) {
                                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).resetYouHave.replace("[number]", String.valueOf(this.plugin.getPlayers().getResetsLeft(playerUUID))));
                                            }
                                            
                                            
                                            
                                            if (!this.onRestartWaitTime(player) || Settings.resetWait == 0 || player.isOp()) {
                                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).islandresetConfirm.replace("[seconds]", String.valueOf(Settings.resetConfirmWait)));
                                                if (!this.confirm.containsKey(playerUUID) || !this.confirm.get(playerUUID)) {
                                                    this.confirm.put(playerUUID, true);                                                    
                                                    this.plugin.getServer().getScheduler().runTaskLater((Plugin)this.plugin,                                                    		
                                                    	//	(Runnable)new IslandCmd.IslandCmd$3(this, playerUUID), Settings.resetConfirmWait * 20L);
                                                    		new Runnable(){
                                                    		
                                                    	@Override
                                                        public void run() {
                                                            IslandCmd.this.confirm.put(playerUUID, false);
                                                        }
                                                    }, Settings.resetConfirmWait * 20);
                                                    return true;
                                                    
                                                }
                                                return true;
                                            }
                                            
                                            Util.sendMessage((CommandSender)player, ChatColor.YELLOW + this.plugin.myLocale(player.getUniqueId()).islandresetWait.replace("[time]", String.valueOf(this.getResetWaitTime(player))));
                                            return true;
                                        }
                                        
                                        
                                        else if (split[0].equalsIgnoreCase("confirm")) {
                                            if (this.confirm.containsKey(playerUUID) && this.confirm.get(playerUUID)) {
                                                this.confirm.remove(playerUUID);
                                                Util.sendMessage((CommandSender)player, ChatColor.YELLOW + this.plugin.myLocale(player.getUniqueId()).islandresetPleaseWait);
                                                if (this.plugin.getPlayers().getResetsLeft(playerUUID) == 0) {
                                                    Util.sendMessage((CommandSender)player, ChatColor.YELLOW + this.plugin.myLocale(player.getUniqueId()).islandResetNoMore);
                                                }
                                                if (this.plugin.getPlayers().getResetsLeft(playerUUID) > 0) {
                                                    Util.sendMessage((CommandSender)player, ChatColor.YELLOW + this.plugin.myLocale(player.getUniqueId()).resetYouHave.replace("[number]", String.valueOf(this.plugin.getPlayers().getResetsLeft(playerUUID))));
                                                }
                                                List<Schematic> schems = this.getSchematics(player, false);
                                                final Island oldIsland2 = this.plugin.getGrid().getIsland(player.getUniqueId());
                                                if (schems.isEmpty()) {
                                                    this.newIsland(player);
                                                    this.resetPlayer(player, oldIsland2);
                                                }
                                                else if (schems.size() == 1) {
                                                    this.newIsland(player, schems.get(0));
                                                    this.resetPlayer(player, oldIsland2);
                                                }
                                                else if (Settings.useSchematicPanel) {
                                                    this.pendingNewIslandSelection.add(playerUUID);
                                                    this.resettingIsland.add(playerUUID);
                                                    player.openInventory(this.plugin.getSchematicsPanel().getPanel(player));
                                                }
                                                else {
                                                    schems = this.getSchematics(player, true);
                                                    if (schems.isEmpty()) {
                                                        this.newIsland(player);
                                                    }
                                                    else if (Settings.chooseIslandRandomly) {
                                                        this.newIsland(player, schems.get(this.random.nextInt(schems.size())));
                                                    }
                                                    else {
                                                        this.newIsland(player, schems.get(0));
                                                    }
                                                    this.resetPlayer(player, oldIsland2);
                                                }
                                                return true;
                                            }
                                            Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/island restart: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpRestart);
                                            return true;
                                        }
                                        else if (split[0].equalsIgnoreCase("sethome")) {
                                            if (!VaultHelper.checkPerm(player, "askyblock.island.sethome")) {
                                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).errorNoPermission);
                                                return true;
                                            }
                                            if (this.plugin.getGrid().getIsland(player.getUniqueId()) == null) {
                                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoIsland);
                                                return true;
                                            }
                                            this.plugin.getGrid().homeSet(player);
                                            return true;
                                        }
                                        else {
                                            if (split[0].equalsIgnoreCase("help")) {
                                                Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.getName() + " " + this.plugin.getDescription().getVersion() + " help:");
                                                if (Settings.useControlPanel) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + ": " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpControlPanel);
                                                }
                                                else {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + ": " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpIsland);
                                                }
                                                int maxHomes = Settings.maxHomes;
                                                for (final PermissionAttachmentInfo perms2 : player.getEffectivePermissions()) {
                                                    if (perms2.getPermission().startsWith("askyblock.island.maxhomes.")) {
                                                        if (perms2.getPermission().startsWith("askyblock.island.maxhomes.*")) {
                                                            maxHomes = Settings.maxHomes;
                                                            break;
                                                        }
                                                        final String[] spl2 = perms2.getPermission().split("askyblock.island.maxhomes.");
                                                        if (spl2.length > 1) {
                                                            if (!NumberUtils.isDigits(spl2[1])) {
                                                                this.plugin.getLogger().severe("Player " + player.getName() + " has permission: " + perms2.getPermission() + " <-- the last part MUST be a number! Ignoring...");
                                                            }
                                                            else {
                                                                maxHomes = Math.max(maxHomes, Integer.valueOf(spl2[1]));
                                                            }
                                                        }
                                                    }
                                                    if (maxHomes < 1) {
                                                        maxHomes = 1;
                                                    }
                                                }
                                                if (maxHomes > 1 && VaultHelper.checkPerm(player, "askyblock.island.go")) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " go <1 - " + maxHomes + ">: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpTeleport);
                                                }
                                                else if (VaultHelper.checkPerm(player, "askyblock.island.go")) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " go: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpTeleport);
                                                }
                                                if (this.plugin.getGrid() != null && this.plugin.getGrid().getSpawn() != null && VaultHelper.checkPerm(player, "askyblock.island.spawn")) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " spawn: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpSpawn);
                                                }
                                                if (VaultHelper.checkPerm(player, "askyblock.island.controlpanel")) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " controlpanel or cp [on/off]: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpControlPanel);
                                                }
                                                if (VaultHelper.checkPerm(player, "askyblock.island.reset")) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " reset: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpRestart);
                                                }
                                                if (VaultHelper.checkPerm(player, "askyblock.island.sethome")) {
                                                    if (maxHomes > 1) {
                                                        Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " sethome <1 - " + maxHomes + ">: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpSetHome);
                                                    }
                                                    else {
                                                        Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " sethome: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpSetHome);
                                                    }
                                                }
                                                if (VaultHelper.checkPerm(player, "askyblock.island.info")) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " level: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpLevel);
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " level <player>: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpLevelPlayer);
                                                }
                                                if (VaultHelper.checkPerm(player, "askyblock.island.name") && this.plugin.getPlayers().hasIsland(playerUUID)) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " name <name>: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandHelpName);
                                                }
                                                if (VaultHelper.checkPerm(player, "askyblock.island.topten")) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " top: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpTop);
                                                }
                                                if (Settings.useEconomy && VaultHelper.checkPerm(player, "askyblock.island.minishop")) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " minishop or ms: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpMiniShop);
                                                }
                                                if (VaultHelper.checkPerm(player, "askyblock.island.value")) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " value: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpValue);
                                                }
                                                if (VaultHelper.checkPerm(player, "askyblock.island.warp")) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " warps: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpWarps);
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " warp <player>: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpWarp);
                                                }
                                                if (VaultHelper.checkPerm(player, "askyblock.team.create")) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " team: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpTeam);
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " invite <player>: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpInvite);
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " leave: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpLeave);
                                                }
                                                if (VaultHelper.checkPerm(player, "askyblock.team.kick")) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " kick <player>: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpKick);
                                                }
                                                if (VaultHelper.checkPerm(player, "askyblock.team.join")) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " <accept/reject>: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpAcceptReject);
                                                }
                                                if (VaultHelper.checkPerm(player, "askyblock.team.makeleader")) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " makeleader <player>: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpMakeLeader);
                                                }
                                                if (VaultHelper.checkPerm(player, "askyblock.team.chat") && this.plugin.getPlayers().inTeam(playerUUID)) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " teamchat: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).teamChatHelp);
                                                }
                                                if (VaultHelper.checkPerm(player, "askyblock.island.biomes")) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " biomes: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpBiome);
                                                }
                                                if (VaultHelper.checkPerm(player, "askyblock.island.expel")) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " expel <player>: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpExpel);
                                                }
                                                if (VaultHelper.checkPerm(player, "askyblock.island.ban")) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " ban <player>: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpBan);
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " banlist <player>: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpBanList);
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " unban <player>: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpUnban);
                                                }
                                                if (VaultHelper.checkPerm(player, "askyblock.coop")) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " coop <player>: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpCoop);
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " uncoop <player>: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpUnCoop);
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " listcoops: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpListCoops);
                                                }
                                                if (VaultHelper.checkPerm(player, "askyblock.island.lock")) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " lock: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandHelpLock);
                                                }
                                                if (VaultHelper.checkPerm(player, "askyblock.island.name") && this.plugin.getPlayers().hasIsland(playerUUID)) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " resetname: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpResetName);
                                                }
                                                if (VaultHelper.checkPerm(player, "askyblock.island.settings")) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " settings: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandHelpSettings);
                                                }
                                                if (VaultHelper.checkPerm(player, "askyblock.island.challenges")) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + this.plugin.myLocale(player.getUniqueId()).islandHelpChallenges);
                                                }
                                                if (VaultHelper.checkPerm(player, "askyblock.island.lang")) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " lang <#>: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandHelpSelectLanguage);
                                                }
                                                return true;
                                            }
                                            if (split[0].equalsIgnoreCase("listcoops")) {
                                                if (!VaultHelper.checkPerm(player, "askyblock.coop")) {
                                                    break Label_14129;
                                                }
                                                if (!this.plugin.getPlayers().hasIsland(playerUUID)) {
                                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoIsland);
                                                    return true;
                                                }
                                                final Island island = this.plugin.getGrid().getIsland(playerUUID);
                                                boolean none = true;
                                                for (final UUID uuid : CoopPlay.getInstance().getCoopPlayers(island.getCenter())) {
                                                    Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.getPlayers().getName(uuid));
                                                    none = false;
                                                }
                                                if (none) {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(playerUUID).helpColor + "/" + label + " coop <player>: " + ChatColor.WHITE + this.plugin.myLocale(player.getUniqueId()).islandhelpCoop);
                                                }
                                                else {
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(playerUUID).helpColor + this.plugin.myLocale(playerUUID).coopUseExpel);
                                                }
                                                return true;
                                            }
                                            else if (split[0].equalsIgnoreCase("biomes")) {
                                                if (!VaultHelper.checkPerm(player, "askyblock.island.biomes")) {
                                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoPermission);
                                                    return true;
                                                }
                                                if (teamLeader != null && !teamLeader.equals(playerUUID)) {
                                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).levelerrornotYourIsland);
                                                    return true;
                                                }
                                                if (!this.plugin.getPlayers().hasIsland(playerUUID)) {
                                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoIsland);
                                                    return true;
                                                }
                                                if (!this.plugin.getGrid().playerIsOnIsland(player)) {
                                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).challengeserrorNotOnIsland);
                                                    return true;
                                                }
                                                if (this.plugin.getPlayers().getIslandLocation(playerUUID).getWorld().getEnvironment().equals((Object)World.Environment.NETHER)) {
                                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorWrongWorld);
                                                    return true;
                                                }
                                                final Inventory inv = this.plugin.getBiomes().getBiomePanel(player);
                                                if (inv != null) {
                                                    player.openInventory(inv);
                                                }
                                                return true;
                                            }
                                            else {
                                                if (split[0].equalsIgnoreCase("spawn") && this.plugin.getGrid().getSpawn() != null) {
                                                    if (VaultHelper.checkPerm(player, "askyblock.island.spawn")) {
                                                        Location l = ASkyBlock.getIslandWorld().getSpawnLocation();
                                                        l.add(new Vector(0.5, 0.0, 0.5));
                                                        final Island spawn = this.plugin.getGrid().getSpawn();
                                                        if (spawn != null && spawn.getSpawnPoint() != null) {
                                                            l = spawn.getSpawnPoint();
                                                        }
                                                        player.teleport(l);
                                                    }
                                                    else {
                                                        Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).errorNoPermission);
                                                    }
                                                    return true;
                                                }
                                                if (split[0].equalsIgnoreCase("top")) {
                                                    if (VaultHelper.checkPerm(player, "askyblock.island.topten")) {
                                                        TopTen.topTenShow(player);
                                                        return true;
                                                    }
                                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).errorNoPermission);
                                                    return true;
                                                }
                                                else if (split[0].equalsIgnoreCase("level")) {
                                                    if (!VaultHelper.checkPerm(player, "askyblock.island.info")) {
                                                        Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).errorNoPermission);
                                                        return true;
                                                    }
                                                    if (!this.plugin.getPlayers().inTeam(playerUUID) && !this.plugin.getPlayers().hasIsland(playerUUID)) {
                                                        Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoIsland);
                                                        return true;
                                                    }
                                                    if (!VaultHelper.checkPerm(player, "askyblock.intopten")) {
                                                        Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).topTenerrorExcluded.replace("[perm]", "askyblock.intopten"));
                                                    }
                                                    this.calculateIslandLevel((CommandSender)player, playerUUID);
                                                    return true;
                                                }
                                                else if (split[0].equalsIgnoreCase("invite")) {
                                                    if (!VaultHelper.checkPerm(player, "askyblock.team.create")) {
                                                        Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).errorNoPermission);
                                                        return true;
                                                    }
                                                    Util.sendMessage((CommandSender)player, this.plugin.myLocale(player.getUniqueId()).invitehelp);
                                                    if (!this.plugin.getPlayers().inTeam(playerUUID)) {
                                                        return true;
                                                    }
                                                    if (teamLeader.equals(playerUUID)) {
                                                        int maxSize = Settings.maxTeamSize;
                                                        for (final PermissionAttachmentInfo perms2 : player.getEffectivePermissions()) {
                                                            if (perms2.getPermission().startsWith("askyblock.team.maxsize.")) {
                                                                if (perms2.getPermission().contains("askyblock.team.maxsize.*")) {
                                                                    maxSize = Settings.maxTeamSize;
                                                                    break;
                                                                }
                                                                final String[] spl2 = perms2.getPermission().split("askyblock.team.maxsize.");
                                                                if (spl2.length > 1) {
                                                                    if (!NumberUtils.isDigits(spl2[1])) {
                                                                        this.plugin.getLogger().severe("Player " + player.getName() + " has permission: " + perms2.getPermission() + " <-- the last part MUST be a number! Ignoring...");
                                                                    }
                                                                    else {
                                                                        maxSize = Math.max(maxSize, Integer.valueOf(spl2[1]));
                                                                    }
                                                                }
                                                            }
                                                            if (maxSize < 1) {
                                                                maxSize = 1;
                                                            }
                                                        }
                                                        if (VaultHelper.checkPerm(player, "askyblock.team.vip") && Settings.maxTeamSizeVIP > maxSize) {
                                                            maxSize = Settings.maxTeamSizeVIP;
                                                        }
                                                        if (VaultHelper.checkPerm(player, "askyblock.team.vip2") && Settings.maxTeamSizeVIP2 > maxSize) {
                                                            maxSize = Settings.maxTeamSizeVIP2;
                                                        }
                                                        if (teamMembers.size() < maxSize) {
                                                            Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).inviteyouCanInvite.replace("[number]", String.valueOf(maxSize - teamMembers.size())));
                                                        }
                                                        else {
                                                            Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).inviteerrorYourIslandIsFull);
                                                        }
                                                        return true;
                                                    }
                                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).inviteerrorYouMustHaveIslandToInvite);
                                                    return true;
                                                }
                                                else if (split[0].equalsIgnoreCase("accept")) {
                                                	//Исключение пермишена
                                                    if (!VaultHelper.checkPerm(player, "askyblock.team.join")) {
                                                        Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).errorNoPermission);
                                                        return true;
                                                    }
                                                    if (!this.plugin.getPlayers().inTeam(playerUUID) && this.inviteList.containsKey(playerUUID)) {
                                                        if (this.plugin.getPlayers().hasIsland(playerUUID)) {
                                                            this.plugin.getLogger().info(player.getName() + "'s island will be deleted because they joined a party.");
                                                            this.plugin.deletePlayerIsland(playerUUID, true);
                                                            WGHelper.deleteProtectedRegion(sender);
                                                            WGHelper.deleteNetherProtectedRegion(sender);
                                                            this.plugin.getLogger().info("Island deleted.");
                                                        }
                                                        
                                                        this.addPlayertoTeam(playerUUID, this.inviteList.get(playerUUID));
                                                        if (!this.plugin.getPlayers().inTeam((UUID)this.inviteList.get(playerUUID))) {
                                                            this.addPlayertoTeam(this.inviteList.get(playerUUID), this.inviteList.get(playerUUID));
                                                        }
                                                        
                                                        this.setResetWaitTime(player);
                                                        if (Settings.teamJoinDeathReset) {
                                                            this.plugin.getPlayers().setDeaths(player.getUniqueId(), 0);
                                                        }
                                                        
                                                        this.plugin.getGrid().homeTeleport(player);
                                                        this.plugin.resetPlayer(player);
                                                        
                                                        if (!player.hasPermission("askyblock.command.newteamexempt")) {
                                                            runCommands(Settings.teamStartCommands, (OfflinePlayer)player);
                                                        }
                                                        Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).inviteyouHaveJoinedAnIsland);
                                                        if (this.plugin.getServer().getPlayer((UUID)this.inviteList.get(playerUUID)) != null) {
                                                            Util.sendMessage((CommandSender)this.plugin.getServer().getPlayer((UUID)this.inviteList.get(playerUUID)), ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).invitehasJoinedYourIsland.replace("[name]", player.getName()));
                                                        }
                                                        this.inviteList.remove(player.getUniqueId());
                                                        this.plugin.getGrid().saveGrid();
                                                        return true;
                                                    }
                                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorCommandNotReady);
                                                    return true;
                                                }
                                                else {
                                                    if (split[0].equalsIgnoreCase("reject")) {
                                                        if (this.inviteList.containsKey(player.getUniqueId())) {
                                                            Util.sendMessage((CommandSender)player, ChatColor.YELLOW + this.plugin.myLocale(player.getUniqueId()).rejectyouHaveRejectedInvitation);
                                                            if (Bukkit.getPlayer((UUID)this.inviteList.get(player.getUniqueId())) != null) {
                                                                Util.sendMessage((CommandSender)Bukkit.getPlayer((UUID)this.inviteList.get(player.getUniqueId())), ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).rejectnameHasRejectedInvite.replace("[name]", player.getName()));
                                                            }
                                                            this.inviteList.remove(player.getUniqueId());
                                                        }
                                                        else {
                                                            Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).rejectyouHaveNotBeenInvited);
                                                        }
                                                        return true;
                                                    }
                                                    if (split[0].equalsIgnoreCase("leave")) {
                                                        if (!VaultHelper.checkPerm(player, "askyblock.team.join")) {
                                                            Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).errorNoPermission);
                                                            return true;
                                                        }
                                                        if (!player.getWorld().equals(ASkyBlock.getIslandWorld()) && (!Settings.createNether || !Settings.newNether || ASkyBlock.getNetherWorld() == null || !player.getWorld().equals(ASkyBlock.getNetherWorld()))) {
                                                            Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).leaveerrorYouMustBeInWorld);
                                                            return true;
                                                        }
                                                        if (!this.plugin.getPlayers().inTeam(playerUUID)) {
                                                            Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).leaveerrorYouCannotLeaveIsland);
                                                            return true;
                                                        }
                                                        if (this.plugin.getPlayers().getTeamLeader(playerUUID) != null && this.plugin.getPlayers().getTeamLeader(playerUUID).equals(playerUUID)) {
                                                            Util.sendMessage((CommandSender)player, ChatColor.YELLOW + this.plugin.myLocale(player.getUniqueId()).leaveerrorYouAreTheLeader);
                                                            return true;
                                                        }
                                                        if (!this.leavingPlayers.contains(playerUUID)) {
                                                            this.leavingPlayers.add(playerUUID);
                                                            Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).leaveWarning);
                                                            new BukkitRunnable(){

                                                                public void run() {
                                                                    if (IslandCmd.this.leavingPlayers.contains(playerUUID)) {
                                                                        IslandCmd.this.leavingPlayers.remove(playerUUID);
                                                                    }
                                                                }
                                                            }.runTaskLater((Plugin)this.plugin, Settings.resetConfirmWait * 20);
                                                            return true;
                                                        }
                                                        this.leavingPlayers.remove(playerUUID);
                                                        if (!this.removePlayerFromTeam(playerUUID, teamLeader)) {
                                                            return true;
                                                        }
                                                        CoopPlay.getInstance().clearMyInvitedCoops(player);
                                                        CoopPlay.getInstance().clearMyCoops(player);
                                                        this.plugin.getPlayers().startInviteCoolDownTimer(playerUUID, this.plugin.getPlayers().getTeamIslandLocation(teamLeader));
                                                        this.plugin.getWarpSignsListener().removeWarp(playerUUID);
                                                        Util.sendMessage((CommandSender)player, ChatColor.YELLOW + this.plugin.myLocale(player.getUniqueId()).leaveyouHaveLeftTheIsland);
                                                        if (this.plugin.getServer().getPlayer(teamLeader) != null) {
                                                            final Player leader = this.plugin.getServer().getPlayer(teamLeader);
                                                            Util.sendMessage((CommandSender)leader, ChatColor.RED + this.plugin.myLocale(teamLeader).leavenameHasLeftYourIsland.replace("[name]", player.getName()));
                                                        }
                                                        else {
                                                            this.plugin.getMessages().setMessage(teamLeader, ChatColor.RED + this.plugin.myLocale(teamLeader).leavenameHasLeftYourIsland.replace("[name]", player.getName()));
                                                        }
                                                        if (teamMembers.size() < 2 && !this.removePlayerFromTeam(teamLeader, teamLeader)) {
                                                            return true;
                                                        }
                                                        this.plugin.resetPlayer(player);
                                                        if (!player.performCommand("spawn")) {
                                                            player.teleport(player.getWorld().getSpawnLocation());
                                                        }
                                                        return true;
                                                    }
                                                   
                                                    else {
                                                        if (split[0].equalsIgnoreCase("team")) {
                                                            if (this.plugin.getPlayers().inTeam(playerUUID)) {
                                                                if (teamLeader.equals(playerUUID)) {
                                                                    int maxSize = Settings.maxTeamSize;
                                                                    for (final PermissionAttachmentInfo perms2 : player.getEffectivePermissions()) {
                                                                        if (perms2.getPermission().startsWith("askyblock.team.maxsize.")) {
                                                                            if (perms2.getPermission().contains("askyblock.team.maxsize.*")) {
                                                                                maxSize = Settings.maxTeamSize;
                                                                                break;
                                                                            }
                                                                            final String[] spl2 = perms2.getPermission().split("askyblock.team.maxsize.");
                                                                            if (spl2.length > 1) {
                                                                                if (!NumberUtils.isDigits(spl2[1])) {
                                                                                    this.plugin.getLogger().severe("Player " + player.getName() + " has permission: " + perms2.getPermission() + " <-- the last part MUST be a number! Ignoring...");
                                                                                }
                                                                                else {
                                                                                    maxSize = Math.max(maxSize, Integer.valueOf(spl2[1]));
                                                                                }
                                                                            }
                                                                        }
                                                                        if (maxSize < 1) {
                                                                            maxSize = 1;
                                                                        }
                                                                    }
                                                                    if (teamMembers.size() < maxSize) {
                                                                        Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).inviteyouCanInvite.replace("[number]", String.valueOf(maxSize - teamMembers.size())));
                                                                    }
                                                                    else {
                                                                        Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).inviteerrorYourIslandIsFull);
                                                                    }
                                                                }
                                                                Util.sendMessage((CommandSender)player, ChatColor.YELLOW + this.plugin.myLocale(player.getUniqueId()).teamlistingMembers + ":");
                                                                for (final UUID m : this.plugin.getPlayers().getMembers(teamLeader)) {
                                                                    Util.sendMessage((CommandSender)player, ChatColor.WHITE + this.plugin.getPlayers().getName(m));
                                                                }
                                                            }
                                                            else if (this.inviteList.containsKey(playerUUID)) {
                                                                Util.sendMessage((CommandSender)player, ChatColor.YELLOW + this.plugin.myLocale(player.getUniqueId()).invitenameHasInvitedYou.replace("[name]", this.plugin.getPlayers().getName((UUID)this.inviteList.get(playerUUID))));
                                                                Util.sendMessage((CommandSender)player, ChatColor.WHITE + "/" + label + " [accept/reject]" + ChatColor.YELLOW + this.plugin.myLocale(player.getUniqueId()).invitetoAcceptOrReject);
                                                            }
                                                            else {
                                                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).kickerrorNoTeam);
                                                            }
                                                            return true;
                                                        }
                                                        Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).errorUnknownCommand);
                                                        return true;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        //FIXME
                        //break;
                    }
                    case 2: {
                        if (split[0].equalsIgnoreCase("controlpanel") || split[0].equalsIgnoreCase("cp")) {
                            if (VaultHelper.checkPerm(player, "askyblock.island.controlpanel")) {
                                if (split[1].equalsIgnoreCase("on")) {
                                    this.plugin.getPlayers().setControlPanel(playerUUID, true);
                                }
                                else if (split[1].equalsIgnoreCase("off")) {
                                    this.plugin.getPlayers().setControlPanel(playerUUID, false);
                                }
                                Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(playerUUID).generalSuccess);
                                return true;
                            }
                            Util.sendMessage((CommandSender)player, this.plugin.myLocale(playerUUID).errorNoPermission);
                            return true;
                        }
                        
                        
                        
                        else if (split[0].equalsIgnoreCase("warps")) {
                            if (!Settings.useWarpPanel) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).errorUnknownCommand);
                                return true;
                            }
                            if (!VaultHelper.checkPerm(player, "askyblock.island.warp")) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).errorNoPermission);
                                break;
                            }
                            final Set<UUID> warpList2 = (Set<UUID>)this.plugin.getWarpSignsListener().listWarps();
                            if (warpList2.isEmpty()) {
                                Util.sendMessage((CommandSender)player, ChatColor.YELLOW + this.plugin.myLocale(player.getUniqueId()).warpserrorNoWarpsYet);
                                if (VaultHelper.checkPerm(player, "askyblock.island.addwarp") && this.plugin.getGrid().playerIsOnIsland(player)) {
                                    Util.sendMessage((CommandSender)player, ChatColor.YELLOW + this.plugin.myLocale().warpswarpTip);
                                }
                                return true;
                            }
                            int panelNum = 0;
                            try {
                                panelNum = Integer.valueOf(split[1]) - 1;
                            }
                            catch (Exception e2) {
                                panelNum = 0;
                            }
                            player.openInventory(this.plugin.getWarpPanel().getWarpPanel(panelNum));
                            return true;
                        }
                        else if (split[0].equalsIgnoreCase("make")) {
                            if (!this.pendingNewIslandSelection.contains(playerUUID)) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).errorUnknownCommand);
                                return true;
                            }
                            this.pendingNewIslandSelection.remove(playerUUID);
                            if (!IslandCmd.schematics.containsKey(split[1])) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).errorUnknownCommand);
                                return true;
                            }
                            final Schematic schematic = IslandCmd.schematics.get(split[1]);
                            if (schematic.getPerm().isEmpty() || VaultHelper.checkPerm(player, schematic.getPerm())) {
                                final Island oldIsland2 = this.plugin.getGrid().getIsland(player.getUniqueId());
                                this.newIsland(player, schematic);
                                if (this.resettingIsland.contains(playerUUID)) {
                                    this.resettingIsland.remove(playerUUID);
                                    this.resetPlayer(player, oldIsland2);
                                }
                                return true;
                            }
                            Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).errorNoPermission);
                            return true;
                        }
                        
                        
                        
                        else if (split[0].equalsIgnoreCase("lang")) {
                            if (!VaultHelper.checkPerm(player, "askyblock.island.lang")) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).errorNoPermission);
                                return true;
                            }
                            if (!NumberUtils.isDigits(split[1])) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + "/" + label + " lang <#>");
                                this.displayLocales(player);
                                return true;
                            }
                            try {
                                final int index = Integer.valueOf(split[1]);
                                if (index < 1 || index > this.plugin.getAvailableLocales().size()) {
                                    Util.sendMessage((CommandSender)player, ChatColor.RED + "/" + label + " lang <#>");
                                    this.displayLocales(player);
                                    return true;
                                }
                                for (final ASLocale locale : this.plugin.getAvailableLocales().values()) {
                                    if (locale.getIndex() == index) {
                                        this.plugin.getPlayers().setLocale(playerUUID, locale.getLocaleName());
                                        Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(playerUUID).generalSuccess);
                                        return true;
                                    }
                                }
                                Util.sendMessage((CommandSender)player, ChatColor.RED + "/" + label + " lang <#>");
                                this.displayLocales(player);
                            }
                            catch (Exception e) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + "/" + label + " lang <#>");
                                this.displayLocales(player);
                            }
                            return true;
                        }
                        
                        
                        
                        else if (split[0].equalsIgnoreCase("go")) {
                            if (!this.plugin.getPlayers().hasIsland(playerUUID) && !this.plugin.getPlayers().inTeam(playerUUID)) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoIsland);
                                return true;
                            }
                            if (VaultHelper.checkPerm(player, "askyblock.island.sethome")) {
                                int number = 1;
                                try {
                                    number = Integer.valueOf(split[1]);
                                    if (number < 1) {
                                        this.plugin.getGrid().homeTeleport(player, 1);
                                    }
                                    else {
                                        int maxHomes2 = Settings.maxHomes;
                                        for (final PermissionAttachmentInfo perms3 : player.getEffectivePermissions()) {
                                            if (perms3.getPermission().startsWith("askyblock.island.maxhomes.")) {
                                                if (perms3.getPermission().contains("askyblock.island.maxhomes.*")) {
                                                    maxHomes2 = Settings.maxHomes;
                                                    break;
                                                }
                                                final String[] spl3 = perms3.getPermission().split("askyblock.island.maxhomes.");
                                                if (spl3.length > 1) {
                                                    if (!NumberUtils.isDigits(spl3[1])) {
                                                        this.plugin.getLogger().severe("Player " + player.getName() + " has permission: " + perms3.getPermission() + " <-- the last part MUST be a number! Ignoring...");
                                                    }
                                                    else {
                                                        maxHomes2 = Math.max(maxHomes2, Integer.valueOf(spl3[1]));
                                                    }
                                                }
                                            }
                                            if (maxHomes2 < 1) {
                                                maxHomes2 = 1;
                                            }
                                        }
                                        if (number > maxHomes2) {
                                            if (maxHomes2 > 1) {
                                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).setHomeerrorNumHomes.replace("[max]", String.valueOf(maxHomes2)));
                                            }
                                            else {
                                                this.plugin.getGrid().homeTeleport(player, 1);
                                            }
                                        }
                                        else {
                                            this.plugin.getGrid().homeTeleport(player, number);
                                        }
                                    }
                                }
                                catch (Exception e3) {
                                    this.plugin.getGrid().homeTeleport(player, 1);
                                }
                                if (Settings.islandRemoveMobs) {
                                    this.plugin.getGrid().removeMobs(player.getLocation());
                                }
                            }
                            else {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoPermission);
                            }
                            return true;
                        }
                        
                        
                        
                        else if (split[0].equalsIgnoreCase("sethome")) {
                            if (!VaultHelper.checkPerm(player, "askyblock.island.sethome")) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoPermission);
                                return true;
                            }
                            final Island island = this.plugin.getGrid().getIsland(playerUUID);
                            if (island == null) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoIsland);
                                return true;
                            }
                            int maxHomes2 = Settings.maxHomes;
                            for (final PermissionAttachmentInfo perms3 : player.getEffectivePermissions()) {
                                if (perms3.getPermission().startsWith("askyblock.island.maxhomes.")) {
                                    if (perms3.getPermission().contains("askyblock.island.maxhomes.*")) {
                                        maxHomes2 = Settings.maxHomes;
                                        break;
                                    }
                                    final String[] spl3 = perms3.getPermission().split("askyblock.island.maxhomes.");
                                    if (spl3.length > 1) {
                                        if (!NumberUtils.isDigits(spl3[1])) {
                                            this.plugin.getLogger().severe("Player " + player.getName() + " has permission: " + perms3.getPermission() + " <-- the last part MUST be a number! Ignoring...");
                                        }
                                        else {
                                            maxHomes2 = Math.max(maxHomes2, Integer.valueOf(spl3[1]));
                                        }
                                    }
                                }
                                if (maxHomes2 < 1) {
                                    maxHomes2 = 1;
                                }
                            }
                            if (maxHomes2 > 1) {
                                int number2 = 0;
                                try {
                                    number2 = Integer.valueOf(split[1]);
                                    if (number2 < 1 || number2 > maxHomes2) {
                                        Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).setHomeerrorNumHomes.replace("[max]", String.valueOf(maxHomes2)));
                                    }
                                    else {
                                        this.plugin.getGrid().homeSet(player, number2);
                                    }
                                }
                                catch (Exception e4) {
                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).setHomeerrorNumHomes.replace("[max]", String.valueOf(maxHomes2)));
                                }
                            }
                            else {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoPermission);
                            }
                            return true;
                        }
                        
                        
                        
                        
                        else if (split[0].equalsIgnoreCase("warp")) {
                            if (!VaultHelper.checkPerm(player, "askyblock.island.warp")) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoPermission);
                                return true;
                            }
                            final Set<UUID> warpList2 = (Set<UUID>)this.plugin.getWarpSignsListener().listWarps();
                            if (warpList2.isEmpty()) {
                                Util.sendMessage((CommandSender)player, ChatColor.YELLOW + this.plugin.myLocale(player.getUniqueId()).warpserrorNoWarpsYet);
                                if (VaultHelper.checkPerm(player, "askyblock.island.addwarp")) {
                                    Util.sendMessage((CommandSender)player, ChatColor.YELLOW + this.plugin.myLocale().warpswarpTip);
                                }
                                else {
                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoPermission);
                                }
                                return true;
                            }
                            UUID foundWarp = null;
                            for (final UUID warp : warpList2) {
                                if (warp == null) {
                                    continue;
                                }
                                if (this.plugin.getPlayers().getName(warp).toLowerCase().equals(split[1].toLowerCase())) {
                                    foundWarp = warp;
                                    break;
                                }
                                if (!this.plugin.getPlayers().getName(warp).toLowerCase().startsWith(split[1].toLowerCase())) {
                                    continue;
                                }
                                foundWarp = warp;
                            }
                            if (foundWarp == null) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).warpserrorDoesNotExist);
                                return true;
                            }
                            final Location warpSpot = this.plugin.getWarpSignsListener().getWarp(foundWarp);
                            if (warpSpot == null) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).warpserrorNotReadyYet);
                                this.plugin.getLogger().warning("Null warp found, owned by " + this.plugin.getPlayers().getName(foundWarp));
                                return true;
                            }
                            final Island island2 = this.plugin.getGrid().getIslandAt(warpSpot);
                            if (island2 != null && this.plugin.getPlayers().isBanned(island2.getOwner(), playerUUID)) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).banBanned.replace("[name]", this.plugin.getPlayers().getName(island2.getOwner())));
                                if (!VaultHelper.checkPerm(player, "askyblock.mod.bypassprotect") && !VaultHelper.checkPerm(player, "askyblock.mod.bypasslock")) {
                                    return true;
                                }
                            }
                            if (island2 != null && island2.isLocked() && !player.isOp() && !VaultHelper.checkPerm(player, "askyblock.mod.bypasslock") && !VaultHelper.checkPerm(player, "askyblock.mod.bypassprotect")) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).lockIslandLocked);
                                if (!this.plugin.getGrid().locationIsAtHome(player, true, warpSpot)) {
                                    return true;
                                }
                            }
                            boolean pvp = false;
                            if ((warpSpot.getWorld().equals(ASkyBlock.getIslandWorld()) && island2.getIgsFlag(Island.SettingsFlag.PVP)) || (warpSpot.getWorld().equals(ASkyBlock.getNetherWorld()) && island2.getIgsFlag(Island.SettingsFlag.NETHER_PVP))) {
                                pvp = true;
                            }
                            final Block b = warpSpot.getBlock();
                            if (!b.getType().equals((Object)Material.SIGN_POST) && !b.getType().equals((Object)Material.WALL_SIGN)) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).warpserrorDoesNotExist);
                                this.plugin.getWarpSignsListener().removeWarp(warpSpot);
                                return true;
                            }
                            final Sign sign = (Sign)b.getState();
                            final org.bukkit.material.Sign s = (org.bukkit.material.Sign)((Block) sign).getState();
                            //                            final org.bukkit.material.Sign s = (org.bukkit.material.Sign)sign.getData();
                            //FIXME
                            final BlockFace directionFacing = s.getFacing();
                            final Location inFront = b.getRelative(directionFacing).getLocation();
                            final Location oneDown = b.getRelative(directionFacing).getRelative(BlockFace.DOWN).getLocation();
                            if (GridManager.isSafeLocation(inFront)) {
                                this.warpPlayer(player, inFront, foundWarp, directionFacing, pvp);
                                return true;
                            }
                            if (b.getType().equals((Object)Material.WALL_SIGN) && GridManager.isSafeLocation(oneDown)) {
                                this.warpPlayer(player, oneDown, foundWarp, directionFacing, pvp);
                                return true;
                            }
                            if (!GridManager.isSafeLocation(warpSpot)) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).warpserrorNotSafe);
                                if (b.getType().equals((Object)Material.SIGN_POST)) {
                                    this.plugin.getLogger().warning("Unsafe warp found at " + warpSpot.toString() + " owned by " + this.plugin.getPlayers().getName(foundWarp));
                                }
                                return true;
                            }
                            final Location actualWarp = new Location(warpSpot.getWorld(), warpSpot.getBlockX() + 0.5, (double)warpSpot.getBlockY(), warpSpot.getBlockZ() + 0.5);
                            player.teleport(actualWarp);
                            if (pvp) {
                                Util.sendMessage((CommandSender)player, ChatColor.BOLD + "" + ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).igs.get(Island.SettingsFlag.PVP) + " " + this.plugin.myLocale(player.getUniqueId()).igsAllowed);
                                if (this.plugin.getServer().getVersion().contains("(MC: 1.8") || this.plugin.getServer().getVersion().contains("(MC: 1.7")) {
                                    player.getWorld().playSound(player.getLocation(), Sound.valueOf("ARROW_HIT"), 1.0f, 1.0f);
                                }
                                
                            }
                            else if (this.plugin.getServer().getVersion().contains("(MC: 1.8") || this.plugin.getServer().getVersion().contains("(MC: 1.7")) {
                                player.getWorld().playSound(player.getLocation(), Sound.valueOf("BAT_TAKEOFF"), 1.0f, 1.0f);
                            }
                           
                            return true;
                        }
                        
                        
                        
                        else if (split[0].equalsIgnoreCase("level")) {
                            if (!VaultHelper.checkPerm(player, "askyblock.island.info")) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoPermission);
                                return true;
                            }
                            final UUID targetPlayerUUID = this.plugin.getPlayers().getUUID(split[1]);
                            if (targetPlayerUUID == null) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorUnknownPlayer);
                                return true;
                            }
                            if (this.plugin.getPlayers().hasIsland(targetPlayerUUID) || this.plugin.getPlayers().inTeam(targetPlayerUUID)) {
                                this.calculateIslandLevel((CommandSender)player, targetPlayerUUID);
                            }
                            else {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoIslandOther);
                            }
                            return true;
                        }
                        
 //LABEL                       
                        
                        else if (split[0].equalsIgnoreCase("invite")) {
                            if (!VaultHelper.checkPerm(player, "askyblock.team.create")) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoPermission);
                                return true;
                            }
                            final Player invitedPlayer = this.plugin.getServer().getPlayer(split[1]);
                            if (invitedPlayer == null) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorOfflinePlayer);
                                return true;
                            }
                            final UUID invitedPlayerUUID = invitedPlayer.getUniqueId();
                            if (!this.plugin.getPlayers().hasIsland(player.getUniqueId())) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).inviteerrorYouMustHaveIslandToInvite);
                                return true;
                            }
                            if (player.getName().equalsIgnoreCase(split[1])) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).inviteerrorYouCannotInviteYourself);
                                return true;
                            }
                            final long time = this.plugin.getPlayers().getInviteCoolDownTime(invitedPlayerUUID, this.plugin.getPlayers().getIslandLocation(playerUUID));
                            if (time > 0L && !player.isOp()) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).inviteerrorCoolDown.replace("[time]", String.valueOf(time)));
                                return true;
                            }
                            if (this.plugin.getPlayers().inTeam(player.getUniqueId())) {
                                if (teamLeader.equals(player.getUniqueId())) {
                                    if (!this.plugin.getPlayers().inTeam(invitedPlayerUUID)) {
                                        int maxSize2 = Settings.maxTeamSize;
                                        for (final PermissionAttachmentInfo perms4 : player.getEffectivePermissions()) {
                                            if (perms4.getPermission().startsWith("askyblock.team.maxsize.")) {
                                                if (perms4.getPermission().contains("askyblock.team.maxsize.*")) {
                                                    maxSize2 = Settings.maxTeamSize;
                                                    break;
                                                }
                                                final String[] spl4 = perms4.getPermission().split("askyblock.team.maxsize.");
                                                if (spl4.length > 1) {
                                                    if (!NumberUtils.isDigits(spl4[1])) {
                                                        this.plugin.getLogger().severe("Player " + player.getName() + " has permission: " + perms4.getPermission() + " <-- the last part MUST be a number! Ignoring...");
                                                    }
                                                    else {
                                                        maxSize2 = Math.max(maxSize2, Integer.valueOf(spl4[1]));
                                                    }
                                                }
                                            }
                                            if (maxSize2 < 1) {
                                                maxSize2 = 1;
                                            }
                                        }
                                        if (teamMembers.size() < maxSize2) {
                                            if (this.inviteList.containsValue(playerUUID)) {
                                                this.inviteList.remove(getKeyByValue(this.inviteList, player.getUniqueId()));
                                                Util.sendMessage((CommandSender)player, ChatColor.YELLOW + this.plugin.myLocale(player.getUniqueId()).inviteremovingInvite);
                                            }
                                            this.inviteList.put(invitedPlayerUUID, player.getUniqueId());
                                            Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).inviteinviteSentTo.replace("[name]", split[1]));
                                            Util.sendMessage((CommandSender)Bukkit.getPlayer(invitedPlayerUUID), this.plugin.myLocale(invitedPlayerUUID).invitenameHasInvitedYou.replace("[name]", player.getName()));
                                            Util.sendMessage((CommandSender)Bukkit.getPlayer(invitedPlayerUUID), ChatColor.WHITE + "/" + label + " [accept/reject]" + ChatColor.YELLOW + " " + this.plugin.myLocale(invitedPlayerUUID).invitetoAcceptOrReject);
                                            if (this.plugin.getPlayers().hasIsland(invitedPlayerUUID)) {
                                                Util.sendMessage((CommandSender)Bukkit.getPlayer(invitedPlayerUUID), ChatColor.RED + this.plugin.myLocale(invitedPlayerUUID).invitewarningYouWillLoseIsland);
                                            }
                                        }
                                        else {
                                            Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).inviteerrorYourIslandIsFull);
                                        }
                                    }
                                    else {
                                        Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).inviteerrorThatPlayerIsAlreadyInATeam);
                                    }
                                }
                                else {
                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).inviteerrorYouMustHaveIslandToInvite);
                                }
                            }
                            else if (!this.plugin.getPlayers().inTeam(invitedPlayerUUID)) {
                                if (this.inviteList.containsValue(playerUUID)) {
                                    this.inviteList.remove(getKeyByValue(this.inviteList, player.getUniqueId()));
                                    Util.sendMessage((CommandSender)player, ChatColor.YELLOW + this.plugin.myLocale(player.getUniqueId()).inviteremovingInvite);
                                }
                                this.inviteList.put(invitedPlayerUUID, player.getUniqueId());
                                Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).inviteinviteSentTo.replace("[name]", split[1]));
                                Util.sendMessage((CommandSender)Bukkit.getPlayer(invitedPlayerUUID), this.plugin.myLocale(invitedPlayerUUID).invitenameHasInvitedYou.replace("[name]", player.getName()));
                                Util.sendMessage((CommandSender)Bukkit.getPlayer(invitedPlayerUUID), ChatColor.WHITE + "/" + label + " [accept/reject]" + ChatColor.YELLOW + " " + this.plugin.myLocale(invitedPlayerUUID).invitetoAcceptOrReject);
                                if (this.plugin.getPlayers().hasIsland(invitedPlayerUUID)) {
                                    Util.sendMessage((CommandSender)Bukkit.getPlayer(invitedPlayerUUID), ChatColor.RED + this.plugin.myLocale(invitedPlayerUUID).invitewarningYouWillLoseIsland);
                                }
                            }
                            else {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).inviteerrorThatPlayerIsAlreadyInATeam);
                            }
                            return true;
                        }
                        
                        
                        
                        
                        else if (split[0].equalsIgnoreCase("coop")) {
                            if (!VaultHelper.checkPerm(player, "askyblock.coop")) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoPermission);
                                return true;
                            }
                            final Player target2 = this.plugin.getServer().getPlayer(split[1]);
                            if (target2 == null) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorOfflinePlayer);
                                return true;
                            }
                            final UUID targetPlayerUUID2 = target2.getUniqueId();
                            if (!this.plugin.getPlayers().hasIsland(playerUUID) && !this.plugin.getPlayers().inTeam(playerUUID)) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).inviteerrorYouMustHaveIslandToInvite);
                                return true;
                            }
                            if (playerUUID.equals(targetPlayerUUID2)) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).inviteerrorYouCannotInviteYourself);
                                return true;
                            }
                            if (this.plugin.getPlayers().getMembers(playerUUID).contains(targetPlayerUUID2)) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).coopOnYourTeam);
                                return true;
                            }
                            if (!this.plugin.getPlayers().inTeam(targetPlayerUUID2) && !this.plugin.getPlayers().hasIsland(targetPlayerUUID2)) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoIslandOther);
                                return true;
                            }
                            if (CoopPlay.getInstance().addCoopPlayer(player, target2)) {
                                Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).coopSuccess.replace("[name]", target2.getName()));
                                Util.sendMessage((CommandSender)target2, ChatColor.GREEN + this.plugin.myLocale(targetPlayerUUID2).coopMadeYouCoop.replace("[name]", player.getName()));
                            }
                            return true;
                        }
                        
                        
                        
                        
                        
                        else if (split[0].equalsIgnoreCase("expel")) {
                            if (!VaultHelper.checkPerm(player, "askyblock.island.expel")) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoPermission);
                                return true;
                            }
                            final UUID targetPlayerUUID = this.plugin.getPlayers().getUUID(split[1]);
                            if (targetPlayerUUID == null) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorUnknownPlayer);
                                return true;
                            }
                            if (targetPlayerUUID.equals(playerUUID)) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).expelNotYourself);
                                return true;
                            }
                            final Player target3 = this.plugin.getServer().getPlayer(targetPlayerUUID);
                            if (target3 != null && (target3.isOp() || VaultHelper.checkPerm(target3, "askyblock.mod.bypassprotect") || VaultHelper.checkPerm(target3, "askyblock.mod.bypassexpel"))) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).expelFail.replace("[name]", target3.getName()));
                                return true;
                            }
                            final boolean coop = CoopPlay.getInstance().removeCoopPlayer(player, targetPlayerUUID);
                            if (coop) {
                                if (target3 != null) {
                                    Util.sendMessage((CommandSender)target3, ChatColor.RED + this.plugin.myLocale(target3.getUniqueId()).coopRemoved.replace("[name]", player.getName()));
                                }
                                else {
                                    this.plugin.getMessages().setMessage(targetPlayerUUID, ChatColor.RED + this.plugin.myLocale(targetPlayerUUID).coopRemoved.replace("[name]", player.getName()));
                                }
                                Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).coopRemoveSuccess.replace("[name]", this.plugin.getPlayers().getName(targetPlayerUUID)));
                            }
                            if (target3 != null && this.plugin.getGrid().isOnIsland(player, target3)) {
                                if (this.plugin.getPlayers().inTeam(targetPlayerUUID) || this.plugin.getPlayers().hasIsland(targetPlayerUUID)) {
                                    this.plugin.getGrid().homeTeleport(target3);
                                }
                                else if (!target3.performCommand("spawn")) {
                                    target3.teleport(player.getWorld().getSpawnLocation());
                                }
                                Util.sendMessage((CommandSender)target3, ChatColor.RED + this.plugin.myLocale(target3.getUniqueId()).expelExpelled);
                                this.plugin.getLogger().info(player.getName() + " expelled " + target3.getName() + " from their island.");
                                Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).expelSuccess.replace("[name]", target3.getName()));
                            }
                            else if (!coop) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).expelNotOnIsland);
                            }
                            return true;
                        }
                        
                        
                        
                        
                        else if (split[0].equalsIgnoreCase("uncoop")) {
                            if (!VaultHelper.checkPerm(player, "askyblock.coop")) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoPermission);
                                return true;
                            }
                            final UUID targetPlayerUUID = this.plugin.getPlayers().getUUID(split[1]);
                            if (targetPlayerUUID == null) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorUnknownPlayer);
                                return true;
                            }
                            if (targetPlayerUUID.equals(playerUUID)) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).expelNotYourself);
                                return true;
                            }
                            final OfflinePlayer target4 = this.plugin.getServer().getOfflinePlayer(targetPlayerUUID);
                            final boolean coop = CoopPlay.getInstance().removeCoopPlayer(player, targetPlayerUUID);
                            if (coop) {
                                if (target4 != null && target4.isOnline()) {
                                    Util.sendMessage((CommandSender)target4.getPlayer(), ChatColor.RED + this.plugin.myLocale(target4.getUniqueId()).coopRemoved.replace("[name]", player.getName()));
                                }
                                else {
                                    this.plugin.getMessages().setMessage(targetPlayerUUID, ChatColor.RED + this.plugin.myLocale(targetPlayerUUID).coopRemoved.replace("[name]", player.getName()));
                                }
                                Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).coopRemoveSuccess.replace("[name]", this.plugin.getPlayers().getName(targetPlayerUUID)));
                            }
                            else {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).coopNotInCoop.replace("[name]", this.plugin.getPlayers().getName(targetPlayerUUID)));
                            }
                            return true;
                        }
                        
                        
                        
                        
                        else if (split[0].equalsIgnoreCase("ban")) {
                            if (!VaultHelper.checkPerm(player, "askyblock.island.ban")) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoPermission);
                                return true;
                            }
                            final UUID targetPlayerUUID = this.plugin.getPlayers().getUUID(split[1]);
                            if (targetPlayerUUID == null) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorUnknownPlayer);
                                return true;
                            }
                            if (targetPlayerUUID.equals(playerUUID)) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).banNotYourself);
                                return true;
                            }
                            if (this.plugin.getPlayers().inTeam(playerUUID) && this.plugin.getPlayers().inTeam(targetPlayerUUID) && this.plugin.getPlayers().getTeamLeader(playerUUID).equals(this.plugin.getPlayers().getTeamLeader(targetPlayerUUID))) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).banNotTeamMember);
                                return true;
                            }
                            if (this.plugin.getPlayers().isBanned(playerUUID, targetPlayerUUID)) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).banAlreadyBanned.replace("[name]", split[1]));
                                return true;
                            }
                            final Player target3 = this.plugin.getServer().getPlayer(targetPlayerUUID);
                            final OfflinePlayer offlineTarget = this.plugin.getServer().getOfflinePlayer(targetPlayerUUID);
                            if (offlineTarget.isOp()) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).banFail.replace("[name]", split[1]));
                                return true;
                            }
                            if (target3 != null) {
                                if (VaultHelper.checkPerm(target3, "askyblock.admin.noban")) {
                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).banFail.replace("[name]", split[1]));
                                    return true;
                                }
                                final boolean coop2 = CoopPlay.getInstance().removeCoopPlayer(player, target3);
                                if (coop2) {
                                    Util.sendMessage((CommandSender)target3, ChatColor.RED + this.plugin.myLocale(target3.getUniqueId()).coopRemoved.replace("[name]", player.getName()));
                                    Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).coopRemoveSuccess.replace("[name]", target3.getName()));
                                }
                                if (this.plugin.getGrid().isOnIsland(player, target3)) {
                                    if (this.plugin.getPlayers().inTeam(targetPlayerUUID) || this.plugin.getPlayers().hasIsland(targetPlayerUUID)) {
                                        this.plugin.getGrid().homeTeleport(target3);
                                    }
                                    else if (!target3.performCommand("spawn")) {
                                        target3.teleport(player.getWorld().getSpawnLocation());
                                    }
                                }
                                Util.sendMessage((CommandSender)target3, ChatColor.RED + this.plugin.myLocale(targetPlayerUUID).banBanned.replace("[name]", player.getName()));
                            }
                            else {
                                this.plugin.getMessages().setMessage(targetPlayerUUID, ChatColor.RED + this.plugin.myLocale(targetPlayerUUID).banBanned.replace("[name]", player.getName()));
                            }
                            this.plugin.getLogger().info(player.getName() + " banned " + split[1] + " from their island.");
                            Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).banSuccess.replace("[name]", split[1]));
                            this.plugin.getMessages().tellTeam(playerUUID, ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).banSuccess.replace("[name]", split[1]));
                            this.plugin.getMessages().tellOfflineTeam(playerUUID, ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).banSuccess.replace("[name]", split[1]));
                            this.plugin.getPlayers().ban(playerUUID, targetPlayerUUID);
                            this.plugin.getGrid().saveGrid();
                            return true;
                        }
                        
                        
                        
                        
                        else if (split[0].equalsIgnoreCase("unban")) {
                            if (!VaultHelper.checkPerm(player, "askyblock.island.ban")) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoPermission);
                                return true;
                            }
                            final UUID targetPlayerUUID = this.plugin.getPlayers().getUUID(split[1]);
                            if (targetPlayerUUID == null) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorUnknownPlayer);
                                return true;
                            }
                            if (targetPlayerUUID.equals(playerUUID)) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).banNotYourself);
                                return true;
                            }
                            if (!this.plugin.getPlayers().isBanned(playerUUID, targetPlayerUUID)) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).banNotBanned.replace("[name]", split[1]));
                                return true;
                            }
                            final Player target3 = this.plugin.getServer().getPlayer(targetPlayerUUID);
                            if (target3 != null) {
                                Util.sendMessage((CommandSender)target3, ChatColor.RED + this.plugin.myLocale(target3.getUniqueId()).banLifted.replace("[name]", player.getName()));
                            }
                            else {
                                this.plugin.getMessages().setMessage(targetPlayerUUID, ChatColor.GREEN + this.plugin.myLocale(targetPlayerUUID).banLifted.replace("[name]", player.getName()));
                            }
                            Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).banLiftedSuccess.replace("[name]", split[1]));
                            this.plugin.getLogger().info(player.getName() + " unbanned " + split[1] + " from their island.");
                            this.plugin.getMessages().tellTeam(playerUUID, ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).banLiftedSuccess.replace("[name]", split[1]));
                            this.plugin.getMessages().tellOfflineTeam(playerUUID, ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).banLiftedSuccess.replace("[name]", split[1]));
                            this.plugin.getPlayers().unBan(playerUUID, targetPlayerUUID);
                            this.plugin.getGrid().saveGrid();
                            return true;
                        }
                        
                        
                        
                        
                        else if (split[0].equalsIgnoreCase("kick") || split[0].equalsIgnoreCase("remove")) {
                            if (!VaultHelper.checkPerm(player, "askyblock.team.kick")) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoPermission);
                                return true;
                            }
                            if (!this.plugin.getPlayers().inTeam(playerUUID)) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).kickerrorNoTeam);
                                return true;
                            }
                            if (teamLeader != null && !teamLeader.equals(playerUUID)) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).kickerrorOnlyLeaderCan);
                                return true;
                            }
                            targetPlayer = null;
                            for (final UUID member : teamMembers) {
                                if (this.plugin.getPlayers().getName(member).equalsIgnoreCase(split[1])) {
                                    targetPlayer = member;
                                }
                            }
                            if (targetPlayer == null) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).kickerrorNotPartOfTeam);
                                return true;
                            }
                            if (teamMembers.contains(targetPlayer)) {
                                if (player.getUniqueId().equals(targetPlayer)) {
                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).leaveerrorLeadersCannotLeave);
                                    return true;
                                }
                                if (!this.removePlayerFromTeam(targetPlayer, teamLeader)) {
                                    return true;
                                }
                                this.plugin.getPlayers().startInviteCoolDownTimer(targetPlayer, this.plugin.getPlayers().getIslandLocation(playerUUID));
                                if (Settings.resetChallenges) {
                                    this.plugin.getPlayers().resetAllChallenges(targetPlayer, false);
                                }
                                this.plugin.getPlayers().setIslandLevel(targetPlayer, 0L);
                                TopTen.topTenAddEntry(playerUUID, 0L);
                                final Player target2 = this.plugin.getServer().getPlayer(targetPlayer);
                                if (target2 != null) {
                                    Util.sendMessage((CommandSender)target2, ChatColor.RED + this.plugin.myLocale(targetPlayer).kicknameRemovedYou.replace("[name]", player.getName()));
                                    CoopPlay.getInstance().clearMyInvitedCoops(target2);
                                    CoopPlay.getInstance().clearMyCoops(target2);
                                    if (target2.getWorld().equals(ASkyBlock.getIslandWorld()) && !Settings.kickedKeepInv) {
                                        for (final ItemStack i : target2.getInventory().getContents()) {
                                            if (i != null) {
                                                try {
                                                    final Item drop = player.getWorld().dropItemNaturally(player.getLocation(), i);
                                                    final PlayerDropItemEvent event = new PlayerDropItemEvent(target2, drop);
                                                    this.plugin.getServer().getPluginManager().callEvent((Event)event);
                                                }
                                                catch (Exception ex) {}
                                            }
                                        }
                                        target2.getInventory().clear();
                                        target2.getInventory().setArmorContents((ItemStack[])null);
                                        target2.getInventory().setHelmet((ItemStack)null);
                                        target2.getInventory().setChestplate((ItemStack)null);
                                        target2.getInventory().setLeggings((ItemStack)null);
                                        target2.getInventory().setBoots((ItemStack)null);
                                        target2.getEquipment().clear();
                                        target2.updateInventory();
                                    }
                                    if (!target2.performCommand("spawn")) {
                                        target2.teleport(ASkyBlock.getIslandWorld().getSpawnLocation());
                                    }
                                }
                                else {
                                    this.plugin.getMessages().setMessage(targetPlayer, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).kicknameRemovedYou.replace("[name]", player.getName()));
                                }
                                this.plugin.getWarpSignsListener().removeWarp(targetPlayer);
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).kicknameRemoved.replace("[name]", split[1]));
                                teamMembers.remove(targetPlayer);
                                if (teamMembers.size() < 2 && !this.removePlayerFromTeam(player.getUniqueId(), teamLeader)) {
                                    return true;
                                }
                                this.plugin.getPlayers().save(targetPlayer);
                            }
                            else {
                                this.plugin.getLogger().warning("Player " + player.getName() + " failed to remove " + this.plugin.getPlayers().getName(targetPlayer));
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).kickerrorNotPartOfTeam);
                            }
                            return true;
                        }
                        
                        
                        
                        
                        
                        else {
                            if (!split[0].equalsIgnoreCase("makeleader")) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).errorUnknownCommand);
                                return true;
                            }
                            if (!VaultHelper.checkPerm(player, "askyblock.team.makeleader")) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoPermission);
                                return true;
                            }
                            targetPlayer = this.plugin.getPlayers().getUUID(split[1]);
                            if (targetPlayer == null) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorUnknownPlayer);
                                return true;
                            }
                            if (targetPlayer.equals(playerUUID)) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).makeLeadererrorGeneralError);
                                return true;
                            }
                            if (!this.plugin.getPlayers().inTeam(player.getUniqueId())) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).makeLeadererrorYouMustBeInTeam);
                                return true;
                            }
                            if (this.plugin.getPlayers().getMembers(player.getUniqueId()).size() > 2) {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).makeLeadererrorRemoveAllPlayersFirst);
                                this.plugin.getLogger().info(player.getName() + " tried to transfer his island, but failed because >2 people in a team");
                                return true;
                            }
                            if (this.plugin.getPlayers().inTeam(player.getUniqueId())) {
                                if (teamLeader.equals(player.getUniqueId())) {
                                    if (teamMembers.contains(targetPlayer)) {
                                        if (!this.removePlayerFromTeam(targetPlayer, teamLeader, true)) {
                                            return true;
                                        }
                                        if (!this.removePlayerFromTeam(teamLeader, teamLeader, true)) {
                                            return true;
                                        }
                                        Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).makeLeadernameIsNowTheOwner.replace("[name]", this.plugin.getPlayers().getName(targetPlayer)));
                                        this.plugin.getGrid().transferIsland(player.getUniqueId(), targetPlayer);
                                        this.addPlayertoTeam(player.getUniqueId(), targetPlayer);
                                        this.addPlayertoTeam(targetPlayer, targetPlayer);
                                        final Player target2 = this.plugin.getServer().getPlayer(targetPlayer);
                                        if (target2 == null) {
                                            this.plugin.getMessages().setMessage(targetPlayer, this.plugin.myLocale(player.getUniqueId()).makeLeaderyouAreNowTheOwner);
                                        }
                                        else {
                                            Util.sendMessage((CommandSender)this.plugin.getServer().getPlayer(targetPlayer), ChatColor.GREEN + this.plugin.myLocale(targetPlayer).makeLeaderyouAreNowTheOwner);
                                            boolean hasARangePerm = false;
                                            int range = Settings.islandProtectionRange;
                                            final Island islandByOwner = this.plugin.getGrid().getIsland(targetPlayer);
                                            if (islandByOwner.getProtectionSize() == 0) {
                                                this.plugin.getLogger().warning("Player " + player.getName() + "'s island had a protection range of 0. Setting to default " + range);
                                                islandByOwner.setProtectionSize(range);
                                            }
                                            for (final PermissionAttachmentInfo perms5 : target2.getEffectivePermissions()) {
                                                if (perms5.getPermission().startsWith("askyblock.island.range.")) {
                                                    if (perms5.getPermission().contains("askyblock.island.range.*")) {
                                                        break;
                                                    }
                                                    final String[] spl5 = perms5.getPermission().split("askyblock.island.range.");
                                                    if (spl5.length <= 1) {
                                                        continue;
                                                    }
                                                    if (!NumberUtils.isDigits(spl5[1])) {
                                                        this.plugin.getLogger().severe("Player " + player.getName() + " has permission: " + perms5.getPermission() + " <-- the last part MUST be a number! Ignoring...");
                                                    }
                                                    else {
                                                        hasARangePerm = true;
                                                        range = Math.max(range, Integer.valueOf(spl5[1]));
                                                    }
                                                }
                                            }
                                            if (hasARangePerm) {
                                                if (range % 2 != 0) {
                                                    --range;
                                                }
                                                if (range != islandByOwner.getProtectionSize()) {
                                                    Util.sendMessage((CommandSender)player, ChatColor.GOLD + this.plugin.myLocale(targetPlayer).adminSetRangeUpdated.replace("[number]", String.valueOf(range)));
                                                    Util.sendMessage((CommandSender)target2, ChatColor.GOLD + this.plugin.myLocale(targetPlayer).adminSetRangeUpdated.replace("[number]", String.valueOf(range)));
                                                    this.plugin.getLogger().info("Makeleader: Island protection range changed from " + islandByOwner.getProtectionSize() + " to " + range + " for " + player.getName() + " due to permission.");
                                                }
                                                islandByOwner.setProtectionSize(range);
                                            }
                                        }
                                        this.plugin.getGrid().saveGrid();
                                        return true;
                                    }
                                    else {
                                        Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).makeLeadererrorThatPlayerIsNotInTeam);
                                    }
                                }
                                else {
                                    Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).makeLeadererrorNotYourIsland);
                                }
                            }
                            else {
                                Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).makeLeadererrorGeneralError);
                            }
                            return true;
                        }
                        //FIXME
                       // break;  
                    }
                }
            }
            Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(playerUUID).errorUnknownCommand);
            return true;
        }
        if (!VaultHelper.checkPerm(player, "askyblock.island.name") || !this.plugin.getPlayers().hasIsland(playerUUID)) {
            Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorNoPermission);
            return true;
        }
        String name = split[1];
        for (int j = 2; j < split.length; ++j) {
            name = name + " " + split[j];
        }
        if (name.length() < Settings.minNameLength) {
            Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorTooShort.replace("[length]", String.valueOf(Settings.minNameLength)));
            return true;
        }
        if (name.length() > Settings.maxNameLength) {
            Util.sendMessage((CommandSender)player, ChatColor.RED + this.plugin.myLocale(player.getUniqueId()).errorTooLong.replace("[length]", String.valueOf(Settings.maxNameLength)));
            return true;
        }
        this.plugin.getGrid().setIslandName(playerUUID, ChatColor.translateAlternateColorCodes('&', name));
        Util.sendMessage((CommandSender)player, ChatColor.GREEN + this.plugin.myLocale(player.getUniqueId()).generalSuccess);
        return true;
    }
   

    private void displayLocales(Player player) {
        TreeMap<Integer, String> langs = new TreeMap<Integer, String>();
        for (ASLocale locale : this.plugin.getAvailableLocales().values()) {
            if (locale.getLocaleName().equalsIgnoreCase("locale")) continue;
            langs.put(locale.getIndex(), locale.getLanguageName() + " (" + locale.getCountryName() + ")");
        }
        for (Map.Entry entry : langs.entrySet()) {
            Util.sendMessage((CommandSender)player, entry.getKey() + ": " + (String)entry.getValue());
        }
    }

    private void warpPlayer(Player player, Location inFront, UUID foundWarp, BlockFace directionFacing, boolean pvp) {
        float yaw = Util.blockFaceToFloat(directionFacing);
        Location actualWarp = new Location(inFront.getWorld(), (double)inFront.getBlockX() + 0.5, (double)inFront.getBlockY(), (double)inFront.getBlockZ() + 0.5, yaw, 30.0f);
        player.teleport(actualWarp);
        if (pvp) {
            Util.sendMessage((CommandSender)player, (Object)ChatColor.BOLD + "" + (Object)ChatColor.RED + this.plugin.myLocale((UUID)player.getUniqueId()).igs.get((Object)Island.SettingsFlag.PVP) + " " + this.plugin.myLocale((UUID)player.getUniqueId()).igsAllowed);
            if (this.plugin.getServer().getVersion().contains("(MC: 1.8") || this.plugin.getServer().getVersion().contains("(MC: 1.7")) {
                player.getWorld().playSound(player.getLocation(), Sound.valueOf("ARROW_HIT"), 1.0f, 1.0f);
            }
            else {//Подсосите новую версию bukkit, чтобы это пофиксить
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT, 1.0f, 1.0f);
            }
        } else if (this.plugin.getServer().getVersion().contains("(MC: 1.8") || this.plugin.getServer().getVersion().contains("(MC: 1.7")) {
            	player.getWorld().playSound(player.getLocation(), Sound.valueOf("BAT_TAKEOFF"), 1.0f, 1.0f);
        	  }
        		else {//Подсосите новую версию bukkit, чтобы это пофиксить
        			player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.0f);
        	}
        Player warpOwner = this.plugin.getServer().getPlayer(foundWarp);
        if (warpOwner != null && !warpOwner.equals((Object)player)) {
            Util.sendMessage((CommandSender)warpOwner, this.plugin.myLocale((UUID)foundWarp).warpsPlayerWarped.replace("[name]", player.getName()));
        }
    }

    private void chooseIsland(Player player) {
        List<Schematic> schems = this.getSchematics(player, false);
        if (schems.isEmpty()) {
            this.newIsland(player);
        } else if (schems.size() == 1) {
            this.newIsland(player, schems.get(0));
        } else if (Settings.useSchematicPanel) {
            this.pendingNewIslandSelection.add(player.getUniqueId());
            Inventory inv = this.plugin.getSchematicsPanel().getPanel(player);
            if (inv != null) {
                player.openInventory(inv);
            } else {
                this.plugin.getLogger().severe("There are no valid schematics available for " + player.getName() + "! Check config.yml schematicsection.");
            }
        } else {
            schems = this.getSchematics(player, true);
            if (schems.isEmpty()) {
                this.newIsland(player);
            } else if (Settings.chooseIslandRandomly) {
                this.newIsland(player, schems.get(this.random.nextInt(schems.size())));
            } else {
                this.newIsland(player, schems.get(0));
            }
        }
    }

    private void resetPlayer(Player player, Island oldIsland) {
        this.plugin.getPlayers().setResetsLeft(player.getUniqueId(), this.plugin.getPlayers().getResetsLeft(player.getUniqueId()) - 1);
        if (Settings.islandResetDeathReset) {
            this.plugin.getPlayers().setDeaths(player.getUniqueId(), 0);
        }
        CoopPlay.getInstance().clearMyInvitedCoops(player);
        CoopPlay.getInstance().clearMyCoops(player);
        this.plugin.getWarpSignsListener().removeWarp(player.getUniqueId());
        if (oldIsland != null) {
            CoopPlay.getInstance().clearAllIslandCoops(oldIsland.getCenter());
            this.plugin.getGrid().removePlayersFromIsland(oldIsland, player.getUniqueId());
            new DeleteIslandChunk(this.plugin, oldIsland);
            IslandResetEvent event = new IslandResetEvent(player, oldIsland.getCenter());
            this.plugin.getServer().getPluginManager().callEvent((Event)event);
        }
        this.plugin.getGrid().saveGrid();
    }

    public static void runCommands(List<String> commands, OfflinePlayer offlinePlayer) {
        Iterator<String> i = commands.iterator();
        while (i.hasNext()) {
            String cmd = i.next();
            if (cmd.startsWith("[SELF]")) {
                cmd = cmd.substring(6, cmd.length()).replace("[player]", offlinePlayer.getName()).trim();
                if (!offlinePlayer.isOnline()) continue;
                try {
                    Bukkit.getLogger().info("Running command '" + cmd + "' as " + offlinePlayer.getName());
                    ((Player)offlinePlayer).performCommand(cmd);
                }
                catch (Exception e) {
                    Bukkit.getLogger().severe("Problem executing island command executed by player - skipping!");
                    Bukkit.getLogger().severe("Command was : " + cmd);
                    Bukkit.getLogger().severe("Error was: " + e.getMessage());
                    e.printStackTrace();
                }
                continue;
            }
            try {
                if (Bukkit.getServer().dispatchCommand((CommandSender)Bukkit.getServer().getConsoleSender(), cmd.replace("[player]", offlinePlayer.getName()))) continue;
                Bukkit.getLogger().severe("Problem executing island command - skipping!");
                Bukkit.getLogger().severe("Command was : " + cmd);
            }
            catch (Exception e) {
                Bukkit.getLogger().severe("Problem executing island command - skipping!");
                Bukkit.getLogger().severe("Command was : " + cmd);
                Bukkit.getLogger().severe("Error was: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public boolean onRestartWaitTime(Player player) {
        if (this.resetWaitTime.containsKey(player.getUniqueId())) {
            if (this.resetWaitTime.get(player.getUniqueId()) > Calendar.getInstance().getTimeInMillis()) {
                return true;
            }
            return false;
        }
        return false;
    }

    public boolean onLevelWaitTime(Player player) {
        if (this.levelWaitTime.containsKey(player.getUniqueId())) {
            if (this.levelWaitTime.get(player.getUniqueId()) > Calendar.getInstance().getTimeInMillis()) {
                return true;
            }
            return false;
        }
        return false;
    }

    private void setResetWaitTime(Player player) {
        this.resetWaitTime.put(player.getUniqueId(), Calendar.getInstance().getTimeInMillis() + (long)(Settings.resetWait * 1000));
    }

    private void setLevelWaitTime(Player player) {
        this.levelWaitTime.put(player.getUniqueId(), Calendar.getInstance().getTimeInMillis() + (long)(Settings.levelWait * 1000));
    }

    private long getResetWaitTime(Player player) {
        if (this.resetWaitTime.containsKey(player.getUniqueId())) {
            if (this.resetWaitTime.get(player.getUniqueId()) > Calendar.getInstance().getTimeInMillis()) {
                return (this.resetWaitTime.get(player.getUniqueId()) - Calendar.getInstance().getTimeInMillis()) / 1000;
            }
            return 0;
        }
        return 0;
    }

    private long getLevelWaitTime(Player player) {
        if (this.levelWaitTime.containsKey(player.getUniqueId())) {
            if (this.levelWaitTime.get(player.getUniqueId()) > Calendar.getInstance().getTimeInMillis()) {
                return (this.levelWaitTime.get(player.getUniqueId()) - Calendar.getInstance().getTimeInMillis()) / 1000;
            }
            return 0;
        }
        return 0;
    }

    public void reserveLocation(UUID playerUUID, Location location) {
        this.islandSpot.put(playerUUID, location);
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<String>();
        }
        Player player = (Player)sender;
        if (!VaultHelper.checkPerm(player, "askyblock.island.create")) {
            return new ArrayList<String>();
        }
        UUID playerUUID = player.getUniqueId();
        UUID teamLeader = this.plugin.getPlayers().getTeamLeader(playerUUID);
        List teamMembers = new ArrayList();
        if (teamLeader != null) {
            teamMembers = this.plugin.getPlayers().getMembers(teamLeader);
        }
        ArrayList<String> options = new ArrayList<String>();
        String lastArg = args.length != 0 ? args[args.length - 1] : "";
        switch (args.length) {
            case 0: 
            case 1: {
                options.add("help");
                if (VaultHelper.checkPerm(player, "askyblock.island.go")) {
                    options.add("go");
                }
                if (VaultHelper.checkPerm(player, "askyblock.island.name") && this.plugin.getPlayers().hasIsland(player.getUniqueId())) {
                    options.add("name");
                }
                options.add("about");
                if (this.plugin.getGrid() != null && this.plugin.getGrid().getSpawn() != null) {
                    options.add("spawn");
                }
                if (VaultHelper.checkPerm(player, "askyblock.island.controlpanel")) {
                    options.add("controlpanel");
                    options.add("cp");
                }
                if (VaultHelper.checkPerm(player, "askyblock.island.reset")) {
                    options.add("reset");
                }
                if (VaultHelper.checkPerm(player, "askyblock.island.sethome")) {
                    options.add("sethome");
                }
                if (VaultHelper.checkPerm(player, "askyblock.island.info")) {
                    options.add("level");
                }
                if (VaultHelper.checkPerm(player, "askyblock.island.topten")) {
                    options.add("top");
                }
                if (Settings.useEconomy && VaultHelper.checkPerm(player, "askyblock.island.minishop")) {
                    options.add("minishop");
                    options.add("ms");
                }
                if (VaultHelper.checkPerm(player, "askyblock.island.warp")) {
                    options.add("warp");
                    options.add("warps");
                }
                if (VaultHelper.checkPerm(player, "askyblock.team.create")) {
                    options.add("team");
                    options.add("invite");
                    options.add("leave");
                }
                if (VaultHelper.checkPerm(player, "askyblock.team.kick")) {
                    options.add("kick");
                }
                if (VaultHelper.checkPerm(player, "askyblock.team.join")) {
                    options.add("accept");
                    options.add("reject");
                }
                if (VaultHelper.checkPerm(player, "askyblock.team.makeleader")) {
                    options.add("makeleader");
                }
                if (VaultHelper.checkPerm(player, "askyblock.team.chat")) {
                    options.add("teamchat");
                    options.add("tc");
                }
                if (VaultHelper.checkPerm(player, "askyblock.island.biomes")) {
                    options.add("biomes");
                }
                if (VaultHelper.checkPerm(player, "askyblock.island.expel")) {
                    options.add("expel");
                }
                if (VaultHelper.checkPerm(player, "askyblock.coop")) {
                    options.add("coop");
                    options.add("uncoop");
                    options.add("listcoops");
                }
                if (VaultHelper.checkPerm(player, "askyblock.island.lock")) {
                    options.add("lock");
                }
                if (VaultHelper.checkPerm(player, "askyblock.island.settings")) {
                    options.add("settings");
                }
                if (VaultHelper.checkPerm(player, "askyblock.island.lang")) {
                    options.add("lang");
                }
                if (VaultHelper.checkPerm(player, "askyblock.island.ban")) {
                    options.add("ban");
                    options.add("unban");
                    options.add("banlist");
                }
                if (VaultHelper.checkPerm(player, "askyblock.island.value")) {
                    options.add("value");
                }
                if (!VaultHelper.checkPerm(player, "askyblock.island.spawn") || this.plugin.getGrid() == null || this.plugin.getGrid().getSpawn() == null) break;
                options.add("spawn");
                break;
            }
            case 2: {
                if (args[0].equalsIgnoreCase("make")) {
                    options.addAll(schematics.keySet());
                }
                if (VaultHelper.checkPerm(player, "askyblock.island.sethome") && (args[0].equalsIgnoreCase("go") || args[0].equalsIgnoreCase("sethome"))) {
                    int maxHomes = Settings.maxHomes;
                    for (PermissionAttachmentInfo perms : player.getEffectivePermissions()) {
                        if (perms.getPermission().startsWith("askyblock.island.maxhomes.")) {
                            if (perms.getPermission().contains("askyblock.island.maxhomes.*")) {
                                maxHomes = Settings.maxHomes;
                                break;
                            }
                            String[] spl = perms.getPermission().split("askyblock.island.maxhomes.");
                            if (spl.length > 1) {
                                if (!NumberUtils.isDigits((String)spl[1])) {
                                    this.plugin.getLogger().severe("Player " + player.getName() + " has permission: " + perms.getPermission() + " <-- the last part MUST be a number! Ignoring...");
                                } else {
                                    maxHomes = Math.max(maxHomes, Integer.valueOf(spl[1]));
                                }
                            }
                        }
                        if (maxHomes >= 1) continue;
                        maxHomes = 1;
                    }
                    for (int i = 0; i < maxHomes; ++i) {
                        options.add(Integer.toString(i));
                    }
                }
                if (VaultHelper.checkPerm(player, "askyblock.island.warp") && args[0].equalsIgnoreCase("warp")) {
                    Set<UUID> warpList = this.plugin.getWarpSignsListener().listWarps();
                    for (UUID warp : warpList) {
                        options.add(this.plugin.getPlayers().getName(warp));
                    }
                }
                if (VaultHelper.checkPerm(player, "askyblock.island.info") && args[0].equalsIgnoreCase("level")) {
                    options.addAll(Util.getOnlinePlayerList(player));
                }
                if (VaultHelper.checkPerm(player, "askyblock.team.create") && args[0].equalsIgnoreCase("invite")) {
                    options.addAll(Util.getOnlinePlayerList(player));
                }
                if (VaultHelper.checkPerm(player, "askyblock.coop") && args[0].equalsIgnoreCase("coop")) {
                    options.addAll(Util.getOnlinePlayerList(player));
                }
                if (VaultHelper.checkPerm(player, "askyblock.coop") && args[0].equalsIgnoreCase("uncoop")) {
                    options.addAll(Util.getOnlinePlayerList(player));
                }
                if (VaultHelper.checkPerm(player, "askyblock.island.expel") && args[0].equalsIgnoreCase("expel")) {
                    options.addAll(Util.getOnlinePlayerList(player));
                }
                if (VaultHelper.checkPerm(player, "askyblock.team.kick") && (args[0].equalsIgnoreCase("kick") || args[0].equalsIgnoreCase("remove"))) {
                    for (UUID member : (List<UUID>)teamMembers) {
                        options.add(this.plugin.getPlayers().getName(member));
                    }
                }
                if (VaultHelper.checkPerm(player, "askyblock.team.makeleader") && args[0].equalsIgnoreCase("makeleader")) {
                    for (UUID member : (List<UUID>)teamMembers) {
                        options.add(this.plugin.getPlayers().getName(member));
                    }
                }
                if (VaultHelper.checkPerm(player, "askyblock.island.controlpanel") && (args[0].equalsIgnoreCase("cp") || args[0].equalsIgnoreCase("controlpanel"))) {
                    options.add("on");
                    options.add("off");
                }
                if (VaultHelper.checkPerm(player, "askyblock.island.ban") && args[0].equalsIgnoreCase("ban")) {
                    for (Player banPlayer : this.plugin.getServer().getOnlinePlayers()) {
                        if (banPlayer.isOp() || VaultHelper.checkPerm(banPlayer, "askyblock.admin.noban") || banPlayer.equals((Object)player) || this.plugin.getPlayers().getMembers(playerUUID).contains(banPlayer.getUniqueId())) continue;
                        options.add(banPlayer.getName());
                    }
                }
                if (!VaultHelper.checkPerm(player, "askyblock.island.ban") || !args[0].equalsIgnoreCase("unban")) break;
                for (UUID banPlayer : this.plugin.getPlayers().getBanList(playerUUID)) {
                    options.add(this.plugin.getPlayers().getName(banPlayer));
                }
                break;
            }
        }
        return Util.tabLimit(options, lastArg);
    }

}