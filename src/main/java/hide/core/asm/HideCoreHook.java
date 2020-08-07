package hide.core.asm;

import java.io.File;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import hide.core.HideBase;
import hide.core.sync.FileData;
import hide.core.sync.HideSync;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.network.handshake.FMLHandshakeMessage;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class HideCoreHook {
	/** 起動時のModファイル削除 */
	public static void hookPreLoadMod() {
		File dir = new File(Loader.instance().getConfigDir().getParentFile(), HideSync.Mods.ClientDir);
		File deleteDir = new File(Loader.instance().getConfigDir().getParentFile(), HideSync.DeleteDir);
		File[] test = dir.listFiles();
		if (test != null)
			for (File file : dir.listFiles()) {
				if (file.getName().endsWith(HideSync.DeleteTag)) {
					file.delete();
					File mod = new File(dir, file.getName().replace(HideSync.DeleteTag, ""));
					File move = new File(deleteDir, mod.getName());
					if (!mod.renameTo(move))
						System.err.println("delete error mod " + mod.getName() + " is coremod");

				} else if (file.getName().endsWith(HideSync.AddTag)) {
					File mod = new File(dir, file.getName().replace(HideSync.AddTag, ""));
					if (!file.renameTo(mod))
						System.err.println("rename error mod " + mod.getName() + " is coremod");
					file.delete();
				}
			}
	}

	/** trueでキャンセル*/
	public static boolean hookOnClientReceveServerData(ChannelHandlerContext ctx, FMLHandshakeMessage msg,
			Consumer cons, Object error) {
		if (msg instanceof FileData) {
			FileData data = (FileData) msg;

			String change = HideSync.applyFileChange(data.dataMap);
			if (change != null) {
				cons.accept(error);
				NetworkDispatcher dispatcher = ctx.channel().attr(NetworkDispatcher.FML_DISPATCHER).get();
				dispatcher.rejectHandshake("hidebase.restart:" + change);
			}
			//System.out.println("実験は成功だ");
			return true;
		}
		//System.out.println("HOOK Clinet DATA!!!!!!!!!!!!!!!");
		return false;
	}

	public static Function<Minecraft, GuiNewChat> GuiNewChat;

	@SideOnly(Side.CLIENT)
	public static GuiNewChat getGuiNewChat(Minecraft mc) {
		if (GuiNewChat != null)
			return GuiNewChat.apply(mc);
		return new GuiNewChat(mc);
	}

	public static Class<? extends FMLHandshakeMessage> getAdditionalClass() {
		return FileData.class;
	}

	public static void hookOnClientHello(ChannelHandlerContext ctx) {

		if (!ctx.channel().attr(NetworkDispatcher.IS_LOCAL).get()) {
			HideBase.log.info("Start send file hash");
			HideSync.sendHashToServer(ctx);
			HideBase.log.info("End send file hash");
		}

		//System.out.println("HOOK Clinet HELLO!!!!!!!!!!!!!!!");
	}

	/** trueでキャンセル*/
	public static boolean hookOnServerHello(ChannelHandlerContext ctx, FMLHandshakeMessage msg) {
		if (msg instanceof FileData) {
			HideSync.sendChangeToClient(ctx, (FileData) msg);
			System.out.println("実験は成功だ");
			return true;
		}
		System.out.println("HOOK Server HELLO!!!!!!!!!!!!!!!" + msg.getClass());
		return false;
	}

	public static Consumer<RenderLivingBase<EntityLivingBase>> OnMakeLivingRender;

	@SideOnly(Side.CLIENT)
	public static void hookOnMakeLivingRender(RenderLivingBase<EntityLivingBase> render) {
		if (OnMakeLivingRender != null)
			OnMakeLivingRender.accept(render);
	}

	public static BiConsumer<ModelBiped, Entity> OnSetAngle;

	@SideOnly(Side.CLIENT)
	public static void hookOnSetAngle(ModelBiped model, Entity entity) {
		if (OnSetAngle != null)
			OnSetAngle.accept(model, entity);
	}

	/** 左クリックにフック trueでキャンセル */
	public static Function<Minecraft, Boolean> OnLeftClick;

	/** 左クリックにフック trueでキャンセル */
	@SideOnly(Side.CLIENT)
	public static boolean hookOnLeftClick(Minecraft mc) {
		if (OnLeftClick != null)
			return OnLeftClick.apply(mc);
		return false;
	}
}
