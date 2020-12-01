package hide.core.ops;

import com.mojang.authlib.GameProfile;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketEntityStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.UserListOpsEntry;

public class OPUtil {
	/**変更に成功したらTrue*/
	public static boolean setOpLevel(MinecraftServer server, GameProfile user, int level) {
		HideUserListOps ops = (HideUserListOps) server.getPlayerList().getOppedPlayers();
		UserListOpsEntry entry = ops.getEntry(user);
		if (entry != null && entry != HideUserListOps.defaultEntry) {
			ops.addEntry(new UserListOpsEntry(user, level, entry.bypassesPlayerLimit()));
			sendPlayerPermissionLevel(server.getPlayerList().getPlayerByUUID(user.getId()), level);
			return true;
		}
		return false;
	}

	public static void addOp(MinecraftServer server, GameProfile user, int level, boolean bypassesPlayerLimitIn) {
		HideUserListOps ops = (HideUserListOps) server.getPlayerList().getOppedPlayers();
		ops.addEntry(new UserListOpsEntry(user, level, bypassesPlayerLimitIn));
		sendPlayerPermissionLevel(server.getPlayerList().getPlayerByUUID(user.getId()), level);
	}

	public static void removeOp(MinecraftServer server, GameProfile user) {
		server.getPlayerList().removeOp(user);
	}

	private static void sendPlayerPermissionLevel(EntityPlayerMP player, int permLevel) {
		System.out.println("send OP prop");
		if (player != null && player.connection != null) {
			byte b0;

			if (permLevel <= 0) {
				b0 = 24;
			} else if (permLevel >= 4) {
				b0 = 28;
			} else {
				b0 = (byte) (24 + permLevel);
			}

			player.connection.sendPacket(new SPacketEntityStatus(player, b0));
		}
	}
}
