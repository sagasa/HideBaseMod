package hide.core.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import hide.core.HideUtil;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.handshake.FMLHandshakeMessage;

public class FileData extends FMLHandshakeMessage {

	private static final byte SEND_HASH = 1;
	private static final byte SEND_DATA = 2;

	private byte mode = -1;

	public Map<Byte, List<String>> hashList;

	public Map<Byte, Pair<List<Pair<String, String>>, List<String>>> dataMap;

	public static FileData makeHashPacket(Map<Byte, List<String>> list) {
		FileData packet = new FileData();
		packet.mode = SEND_HASH;
		packet.hashList = list;
		return packet;
	}

	public static FileData makeEditPacket(Map<Byte, Pair<List<Pair<String, String>>, List<String>>> data) {
		FileData packet = new FileData();
		packet.mode = SEND_DATA;
		packet.dataMap = data;
		return packet;
	}

	/*
		@Override
		public IMessage onMessage(PacketSync m, MessageContext ctx) {
			if (m.mode == REQ) {
				HideSync.sentFileState(m.index);
			} else if (m.mode == SEND_DATA) {
				HideSync.applyFileChange(m.index, m.addList, m.removeList);
				;
			} else if (m.mode == SEND_HASH) {
				HideSync.makeFileChange(m.index, m.hashList, ctx.getServerHandler().player);
			}
			System.out.println("on msg " + m.mode);
			return null;
		}
	//*/
	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeByte(mode);

		if (mode == SEND_DATA) {
			buf.writeByte(dataMap.size());
			for (Entry<Byte, Pair<List<Pair<String, String>>, List<String>>> entry : dataMap.entrySet()) {
				buf.writeByte(entry.getKey());

				buf.writeShort(entry.getValue().getLeft().size());
				for (Pair<String, String> pair : entry.getValue().getLeft()) {
					HideUtil.writeString(buf, pair.getLeft());
					HideUtil.writeString(buf, pair.getRight());
				}

				buf.writeShort(entry.getValue().getRight().size());
				for (String hash : entry.getValue().getRight()) {
					HideUtil.writeString(buf, hash);
				}
			}
		} else if (mode == SEND_HASH) {
			buf.writeByte(hashList.size());
			for (Entry<Byte, List<String>> entry : hashList.entrySet()) {
				buf.writeByte(entry.getKey());

				buf.writeShort(entry.getValue().size());
				for (String hash : entry.getValue()) {
					HideUtil.writeString(buf, hash);
				}
			}
		}
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		mode = buf.readByte();
		if (mode == SEND_DATA) {
			int mapSize = buf.readByte();
			dataMap = new HashMap<>();
			for (int i = 0; i < mapSize; i++) {
				byte index = buf.readByte();

				int listSize = buf.readShort();
				List<Pair<String, String>> addlist = new ArrayList<>();
				for (int j = 0; j < listSize; j++) {
					addlist.add(new ImmutablePair<String, String>(HideUtil.readString(buf), HideUtil.readString(buf)));
				}
				listSize = buf.readShort();
				List<String> removelist = new ArrayList<>();
				for (int j = 0; j < listSize; j++) {
					removelist.add(HideUtil.readString(buf));
				}
				dataMap.put(index, ImmutablePair.of(addlist, removelist));
			}

		} else if (mode == SEND_HASH) {
			int mapSize = buf.readByte();
			hashList = new HashMap<>();
			for (int i = 0; i < mapSize; i++) {
				byte index = buf.readByte();

				int listSize = buf.readShort();
				List<String> list = new ArrayList<>();
				for (int j = 0; j < listSize; j++) {
					list.add(HideUtil.readString(buf));
				}
				hashList.put(index, list);
			}
		}
	}

	@Override
	public String toString(Class<? extends Enum<?>> side) {
		return "$HideFileData";
	}
}
