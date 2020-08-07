package hide.core;

import java.io.File;
import java.io.IOException;

import org.apache.logging.log4j.Logger;

import hide.core.gui.GuiRestart;
import hide.core.sync.HideSync;
import hide.core.sync.HideSync.SyncDirEntry;
import hide.core.sync.HttpFileServer;
import hide.core.sync.PacketSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraftforge.client.event.GuiOpenEvent;
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

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		log = event.getModLog();
		BaseDir = event.getModConfigurationDirectory().getParentFile();
		// ネットワーク系
		/*
		 * IMesssageHandlerクラスとMessageクラスの登録。 第三引数：MessageクラスのMOD内での登録ID。256個登録できる
		 * 第四引数：送り先指定。クライアントかサーバーか、Side.CLIENT Side.SERVER
		 */
		NETWORK.registerMessage(PacketSync.class, PacketSync.class, 0, Side.SERVER);
		NETWORK.registerMessage(PacketSync.class, PacketSync.class, 1, Side.CLIENT);

		HideSync.registerSyncDir(HideDirEntry);
	}

	@EventHandler
	public void construct(FMLConstructionEvent event) {
		MinecraftForge.EVENT_BUS.register(this);
	}

	@EventHandler
	public void init(FMLInitializationEvent event) throws IOException {
		for (ModContainer mod : Loader.instance().getModList()) {
			log.info(mod);
		}
		if (event.getSide() == Side.CLIENT) {
			Thread thread = new Thread(new HttpFileServer());
			thread.setDaemon(true);
			thread.start();

			File test = new File(BaseDir, "test");
			test.delete();
			System.out.println("Download test start");
			HideDownloader.downloadFile(test, "http://127.0.0.1:8080/test.jar",
					ResourcePackRepository.getDownloadHeaders(), 52428800, null, Minecraft.getMinecraft().getProxy());
		}

	}

	@EventHandler
	public void serverStart(FMLServerStartingEvent event) {
		event.registerServerCommand(new CommandSuicide());
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

	@SideOnly(Side.CLIENT)
	@SubscribeEvent
	public void onEvent(GuiOpenEvent event) {
		if (event.getGui() instanceof GuiDisconnected) {
			GuiDisconnected gui = (GuiDisconnected) event.getGui();
			if (gui.message.getUnformattedText().startsWith("hidebase.restart:"))
				event.setGui(
						new GuiRestart(gui.message.getUnformattedComponentText().replace("hidebase.restart:", "")));
		}
	}
}