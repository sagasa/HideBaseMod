package hide.core.sync;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import hide.core.HideBase;
import hide.core.sync.HideSync.SyncDirEntry;
import hide.core.sync.http.AbstractHttpMessage.Method;
import hide.core.sync.http.HideFileReq;
import hide.core.sync.http.HideFileRes;

/**サーバー側のファイルを送信する 512MBまで*/
public class HttpFileServer implements Runnable {

	private static final Logger LOGGER = LogManager.getLogger();

	private static final ListeningExecutorService UPLOAD_EXECUTOR = MoreExecutors.listeningDecorator(Executors
			.newCachedThreadPool((new ThreadFactoryBuilder()).setDaemon(true).setNameFormat("Uploader %d").build()));

	/**Hash-File Map*/
	private Map<String, File> fileMap = new ConcurrentHashMap<>();

	/**ファイルのハッシュを再取得 キャッシング付き*/
	private void reload() {
		for (SyncDirEntry entry : HideSync.getSyncEntry()) {
			addFiles(entry.MustDir);
			addFiles(entry.AllowDir);
		}
	}

	/**fileMapに追加*/
	private void addFiles(String path) {
		if (path != null) {
			File[] files = new File(HideBase.BaseDir, path).listFiles();
			if (files != null)
				for (File file : files) {
					if (file.isFile()) {
						fileMap.put(HideSync.getOrMakeHash(file), file);
					}
				}
		}
	}

	private int port;

	public HttpFileServer(int port) {
		this.port = port;
	}

	@Override
	public void run() {
		LOGGER.info("Start file server at " + port);
		try (ServerSocket server = new ServerSocket(port)) {
			while (true) {
				Socket socket = server.accept();
				ListenableFuture<?> listenablefuture = UPLOAD_EXECUTOR.submit(() -> {
					try (InputStream inStream = socket.getInputStream();
							OutputStream outStream = socket.getOutputStream();) {
						LOGGER.info("receive request from" + socket.getRemoteSocketAddress());
						HideFileReq req = new HideFileReq();
						req.readBytes(inStream);
						String name = req.target.replace("/", "").toLowerCase();
						//まずGetかどうが
						if (!req.method.equals(Method.GET) || Strings.isNullOrEmpty(name)) {
							new HideFileRes(HttpURLConnection.HTTP_BAD_REQUEST, "ONLY GET").writeBytes(outStream);
							LOGGER.warn("bad request from" + socket.getRemoteSocketAddress());
							return;
						}
						reload();
						if (fileMap.containsKey(name)) {

							File file = fileMap.get(name);
							HideFileRes res = new HideFileRes(HttpURLConnection.HTTP_ACCEPTED, "ACCEPT");
							res.setFile(file, 512000000);
							res.addHeaderField("Content-Length", String.valueOf(file.length()));
							res.writeBytes(outStream);
							LOGGER.info("sent File " + file.getName());
						} else {
							new HideFileRes(HttpURLConnection.HTTP_NOT_FOUND, "NOT FOUND").writeBytes(outStream);
							LOGGER.warn("file not found " + name + " from " + socket.getRemoteSocketAddress());
							return;
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				});

				Futures.addCallback(listenablefuture, new FutureCallback<Object>() {

					@Override
					public void onSuccess(Object result) {
						System.out.println("success");
					}

					@Override
					public void onFailure(Throwable t) {
						// TODO 自動生成されたメソッド・スタブ
						t.printStackTrace();
					}

				});
			}
		} catch (IOException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}

	}
}
