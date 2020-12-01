package hide.core;

import java.io.File;
import java.io.IOException;

import org.apache.logging.log4j.Logger;

import hide.core.ops.CommandOPLevel;
import hide.core.sync.HideSync;
import hide.core.sync.HideSync.SyncDirEntry;
import hide.core.sync.HttpFileServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod(modid = HideBase.MODID, name = HideBase.NAME)
public class HideBase {
	public static final String MODID = "hidebase";
	public static final String NAME = "HideBase";

	@Mod.Instance(MODID)
	public static HideBase INSTANCE;
	public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("HideBase");

	public static Logger log;

	public static Logger getLog() {
		return log;
	}

	public static File BaseDir;

	public static final SyncDirEntry HideDirEntry = new SyncDirEntry("/Hide/", "/Hide/").setOnlyManagedFile(false)
			.setNeedRestart(true);

	public static Process test;

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		log = event.getModLog();
		BaseDir = event.getModConfigurationDirectory().getParentFile();
		// ネットワーク系
		/*
		 * IMesssageHandlerクラスとMessageクラスの登録。 第三引数：MessageクラスのMOD内での登録ID。256個登録できる
		 * 第四引数：送り先指定。クライアントかサーバーか、Side.CLIENT Side.SERVER
		 */

		HideSync.registerSyncDir(HideDirEntry);
	}

	@EventHandler
	public void construct(FMLConstructionEvent event) {
		MinecraftForge.EVENT_BUS.register(this);
		MinecraftForge.EVENT_BUS.register(new HideSync());
	}

	@EventHandler
	public void init(FMLInitializationEvent event) throws IOException {
		for (ModContainer mod : Loader.instance().getModList()) {
			log.info(mod);
		}
	}

	@EventHandler
	public void serverStart(FMLServerStartingEvent event) {
		event.registerServerCommand(new CommandSuicide());
		if (event.getServer().isDedicatedServer()) {
			event.registerServerCommand(new CommandOPLevel());
			Thread thread = new Thread(new HttpFileServer(event.getServer().getServerPort() + 1));
			thread.setDaemon(true);
			thread.start();
		}
	}

	//切断時にデータを削除
	@SubscribeEvent
	public void onEvent(PlayerLoggedOutEvent event) {
		HidePlayerDataManager.clearServerData(event.player);
	}

	@SideOnly(Side.CLIENT)
	@SubscribeEvent
	public void onEvent(ClientConnectedToServerEvent event) {
		HidePlayerDataManager.clearClientData();
	}
}