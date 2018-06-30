package community.xamlo.wg;

import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import com.wasteofplastic.askyblock.ASkyBlock;
import com.wasteofplastic.askyblock.util.Util;

public class WGHelper {
	/**   need add it
	 * use: deny 
	 * lava-fire: deny 
	 * lava-flow: deny 
	 * chest-access: deny 
	 * enderman-grief:
	 * deny potion-splash: 
	 * deny water-flow: deny 
	 * ghast-fireball: deny 
	 * pvp: deny
	 */

	static WorldGuardPlugin worldGuard = WGHelper.getWorldGuard();
	static RegionManager regionManager = worldGuard.getRegionManager(Bukkit.getWorld("ASkyBlock"));
	static RegionManager NetherRegionManager = worldGuard.getRegionManager(Bukkit.getWorld("ASkyBlock_nether"));
	
	public static WorldGuardPlugin getWorldGuard() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin( "WorldGuard" );
        if (plugin == null || !(plugin instanceof WorldGuardPlugin)) {
            return null;
        }
        return (WorldGuardPlugin)plugin;
    }
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static ProtectedCuboidRegion setRegionFlags(CommandSender sender, String regionName) {
		try {
			Location islandLocation = ASkyBlock.getPlugin().getPlayers().getIslandLocation(((Entity) sender).getUniqueId());
			BlockVector minPoint = WGHelper.getProtectionVectorRight(islandLocation);
			BlockVector maxPoint = WGHelper.getProtectionVectorLeft(islandLocation);
			if (minPoint != null && maxPoint != null) { // Возможно она не нужна

				if (regionName != null && regionName.endsWith("nether")) {
					minPoint = new BlockVector(minPoint.setY(3));
					maxPoint = new BlockVector(maxPoint.setY(120));
					Util.sendMessage((CommandSender) sender, "Нужна ли эта проверка???");

				}
				
				ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionName, minPoint, maxPoint);

				region.getOwners().addPlayer(sender.getName());
				region.setPriority(100);
				
				region.setFlag((Flag) DefaultFlag.USE, (Object) StateFlag.State.DENY);
				region.setFlag((Flag) DefaultFlag.LAVA_FLOW, (Object) StateFlag.State.DENY);
				region.setFlag((Flag) DefaultFlag.LAVA_FIRE , (Object) StateFlag.State.DENY);
				region.setFlag((Flag) DefaultFlag.CHEST_ACCESS, (Object) StateFlag.State.DENY);
				region.setFlag((Flag) DefaultFlag.ENDER_BUILD, (Object) StateFlag.State.DENY);
				region.setFlag((Flag) DefaultFlag.POTION_SPLASH, (Object) StateFlag.State.DENY);
				region.setFlag((Flag) DefaultFlag.WATER_FLOW, (Object) StateFlag.State.DENY);
				region.setFlag((Flag) DefaultFlag.GHAST_FIREBALL, (Object) StateFlag.State.DENY);


				region.setFlag((Flag) DefaultFlag.PVP, (Object) StateFlag.State.DENY);
				region.setFlag((Flag) DefaultFlag.ENTITY_ITEM_FRAME_DESTROY, (Object) StateFlag.State.DENY);
				region.setFlag((Flag) DefaultFlag.ENTITY_PAINTING_DESTROY, (Object) StateFlag.State.DENY);
				region.setFlag((Flag) DefaultFlag.DESTROY_VEHICLE, (Object) StateFlag.State.DENY);
				region.setFlag((Flag) DefaultFlag.GREET_MESSAGE,(Object) DefaultFlag.GREET_MESSAGE.parseInput(WGHelper.getWorldGuard(), sender,"\u00a7d** You are entering \u00a7b" + sender.getName() + " \u00a7disland."));
				region.setFlag((Flag) DefaultFlag.FAREWELL_MESSAGE,(Object) DefaultFlag.FAREWELL_MESSAGE.parseInput(WGHelper.getWorldGuard(), sender,"\u00a7d** You are leaving \u00a7b" + sender.getName() + " \u00a7disland."));
				return region;
			}
			Util.sendMessage((CommandSender) sender, "Чо, пацаны, NPE???");

			return null;
		} catch (InvalidFlagFormat e) {
			e.printStackTrace();
			return null;
		}
		
	}
		
	public static void protectIsland(CommandSender sender) {
		String regionName = sender.getName() + "_island";
        ProtectedCuboidRegion region = WGHelper.setRegionFlags(sender, regionName);
        regionManager.addRegion((ProtectedRegion)region);
        protectNetherIsland(sender);
	}
	public static void protectNetherIsland(CommandSender sender) {
		String regionName = sender.getName() + "_island";
		ProtectedCuboidRegion region = WGHelper.setRegionFlags(sender, regionName);
		NetherRegionManager.addRegion((ProtectedRegion) region);
	}
    public static BlockVector getProtectionVectorLeft(Location island) {
        return new BlockVector(island.getX() + (double)50 - 1.0, 255.0, island.getZ() + (double)50 - 1.0);
    }
    public static BlockVector getProtectionVectorRight(Location island) {
        return new BlockVector(island.getX() - (double)50, 0.0, island.getZ() - (double)50);
    }
  
    public static void addMember( String member, String sender) {
		ProtectedCuboidRegion region = (ProtectedCuboidRegion) regionManager.getRegion(sender);
		ProtectedCuboidRegion netherRegion = (ProtectedCuboidRegion) NetherRegionManager.getRegion(sender);

		region.getMembers().addPlayer(member);
		netherRegion.getMembers().addPlayer(member);
    }
    public static void RemoveMember( String member, String sender) {
		ProtectedCuboidRegion region = (ProtectedCuboidRegion) regionManager.getRegion(sender);
		ProtectedCuboidRegion netherRegion = (ProtectedCuboidRegion) NetherRegionManager.getRegion(sender);

		region.getMembers().removePlayer(member);
		netherRegion.getMembers().removePlayer(member);  	
    }
    
    public static void deleteProtectedRegion(CommandSender sender ) {
    	if (regionManager.getRegion(sender.getName()+"_island") != null)
    		regionManager.removeRegion(sender.getName()+"_island");
		deleteNetherProtectedRegion(sender);
    }
    public static void deleteNetherProtectedRegion(CommandSender sender ) {
    	if (NetherRegionManager.getRegion(sender.getName()+"_island") != null)
    		NetherRegionManager.removeRegion(sender.getName()+"_island");
    }	
}
