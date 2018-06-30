package us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.logging.Level;

import community.xamlo.wg.UsbHelper;
public class uSkyBlock {

	 public static void log(Level level, String message) {
	        UsbHelper.log(level, message, null);
	    }
	    public static void log(Level level, String message, Throwable t) {
	    	UsbHelper.getInstance().getLogger().log(level, message, t);
	    }
}
