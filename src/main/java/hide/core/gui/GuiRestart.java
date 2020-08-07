package hide.core.gui;

import net.minecraftforge.fml.client.GuiErrorBase;

/**ファイルの変更のため再起動が必要*/
public class GuiRestart extends GuiErrorBase {

	public GuiRestart(String msg) {
		this.msg = msg;
	}

	private String msg;

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawDefaultBackground();
		this.drawCenteredString(this.fontRenderer, "Mod updated Please restart", this.width / 2,
				this.height / 2 - 26, 11184810);
		this.drawCenteredString(this.fontRenderer, msg, this.width / 2,
				this.height / 2, 0xEEEEEE);
		super.drawScreen(mouseX, mouseY, partialTicks);
	}
}
