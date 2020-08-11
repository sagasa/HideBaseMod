package hide.core.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.IProgressUpdate;

public class GuiDownloadEntry extends Gui implements IProgressUpdate {

	private Minecraft mc;

	private String msg;
	private String name;
	private float progress;
	private boolean isWorking = true;

	@Override
	public void displaySavingString(String message) {
		msg = message;

	}

	@Override
	public void resetProgressAndMessage(String message) {
		msg = message;
		progress = 0;
	}

	@Override
	public void displayLoadingString(String message) {
		msg = message;
	}

	@Override
	public void setLoadingProgress(int progress) {
		this.progress = progress / 100f;

	}

	@Override
	public void setDoneWorking() {
		isWorking = false;
	}

	public int height;
	public GuiScreen root;

	public GuiDownloadEntry(GuiScreen root, int height, String name) {
		this.height = height;
		this.root = root;
		this.name = name;
		mc = Minecraft.getMinecraft();
	}

	public void draw(int x, int y) {

		drawHorizontalLine(x, root.width - 50, y, 0xAAAAAAAA);
		drawHorizontalLine(x, root.width - 50, y + height - 1, 0xAAAAAAAA);
		int pro = x + (int) ((root.width - 50 - x) * progress);
		drawRect(x, y + 1, pro, y + height - 1, 0xFF0FAA0F);
		mc.fontRenderer.drawString(name, x + 2, y + 2, isWorking?0xFFFFAA:0x004400);
		mc.fontRenderer.drawString(msg, root.width / 2 + 20, y + 2, 0xBBBBBB);
	}
}
