package hide.core.sync;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import hide.core.HideBase;
import hide.core.sync.http.AbstractHttpMessage.Method;
import hide.core.sync.http.HideFileReq;
import hide.core.sync.http.HideFileRes;

public class HttpFileServer implements Runnable {

	private static final ListeningExecutorService UPLOAD_EXECUTOR = MoreExecutors.listeningDecorator(Executors
			.newCachedThreadPool((new ThreadFactoryBuilder()).setDaemon(true).setNameFormat("Uploader %d").build()));

	@Override
	public void run() {
		try {
			ServerSocket server = new ServerSocket(8080);
			while (true) {
				System.out.println("packet reseve");
				Socket socket = server.accept();
				ListenableFuture<?> listenablefuture = UPLOAD_EXECUTOR.submit(() -> {
					try (InputStream inStream = socket.getInputStream();
							OutputStream outStream = socket.getOutputStream();) {

						HideFileReq req = new HideFileReq();
						req.readBytes(inStream);
						//まずGetかどうが
						if (req.method == Method.GET) {
							System.out.println(new File("../../../FlanModelToObj-master.zip").getAbsolutePath());
							File file = new File(HideBase.BaseDir, req.target);
							file.getAbsolutePath().startsWith(HideBase.BaseDir.getAbsolutePath());

						}

						System.out.println(req.target + " " + req.method);

						HideFileRes res = new HideFileRes(HttpURLConnection.HTTP_ACCEPTED, "ACCEPT");
						res.setFile(new File("./FlanModelToObj-master.zip"), 52428800);
						res.writeBytes(outStream);

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
