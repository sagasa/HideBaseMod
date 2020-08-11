package hide.core.sync;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import hide.core.HideUtil;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketSync implements IMessage, IMessageHandler<PacketSync, IMessage> {

	private static final byte EMPTY = -1;
	// **サーバーからクライアントへ 現在のパックの状況を送るようリクエスト*/
	private static final byte REQ = 0;
	// **クライアントからサーバーへ 現在のパック状態の送信*/
	private static final byte SEND_HASH = 1;
	private static final byte SEND_DATA = 2;

	private byte mode = EMPTY;

	private int index;

	private List<String> hashList;

	private List<String> removeList;
	private List<Pair<String, String>> addList;

	public static PacketSync makeStartPacket(int index) {
		PacketSync packet = new PacketSync();
		packet.mode = REQ;
		packet.index = index;
		return packet;
	}

	public static PacketSync makeHashPacket(int index, List<String> list) {
		PacketSync packet = new PacketSync();
		packet.mode = SEND_HASH;
		packet.hashList = list;
		packet.index = index;
		return packet;
	}

	public static PacketSync makeEditPacket(int index, List<Pair<String, String>> addList, List<String> remove) {
		PacketSync packet = new PacketSync();
		packet.mode = SEND_DATA;
		packet.removeList = remove;
		packet.addList = addList;
		packet.index = index;
		return packet;
	}

	@Override
	public IMessage onMessage(PacketSync m, MessageContext ctx) {
		if (m.mode == REQ) {
			HideSync.sentFileState(m.index);
		} else if (m.mode == SEND_DATA) {
			HideSync.applyFileChange(m.index, m.addList, m.removeList);
		} else if (m.mode == SEND_HASH) {
			HideSync.sentFileChange(m.index, m.hashList, ctx.getServerHandler().player);
		}
		System.out.println("on msg " + m.mode);
		return null;
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeByte(mode);
		buf.writeByte(index);
		if (mode == SEND_DATA) {
			buf.writeInt(addList.size());
			for (Pair<String, String> pair : addList) {
				HideUtil.writeString(buf, pair.getLeft());
				HideUtil.writeString(buf, pair.getRight());
			}
			buf.writeInt(removeList.size());
			for (String hash : removeList) {
				HideUtil.writeString(buf, hash);
			}
		} else if (mode == SEND_HASH) {
			buf.writeInt(hashList.size());
			for (String hash : hashList) {
				HideUtil.writeString(buf, hash);
			}
		}
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		mode = buf.readByte();
		index = buf.readByte();
		if (mode == SEND_DATA) {
			int length = buf.readInt();
			addList = new ArrayList<>();
			for (int i = 0; i < length; i++) {
				addList.add(new ImmutablePair<String, String>(HideUtil.readString(buf), HideUtil.readString(buf)));
			}
			length = buf.readInt();
			removeList = new ArrayList<>();
			for (int i = 0; i < length; i++) {
				removeList.add(HideUtil.readString(buf));
			}
		} else if (mode == SEND_HASH) {
			int size = buf.readInt();
			hashList = new ArrayList<>();
			for (int i = 0; i < size; i++) {
				hashList.add(HideUtil.readString(buf));
			}
		}
	}
}