package hide.core.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import hide.core.HideBase;
import hide.core.sync.DataSync.SyncMsg;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**サーバー ⇒ クライアントの同期*/
public class DataSync implements IMessageHandler<SyncMsg, IMessage> {

	/**エントリ登録*/
	public static <T> SyncEntry<T> register(Supplier<T> construct, ISyncInterface<T> syncInterface) {
		SyncEntry<T> entry = new SyncEntry<>(construct, syncInterface);
		entry.index = syncEntries.size();
		syncEntries.add(entry);
		return entry;
	}

	/**IMessageを継承しているならラッパーを省略*/
	public static <T extends IMessage> SyncEntry<T> register(Supplier<T> construct) {
		return register(construct, new ISyncInterface<T>() {
			@Override
			public void fromBytes(T obj, ByteBuf buf) {
				obj.fromBytes(buf);
			}

			@Override
			public void toBytes(T obj, ByteBuf buf) {
				obj.toBytes(buf);
			}
		});
	}

	public static void update(EntityPlayerMP player) {
		syncEntries.forEach(entry -> update(entry, player));
	}

	public static void update(SyncEntry<?> entry, EntityPlayerMP player) {
		if (player == null) {
			HideBase.NETWORK.sendToAll(new SyncMsg(entry));
		} else {
			HideBase.NETWORK.sendTo(new SyncMsg(entry), player);
		}
	}

	public interface ISyncInterface<T> {
		public void fromBytes(T obj, ByteBuf buf);

		public void toBytes(T obj, ByteBuf buf);
	}

	/**エントリホルダ*/
	private static List<SyncEntry<?>> syncEntries = new ArrayList<>();

	/***/
	public static class SyncEntry<T> {
		private int index = -1;
		List<BiConsumer<T, T>> onChange = new ArrayList<>();
		ISyncInterface syncInterface;
		Supplier<T> construct;
		public final T ServerData;
		public T ClientData;

		public void addListener(BiConsumer<T, T> run) {
			onChange.add(run);
		}

		/**クライアントデータに書き込み スレッド注意*/
		void fromBytes(ByteBuf buf) {
			T old = ClientData;
			ClientData = construct.get();
			syncInterface.fromBytes(ClientData, buf);
			onChange.forEach(f -> f.accept(old, ClientData));
		}

		/**サーバーデータからデータ化*/
		void toBytes(ByteBuf buf) {
			syncInterface.toBytes(ServerData, buf);
		}

		private SyncEntry(Supplier<T> construct, ISyncInterface<T> syncInterface) {
			this.construct = construct;
			this.ServerData = construct.get();
			this.ClientData = construct.get();
			this.syncInterface = syncInterface;
		}
	}

	@Override
	public IMessage onMessage(SyncMsg msg, MessageContext ctx) {
		Minecraft.getMinecraft().addScheduledTask(() -> {
			msg.entry.fromBytes(msg.buffer);
			msg.buffer.release();
		});
		return null;
	}

	public static class SyncMsg implements IMessage {
		public SyncMsg() {
		}

		SyncEntry<?> entry;
		ByteBuf buffer;

		public SyncMsg(SyncEntry<?> entry) {
			this.entry = entry;
		}

		@Override
		public void fromBytes(ByteBuf buf) {
			entry = syncEntries.get(buf.readByte());
			buffer = buf.copy();
		}

		@Override
		public void toBytes(ByteBuf buf) {
			buf.writeByte(entry.index);
			entry.toBytes(buf);

		}
	}
}
