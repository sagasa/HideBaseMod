package hide.core.gui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.util.Strings;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import hide.core.HideBase;
import hide.core.sync.HideDownloader;
import hide.core.sync.HideSync;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.client.GuiErrorBase;
import net.minecraftforge.fml.common.Loader;

/**ファイルの変更のため再起動が必要*/
public class GuiRestart extends GuiErrorBase {

	private List<String> removedFile = new ArrayList<>();
	private List<GuiDownloadEntry> download = new ArrayList<>();
	private List<ListenableFuture<Object>> taskList = new ArrayList<>();
	private AtomicInteger runCount = new AtomicInteger();

	public GuiRestart(String msg) {
		String[] split = msg.split(":");
		for (int i = 0; i < split.length; i++)
			if (i % 2 == 1)
				for (String str : split[i].split("\\|"))
					if (Strings.isNotEmpty(str))
						removedFile.add(str);

	}

	private boolean noError = true;

	private void onEndTask(boolean ok) {
		noError = noError && ok;
		if (runCount.decrementAndGet() <= 0) {
			exitButton.id = 21;
			exitButton.displayString = I18n.format("menu.quit");

			if (noError) {
				msgColor = 0x0FFF0F;
				msg = I18n.format("hidebase.restart.success");
			} else {
				msgColor = 0xFF0F0F;
				msg = I18n.format("hidebase.restart.failed");
			}

			System.out.println("All End");
			//Hide/Update.jarを起動する
			try {
				Runtime rt = Runtime.getRuntime();
				List<String> list = new ArrayList();
				File modDir = Loader.instance().getConfigDir().getParentFile();
				list.add(new File(modDir, HideSync.DeleteDir).toString());
				for (String str : HideSync.Mods.ClientDir)
					list.add(new File(modDir, str).toString());
				//プロセスが起きているなら
				if (!rt.exec("java -jar " + modDir.toPath() + "/Hide/Updater.jar " + Strings.join(list, ' '))
						.isAlive()) {
					noJavaMsg = I18n.format("hidebase.nojava");
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}

	private GuiButton exitButton;

	@Override
	public void initGui() {
		super.initGui();
		this.buttonList.clear();
		this.buttonList.add(new GuiButton(10, this.width / 2 + 5, this.height - 38, this.width / 2 - 55, 20,
				I18n.format("fml.button.open.mods.folder")));

		exitButton = new GuiButton(20, 50, this.height - 38, this.width / 2 - 55, 20, I18n.format("gui.cancel"));
		buttonList.add(exitButton);

		//ダウンロードがあるなら
		if (0 < HideSync.DownloadQueue.size()) {
			Pair<File, String> entry;

			while ((entry = HideSync.DownloadQueue.poll()) != null) {
				GuiDownloadEntry gui = new GuiDownloadEntry(this, mc.fontRenderer.FONT_HEIGHT + 4,
						entry.getLeft().getName());
				runCount.incrementAndGet();
				//ダウンロードを開始して終了時の処理を設定
				ListenableFuture<Object> task = HideDownloader.downloadFile(entry.getLeft(), entry.getRight(),
						512000000, gui);
				taskList.add(task);
				Futures.addCallback(task,
						new FutureCallback<Object>() {
							@Override
							public void onSuccess(Object result) {
								onEndTask(true);
							}

							@Override
							public void onFailure(Throwable t) {
								HideBase.log.warn("Download canceled");
								onEndTask(false);
							}
						});
				download.add(gui);
			}
		} else {
			onEndTask(true);
		}
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		super.actionPerformed(button);
		switch (button.id) {
		case 20:
			taskList.forEach(task -> task.cancel(true));
			mc.displayGuiScreen(new GuiMainMenu());
			break;
		case 21:
			mc.shutdown();
			break;
		}
	}

	private String noJavaMsg = null;
	private String msg = I18n.format("hidebase.restart.work");
	private int msgColor = 0xFFFF0F;

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawDefaultBackground();
		//1番上
		this.drawCenteredString(this.fontRenderer, msg, this.width / 2, 20, msgColor);

		if (noJavaMsg != null) {
			this.drawCenteredString(this.fontRenderer, noJavaMsg, this.width / 2, 40, 0xFFFF0F);
		}
		super.drawScreen(mouseX, mouseY, partialTicks);

		int yPos = 36;
		for (GuiDownloadEntry entry : download) {
			entry.draw(50, yPos);
			yPos += mc.fontRenderer.FONT_HEIGHT + 6;
		}
		yPos += 20;
		this.drawCenteredString(this.fontRenderer, "Removed Files", this.width / 2, yPos, 0xFFFFFF);
		yPos += 16;
		for (String file : removedFile) {
			this.drawCenteredString(this.fontRenderer, file, this.width / 2, yPos, 0xAA4444);
			yPos += mc.fontRenderer.FONT_HEIGHT + 4;
		}
	}
}
