package hide.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class HidePlayerDataManager {

	private static Map<EntityPlayer, Map<Class, Object>> serverDataMap = new ConcurrentHashMap<>();

	private static Map<Class, Object> clientData = new ConcurrentHashMap<>();

	private static List<Class> serverDataList = new ArrayList<>();

	public static void register(Class<? extends IHidePlayerData> clazz, Side side) {
		if (side == Side.SERVER) {
			serverDataList.add(clazz);
		} else if (side == Side.CLIENT) {
			addClientData(clazz);
		}
	}

	static void clearServerData(EntityPlayer player) {
		serverDataMap.remove(player);
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
	public static <T> T getClientData(Class<T> clazz) {
		return (T) clientData.get(clazz);
	}

	/** プレイヤーデータを取得*/
	public static <T> T getServerData(Class<T> clazz, EntityPlayer player) {
		Map<Class, Object> map = serverDataMap.get(player);
		if (map == null) {
			map = makeData(player);
			serverDataMap.put(player, map);
		}
		return (T) map.get(clazz);
	}

	private static Map<Class, Object> makeData(EntityPlayer player) {
		Map<Class, Object> map = new ConcurrentHashMap<>();
		for (Class clazz : serverDataList)
			map.put(clazz, makeData(clazz, player));
		return map;
	}

	private static Object makeData(Class clazz, EntityPlayer player) {
		try {
			IHidePlayerData data = (IHidePlayerData) clazz.newInstance();
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
}
