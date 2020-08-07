package hide.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.util.concurrent.ListenableFuture;

import hide.core.sync.http.HideFileRes;
import net.minecraft.util.HttpUtil;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class HideDownloader {

	private static final Logger LOGGER = LogManager.getLogger();

	private static HttpURLConnection connect(final URL url, final Proxy proxy,
			final Map<String, String> requestProperty,
			@Nullable final IProgressUpdate progress) throws IOException {
		if (progress != null) {
			progress.displayLoadingString(I18n.translateToLocal("resourcepack.requesting"));
		}
		HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();

		urlConn.setRequestMethod("GET");
		urlConn.setInstanceFollowRedirects(true);

		float f = 0.0F;
		float f1 = requestProperty.entrySet().size();
		for (Entry<String, String> entry : requestProperty.entrySet()) {
			urlConn.setRequestProperty(entry.getKey(), entry.getValue());
			if (progress != null) {
				progress.setLoadingProgress((int) (++f / f1 * 100.0F));
			}
		}

		urlConn.connect();
		int status = urlConn.getResponseCode();
		//リダイレクト
		if (status == HttpURLConnection.HTTP_MOVED_TEMP
				|| status == HttpURLConnection.HTTP_MOVED_PERM
				|| status == HttpURLConnection.HTTP_SEE_OTHER) {
			urlConn.disconnect();
			return connect(new URL(urlConn.getHeaderField("Location")), proxy, requestProperty, progress);
		}
		return urlConn;
	}

	@SideOnly(Side.CLIENT)
	public static ListenableFuture<Object> downloadFile(final File saveFile, final String packUrl,
			final Map<String, String> requestProperty, final int maxSize, @Nullable final IProgressUpdate progress,
			final Proxy proxy) {
		ListenableFuture<?> listenablefuture = HttpUtil.DOWNLOADER_EXECUTOR.submit(() -> {
			HttpURLConnection urlConn = null;
			InputStream inputstream = null;
			OutputStream outputstream = null;
			if (progress != null) {
				progress.resetProgressAndMessage(I18n.translateToLocal("resourcepack.downloading"));
			}

			try {
				try {
					if (saveFile.exists()) {
						FileUtils.deleteQuietly(saveFile);
					} else if (saveFile.getParentFile() != null) {
						saveFile.getParentFile().mkdirs();
					}

					URL url = new URL(packUrl);

					//コネクションを取得する
					urlConn = connect(url, proxy, requestProperty, progress);

					inputstream = urlConn.getInputStream();
					float total = urlConn.getContentLength();

					if (progress != null) {
						progress.displayLoadingString(I18n.translateToLocalFormatted("resourcepack.progress",
								String.format("%.2f", total / 1000.0F / 1000.0F)));
					}
					if (maxSize > 0 && total > maxSize) {
						if (progress != null) {
							progress.setDoneWorking();
						}
						throw new IOException(
								"Filesize is bigger than maximum allowed (file is " + total + ", limit is " + maxSize
										+ ")");
					}

					HideFileRes fileRes = new HideFileRes().setFile(saveFile, maxSize);
					fileRes.setProgress((int) total, progress);
					fileRes.readBytes(inputstream);

					if (progress != null) {
						progress.setDoneWorking();
						return;
					}
				} catch (Throwable throwable) {
					throwable.printStackTrace();

					if (urlConn != null) {
						try (InputStream inputstream1 = urlConn.getErrorStream()) {
							LOGGER.error(IOUtils.toString(inputstream1));
						} catch (IOException ioexception) {
							ioexception.printStackTrace();
						}
					}

					if (progress != null) {
						progress.setDoneWorking();
						return;
					}
				}
			} finally {
				IOUtils.closeQuietly(inputstream);
				IOUtils.closeQuietly(outputstream);
			}

		});
		return (ListenableFuture<Object>) listenablefuture;
	}
}
