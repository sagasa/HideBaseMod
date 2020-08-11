package hide.core.sync;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
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
		public SyncDirEntry(String must, String... client) {
			MustDir = must;
			ClientDir = client;
		}

		public SyncDirEntry(String must, String client) {
			this(must, new String[] { client });
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
		public final String ClientDir[];
		private boolean onlyMagagedFile = false;
		private boolean needRestart = false;
		private Runnable clientChange;
		private byte index = -1;

		@Override
		public String toString() {
			return "SyncDirEntry[MustDir = " + MustDir + "AllowDir = " + AllowDir + ", ClientDir = "
					+ ArrayUtils.toString(ClientDir) + "]";
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

	public static final SyncDirEntry Mods = new SyncDirEntry("/must/", "/mods/", "/mods/1.12.2/")
			.setOnlyManagedFile(false)
			.setNeedRestart(true).setAllowDir("/allow/");

	static {
		HideSync.registerSyncDir(Mods);
	}

	public static ImmutableList<SyncDirEntry> getSyncEntry() {
		return ImmutableList.copyOf(entryList);
	}

	public static void registerSyncDir(SyncDirEntry sync) {
		if (entryList.contains(sync)) {
			HideBase.log.warn(sync + " is already registered");
			return;
		}
		sync.index = (byte) entryList.size();
		entryList.add(sync);
	}

	private static Map<File, Pair<String, Long>> fileHash = new ConcurrentHashMap<>();

	/** 重いので注意 マルチスレッド対応 */
	static String getOrMakeHash(File file) {
		if (!file.canRead())
			return "";
		// ハッシュ 更新時間
		Pair<String, Long> pair = fileHash.get(file);
		if (pair == null || pair.getRight() < file.lastModified()) {
			try (FileInputStream ins = new FileInputStream(file)) {
				pair = new ImmutablePair<String, Long>(DigestUtils.sha1Hex(ins), file.lastModified());
				fileHash.put(file, pair);
			} catch (IOException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
				return "";
			}
			//System.out.println("calcHash " + pair.getLeft() + " " + file.getName());
		}
		return pair.getLeft();
	}

	/**だいぶ重い この段階ではマネージファイル以外も送信*/
	private static List<String> makeFileState(int index) {
		SyncDirEntry sync = entryList.get(index);
		List<String> list = new ArrayList<>();
		for (String str : sync.ClientDir) {
			File HideDir = new File(HideBase.BaseDir, str);
			if (!HideDir.exists())
				return list;

			for (File file : HideDir.listFiles()) {
				// マネージファイルのみのオプション適応 拡張子が.deleteじゃない
				if (file.isFile() && !file.toPath().endsWith(DeleteTag) && !file.toPath().endsWith(AddTag)) {
					list.add(getOrMakeHash(file));
					HideBase.log.info("calc hash " + file.getName() + " = " + getOrMakeHash(file));
				}

			}
		}
		return list;
	}

	public static void sendHashToServer(ChannelHandlerContext ctx) {
		Map<Byte, List<String>> map = new HashMap<>();
		for (SyncDirEntry entry : entryList) {
			System.out.println("send " + ArrayUtils.toString(entry.ClientDir) + " hash to server");
			map.put(entry.index, makeFileState(entry.index));
		}
		ctx.writeAndFlush(FileData.makeHashPacket(map));
	}

	/**だいぶ重い
	 * @param hashList 削除するハッシュになる
	 * @param addList 追加するデータ*/
	private static void makeFileChange(int index, List<String> hashList, List<Pair<String, String>> addList) {
		SyncDirEntry sync = entryList.get(index);
		File mustDir = new File(HideBase.BaseDir, sync.MustDir);
		if (!mustDir.exists())
			mustDir.mkdirs();
		Map<String, File> mustData = new HashMap<>();
		for (File file : mustDir.listFiles()) {
			if (file.isFile()) {
				mustData.put(getOrMakeHash(file), file);
				//System.out.println("server hash " + file.getName() + " " + getOrMakeHash(file));
			}
		}
		Map<String, File> allowData = null;
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
			addList.add(new ImmutablePair<String, String>(file.getName(), getOrMakeHash(file)));
		}
		//削除リストから許可がある分を除く
		if (allowData != null)
			for (Iterator hashitr = hashList.iterator(); hashitr.hasNext();)
				if (allowData.containsKey(hashitr.next()))
					hashitr.remove();
	}

	public static void sendChangeToClient(ChannelHandlerContext ctx, FileData data) {
		Map<Byte, Pair<List<Pair<String, String>>, List<String>>> dataMap = new HashMap<>();
		for (SyncDirEntry entry : entryList) {
			byte index = entry.index;
			if (data.hashList.containsKey(index)) {
				List<Pair<String, String>> addList = new ArrayList();
				List<String> removeList = data.hashList.get(index);
				makeFileChange(entry.index, removeList, addList);
				System.out.println(addList + " " + removeList);
				dataMap.put(index, ImmutablePair.of(addList, removeList));
			}
		}
		ctx.writeAndFlush(FileData.makeEditPacket(dataMap));
	}

	//==== ログイン後のアップデート ====

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

	/** クライアントサイド 指定ディレクトリ直下のファイルのハッシュを取って送信 */
	static void sentFileState(int index) {
		FileIOExecutor.execute(() -> {
			HideBase.NETWORK.sendToServer(PacketSync.makeHashPacket(index, makeFileState(index)));
		});
	}

	/** サーバーサイド 受け取ったハッシュとファイルを照合して変更パケットを送信 */
	static void sentFileChange(int index, List<String> hashList, EntityPlayerMP player) {
		FileIOExecutor.execute(() -> {
			List<Pair<String, String>> addList = new ArrayList();
			makeFileChange(index, hashList, addList);
			HideBase.log.info("add " + addList + " remove" + hashList);
			HideBase.NETWORK.sendTo(PacketSync.makeEditPacket(index, addList, hashList), player);
		});
	}

	@SideOnly(Side.CLIENT)
	/**一括適応*/
	public static String applyFileChange(Map<Byte, Pair<List<Pair<String, String>>, List<String>>> dataMap) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (Entry<Byte, Pair<List<Pair<String, String>>, List<String>>> entry : dataMap.entrySet()) {
			String str = applyFileChange(entry.getKey(), entry.getValue().getLeft(), entry.getValue().getRight());
			if (str != null) {
				if (first)
					first = false;
				else
					sb.append(":");
				sb.append(str);
			}
		}
		return sb.length() == 0 ? null : sb.toString();
	}

	/**面倒だからフィールドにした*/
	private static File deleteDir;
	public static final Queue<Pair<File, String>> DownloadQueue = new LinkedList<>();

	@SideOnly(Side.CLIENT)
	/** クライアントサイド パケットから編集を適応 できなければ理由を返す*/
	protected static String applyFileChange(int index, List<Pair<String, String>> addList, List<String> removeList) {
		System.out.println(addList + " : " + removeList);
		SyncDirEntry sync = entryList.get(index);
		File[] HideDir = Arrays.asList(sync.ClientDir).stream().map(str -> new File(HideBase.BaseDir, str))
				.toArray(File[]::new);
		deleteDir = new File(HideBase.BaseDir, DeleteDir);
		if (!deleteDir.exists())
			deleteDir.mkdirs();
		//FileMap作成
		Multimap<String, File> fileData = MultimapBuilder.hashKeys().arrayListValues().build();
		for (File hide : HideDir) {
			if (!hide.exists())
				continue;

			for (File file : hide.listFiles())
				if (file.isFile() && !file.getName().endsWith(DeleteTag) && !file.getName().endsWith(AddTag))
					fileData.put(getOrMakeHash(file), file);
		}
		try {
			for (Pair<String, String> pair : addList)
				DownloadQueue.add(new ImmutablePair<>(addFile(new File(HideDir[0], pair.getLeft())), pair.getRight()));

			for (String hash : removeList)
				for (File remove : fileData.get(hash))
					removeFile(remove);

		} catch (IOException e) {
			e.printStackTrace();
		}

		//変更があったら
		if (0 < addList.size() || 0 < removeList.size()) {
			if (sync.clientChange != null)
				sync.clientChange.run();

			if (sync.needRestart) {
				StringBuilder sb = new StringBuilder();

				for (Pair<String, String> pair : addList) {
					sb.append(pair.getLeft());
					sb.append("|");
				}
				sb.append(":");
				for (String hash : removeList)
					for (File file : fileData.get(hash)) {
						sb.append(file.getName());
						sb.append("|");
					}

				return sb.toString();
			}
		}

		return null;
	}

	private static File addFile(File to) throws IOException {
		if (to.exists()) {
			if (to.canWrite()) {
				return to;
			}
			//元を削除
			removeFile(to);
			//交換予定ファイル
			File alte = new File(to.getPath() + AddTag);
			alte.createNewFile();
			return alte;

		}
		to.createNewFile();
		return to;
	}

	/**ファイルを削除する 削除が完了したらTrue*/
	private static boolean removeFile(File from) throws IOException {
		if (!from.exists())
			return true;
		File to = new File(deleteDir, from.getName());
		//既にあるなら削除
		to.delete();
		if (!from.canWrite() || !from.renameTo(to)) {
			// 削除予定ファイル作成
			File delete = new File(from + DeleteTag);
			delete.createNewFile();
			return false;
		}
		return true;
	}
}
