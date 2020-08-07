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

	public Map<Byte, List<Integer>> hashList;

	public Map<Byte, Pair<List<Pair<String, byte[]>>, List<Integer>>> dataMap;

	public static FileData makeHashPacket(Map<Byte, List<Integer>> list) {
		FileData packet = new FileData();
		packet.mode = SEND_HASH;
		packet.hashList = list;
		return packet;
	}

	public static FileData makeEditPacket(Map<Byte, Pair<List<Pair<String, byte[]>>, List<Integer>>> data) {
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
			for (Entry<Byte, Pair<List<Pair<String, byte[]>>, List<Integer>>> entry : dataMap.entrySet()) {
				buf.writeByte(entry.getKey());

				buf.writeShort(entry.getValue().getLeft().size());
				for (Pair<String, byte[]> pair : entry.getValue().getLeft()) {
					HideUtil.writeString(buf, pair.getLeft());
					HideUtil.writeBytes(buf, pair.getRight());
				}

				buf.writeShort(entry.getValue().getRight().size());
				for (Integer hash : entry.getValue().getRight()) {
					buf.writeInt(hash);
				}
			}
		} else if (mode == SEND_HASH) {
			buf.writeByte(hashList.size());
			for (Entry<Byte, List<Integer>> entry : hashList.entrySet()) {
				buf.writeByte(entry.getKey());

				buf.writeShort(entry.getValue().size());
				for (Integer hash : entry.getValue()) {
					buf.writeInt(hash);
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
				List<Pair<String, byte[]>> addlist = new ArrayList<>();
				for (int j = 0; j < listSize; j++) {
					addlist.add(new ImmutablePair<String, byte[]>(HideUtil.readString(buf), HideUtil.readBytes(buf)));
				}
				listSize = buf.readShort();
				List<Integer> removelist = new ArrayList<>();
				for (int j = 0; j < listSize; j++) {
					removelist.add(buf.readInt());
				}
				dataMap.put(index, ImmutablePair.of(addlist, removelist));
			}

		} else if (mode == SEND_HASH) {
			int mapSize = buf.readByte();
			hashList = new HashMap<>();
			for (int i = 0; i < mapSize; i++) {
				byte index = buf.readByte();

				int listSize = buf.readShort();
				List<Integer> list = new ArrayList<>();
				for (int j = 0; j < listSize; j++) {
					list.add(buf.readInt());
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
