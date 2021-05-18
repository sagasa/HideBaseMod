package hide.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class HidePlayerDataManager {

	private static Map<UUID, Map<Class, IHidePlayerData>> serverDataMap = new ConcurrentHashMap<>();

	private static Map<Class, IHidePlayerData> clientData = new ConcurrentHashMap<>();

	private static List<Class> serverDataList = new ArrayList<>();

	public static void register(Class<? extends IHidePlayerData> clazz, Side side) {
		if (side == Side.SERVER) {
			serverDataList.add(clazz);
		} else if (side == Side.CLIENT) {
			addClientData(clazz);
		}
	}

	static void clearServerData(EntityPlayer player) {
		serverDataMap.remove(player.getUniqueID());
	}

	@SideOnly(Side.CLIENT)
	static void clearClientData() {
		for (Class clazz : clientData.keySet())
			clientData.put(clazz, makeData(clazz, Minecraft.getMinecraft().player));
	}

	@SideOnly(Side.CLIENT)
	private static void addClientData(Class clazz) {
		clientData.put(clazz, makeData(clazz, Minecraft.getMinecraft().player));
	}

	@SideOnly(Side.CLIENT)
	public static <T extends IHidePlayerData> T getClientData(Class<T> clazz) {
		return (T) clientData.get(clazz);
	}

	/** プレイヤーデータを取得*/
	public static <T extends IHidePlayerData> T getServerData(Class<T> clazz, EntityPlayer player) {
		UUID uuid = player.getUniqueID();
		Map<Class, IHidePlayerData> map = serverDataMap.get(uuid);
		if (map == null) {
			map = makeData(player);
			serverDataMap.put(uuid, map);
		}
		return (T) map.get(clazz);
	}

	private static Map<Class, IHidePlayerData> makeData(EntityPlayer player) {
		Map<Class, IHidePlayerData> map = new ConcurrentHashMap<>();
		for (Class clazz : serverDataList)
			map.put(clazz, makeData(clazz, player));
		return map;
	}

	private static IHidePlayerData makeData(Class<? extends IHidePlayerData> clazz, EntityPlayer player) {
		try {
			IHidePlayerData data = clazz.newInstance();
			data.init(player);
			return data;
		} catch (InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

	public interface IHidePlayerData {
		public void init(EntityPlayer player);
	}

	static void respawn(EntityPlayer player) {
		if (player instanceof EntityPlayerMP) {
			UUID uuid = player.getUniqueID();
			if (serverDataMap.containsKey(uuid))
				serverDataMap.get(uuid).values().forEach(v -> v.init(player));
		} else {
			clientData.values().forEach(v -> v.init(player));
		}

	}
}
