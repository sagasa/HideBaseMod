package hide.core.sync;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

import hide.core.HideBase;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class HideSync {

	/** 利用には登録が必要 */
	public static class SyncDirEntry {
		public SyncDirEntry(String must, String client) {
			MustDir = must;
			ClientDir = client;
		}

		public SyncDirEntry setOnlyManagedFile(boolean flag) {
			onlyMagagedFile = flag;
			return this;
		}

		public SyncDirEntry setAllowDir(String allow) {
			AllowDir = allow;
			return this;
		}

		public SyncDirEntry setNeedRestart(boolean flag) {
			needRestart = flag;
			return this;
		}

		public SyncDirEntry setChangeListener(Runnable listener) {
			clientChange = listener;
			return this;
		}

		public String AllowDir;
		public final String MustDir;
		public final String ClientDir;
		private boolean onlyMagagedFile = false;
		private boolean needRestart = false;
		private Runnable clientChange;
		private byte index = -1;

		@Override
		public String toString() {
			return "SyncDirEntry[MustDir = " + MustDir + "AllowDir = " + AllowDir + ", ClientDir = " + ClientDir + "]";
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}
			if (obj instanceof SyncDirEntry) {
				final SyncDirEntry other = (SyncDirEntry) obj;
				return ObjectUtils.equals(MustDir, other.MustDir) && ObjectUtils.equals(AllowDir, other.AllowDir)
						&& ObjectUtils.equals(ClientDir, other.ClientDir);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return MustDir.hashCode() ^ ClientDir.hashCode() ^ (AllowDir == null ? 0 : AllowDir.hashCode());
		}
	}

	public static final String DeleteTag = ".delete";
	public static final String AddTag = ".add";

	public static final String DeleteDir = "/delete/";

	private static List<SyncDirEntry> entryList = new ArrayList<>();

	public static final SyncDirEntry Mods = new SyncDirEntry("/must/", "/mods/").setOnlyManagedFile(false)
			.setNeedRestart(true).setAllowDir("/allow/");

	static {
		HideSync.registerSyncDir(Mods);
	}

	public static void registerSyncDir(SyncDirEntry sync) {
		if (entryList.contains(sync)) {
			HideBase.log.warn(sync + " is already registered");
			return;
		}
		sync.index = (byte) entryList.size();
		entryList.add(sync);
	}

	public static void makeReq(SyncDirEntry dir) {
		if (dir.index == -1) {
			HideBase.log.error(dir + " is not registered");
			return;
		}
		HideBase.NETWORK.sendToAll(PacketSync.makeStartPacket(dir.index));
	}

	public static void makeReq(SyncDirEntry dir, EntityPlayerMP player) {
		if (dir.index == -1) {
			HideBase.log.error(dir + " is not registered");
			return;
		}
		HideBase.NETWORK.sendTo(PacketSync.makeStartPacket(dir.index), player);
	}

	private static final ThreadPoolExecutor FileIOExecutor = new ThreadPoolExecutor(0, 10, 5000, TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
				private AtomicInteger count = new AtomicInteger(1);

				@Override
				public Thread newThread(Runnable r) {
					Thread thread = new Thread(r, "HideFile I/O Executor Thread-" + count.getAndIncrement());
					thread.setDaemon(true);
					return thread;
				}
			});

	private static Map<File, Pair<Integer, Long>> fileHash = new ConcurrentHashMap<>();

	/** 重いので注意 マルチスレッド対応 */
	private static Integer getOrMakeHash(File file) {
		if (!file.canRead())
			return 0;
		// ハッシュ 更新時間
		Pair<Integer, Long> pair = fileHash.get(file);
		if (pair == null || pair.getRight() < file.lastModified()) {
			try {
				pair = new ImmutablePair<Integer, Long>(toHash(Files.readAllBytes(file.toPath())),
						file.lastModified());
				fileHash.put(file, pair);
			} catch (IOException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
				return 0;
			}
			//System.out.println("calcHash " + pair.getLeft() + " " + file.getName());
		}
		return pair.getLeft();
	}

	/** 4バイトハッシュ */
	private static int toHash(byte[] data) {
		try {
			final MessageDigest md = MessageDigest.getInstance("md2");
			md.update(data);
			return ByteBuffer.wrap(md.digest()).getInt();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return 0;
		}
	}

	/** クライアントサイド 指定ディレクトリ直下のファイルのハッシュを取って送信 */
	static void sentFileState(int index) {
		FileIOExecutor.execute(() -> {
			HideBase.NETWORK.sendToServer(PacketSync.makeHashPacket(index, makeFileState(index)));
		});
	}

	/**だいぶ重い この段階ではマネージファイル以外も送信*/
	private static List<Integer> makeFileState(int index) {
		SyncDirEntry sync = entryList.get(index);
		File HideDir = new File(HideBase.BaseDir, sync.ClientDir);
		if (!HideDir.exists())
			HideDir.mkdirs();
		List<Integer> list = new ArrayList<>();
		for (File file : HideDir.listFiles()) {
			// マネージファイルのみのオプション適応 拡張子が.deleteじゃない
			if (file.isFile() && !file.toPath().endsWith(DeleteTag) && !file.toPath().endsWith(AddTag)) {
				list.add(getOrMakeHash(file));
			}
			HideBase.log.info("client hash " + file.getName() + getOrMakeHash(file));
		}
		return list;
	}

	/** サーバーサイド 受け取ったハッシュとファイルを照合して変更パケットを送信 */
	static void sentFileChange(int index, List<Integer> hashList, EntityPlayerMP player) {
		FileIOExecutor.execute(() -> {
			List<Pair<String, byte[]>> addList = new ArrayList();
			makeFileChange(index, hashList, addList);
			HideBase.log.info("add " + addList + " remove" + hashList);
			HideBase.NETWORK.sendTo(PacketSync.makeEditPacket(index, addList, hashList), player);
		});
	}

	/**だいぶ重い
	 * @param hashList 削除するハッシュになる
	 * @param addList 追加するデータ*/
	private static void makeFileChange(int index, List<Integer> hashList, List<Pair<String, byte[]>> addList) {
		SyncDirEntry sync = entryList.get(index);
		File mustDir = new File(HideBase.BaseDir, sync.MustDir);
		if (!mustDir.exists())
			mustDir.mkdirs();
		Map<Integer, File> mustData = new HashMap<>();
		for (File file : mustDir.listFiles()) {
			if (file.isFile()) {
				mustData.put(getOrMakeHash(file), file);
				//System.out.println("server hash " + file.getName() + " " + getOrMakeHash(file));
			}
		}
		Map<Integer, File> allowData = null;
		if (sync.AllowDir != null) {
			allowData = new HashMap<>();
			File allowDir = new File(HideBase.BaseDir, sync.AllowDir);
			if (!allowDir.exists())
				allowDir.mkdirs();
			for (File file : allowDir.listFiles()) {
				if (file.isFile()) {
					allowData.put(getOrMakeHash(file), file);
				}
			}
		}

		//System.out.println("clientHash " + hashList);
		// 追加が必要な分を抽出 両方から重複分を削除
		for (Iterator hashitr = hashList.iterator(); hashitr.hasNext();)
			if (mustData.remove(hashitr.next()) != null)
				hashitr.remove();

		//System.out.println("remove " + hashList + " add " + mustData.keySet());

		//追加リスト
		for (File file : mustData.values()) {
			try {
				addList.add(new ImmutablePair<String, byte[]>(file.getName(), Files.readAllBytes(file.toPath())));
			} catch (IOException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}
		}
		//削除リストから許可がある分を除く
		if (allowData != null)
			for (Iterator hashitr = hashList.iterator(); hashitr.hasNext();)
				if (allowData.containsKey(hashitr.next()))
					hashitr.remove();
	}

	@SideOnly(Side.CLIENT)
	/**一括適応*/
	public static String applyFileChange(Map<Byte, Pair<List<Pair<String, byte[]>>, List<Integer>>> dataMap) {
		StringBuilder sb = new StringBuilder();
		for (Entry<Byte, Pair<List<Pair<String, byte[]>>, List<Integer>>> entry : dataMap.entrySet()) {
			String str = applyFileChange(entry.getKey(), entry.getValue().getLeft(), entry.getValue().getRight());
			if (str != null)
				sb.append(str);
		}
		return sb.length() == 0 ? null : sb.toString();
	}

	@SideOnly(Side.CLIENT)
	/** クライアントサイド パケットから編集を適応 できなければ理由を返す*/
	public static String applyFileChange(int index, List<Pair<String, byte[]>> addList, List<Integer> removeList) {
		System.out.println(addList + " : " + removeList);
		SyncDirEntry sync = entryList.get(index);
		File HideDir = new File(HideBase.BaseDir, sync.ClientDir);
		File deleteDir = new File(HideBase.BaseDir, DeleteDir);

		if (!deleteDir.exists())
			deleteDir.mkdirs();

		if (!HideDir.exists())
			HideDir.mkdirs();
		Multimap<Integer, File> fileData = MultimapBuilder.hashKeys().arrayListValues().build();
		boolean change = false;
		try {
			for (Pair<String, byte[]> pair : addList) {
				File add = new File(HideDir, pair.getLeft());
				change = true;
				if (add.exists()) {
					if (add.canWrite()) {
						Files.write(add.toPath(), pair.getRight());
					} else {
						//交換予定ファイル
						File alte = new File(add.getPath() + AddTag);
						alte.createNewFile();
						markHideManagedFile(alte.toPath());
						Files.write(alte.toPath(), pair.getRight());
					}
				} else {
					add.createNewFile();
					markHideManagedFile(add.toPath());
					Files.write(add.toPath(), pair.getRight());
				}
			}
			for (File file : HideDir.listFiles()) {
				if (file.isFile() && !file.getName().endsWith(DeleteTag) && !file.getName().endsWith(AddTag)
						&& (!sync.onlyMagagedFile || isHideManagedFile(file.toPath()))) {
					fileData.put(getOrMakeHash(file), file);
				}
			}
			for (Integer hash : removeList) {
				for (File remove : fileData.get(hash)) {
					//System.out.println("remove " + remove.getName());
					change = true;
					File move = new File(deleteDir, remove.getName());
					move.delete();
					//既にあるなら削除
					if (!remove.canWrite() || !remove.renameTo(move)) {
						//System.out.println("cant write");
						// 削除予定ファイル作成
						File delete = new File(remove + DeleteTag);
						delete.createNewFile();
						markHideManagedFile(delete.toPath());
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (change && sync.clientChange != null)
			sync.clientChange.run();
		if (change && sync.needRestart) {
			StringBuilder sb = new StringBuilder("[");
			sb.append(sync.ClientDir);
			sb.append("] ");
			if (addList.size() != 0) {
				sb.append("add {");
				for (Pair<String, byte[]> pair : addList) {
					sb.append(pair.getLeft());
					sb.append(" ");
				}
				sb.append("}");
			}
			if (removeList.size() != 0) {
				sb.append(" remove {");
				for (Integer hash : removeList)
					for (File file : fileData.get(hash)) {
						sb.append(file.getName());
						sb.append(" ");
					}
				sb.append("}");
			}

			return sb.toString();
		}
		return null;

	}

	public static void sendHashToServer(ChannelHandlerContext ctx) {
		Map<Byte, List<Integer>> map = new HashMap<>();
		for (SyncDirEntry entry : entryList) {
			map.put(entry.index, makeFileState(entry.index));
			System.out.println("send " + entry.ClientDir + " hash to server");
		}
		ctx.writeAndFlush(FileData.makeHashPacket(map));
	}

	public static void sendChangeToClient(ChannelHandlerContext ctx, FileData data) {
		Map<Byte, Pair<List<Pair<String, byte[]>>, List<Integer>>> dataMap = new HashMap<>();
		for (SyncDirEntry entry : entryList) {
			byte index = entry.index;
			if (data.hashList.containsKey(index)) {
				List<Pair<String, byte[]>> addList = new ArrayList();
				List<Integer> removeList = data.hashList.get(index);
				makeFileChange(entry.index, removeList, addList);
				System.out.println(addList + " " + removeList);
				dataMap.put(index, ImmutablePair.of(addList, removeList));
			}
		}
		ctx.writeAndFlush(FileData.makeEditPacket(dataMap));
	}

	private static final String HideFileAttributeName = "hideManagedFile";

	private static boolean isHideManagedFile(Path path) {
		UserDefinedFileAttributeView view = Files.getFileAttributeView(path, UserDefinedFileAttributeView.class);
		try {
			return view.list().contains(HideFileAttributeName);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	private static void markHideManagedFile(Path path) throws IOException {
		UserDefinedFileAttributeView view = Files.getFileAttributeView(path, UserDefinedFileAttributeView.class);
		view.write(HideFileAttributeName, ByteBuffer.allocate(1));
	}
}
