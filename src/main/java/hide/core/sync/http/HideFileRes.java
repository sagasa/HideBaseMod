package hide.core.sync.http;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.util.IProgressUpdate;

public class HideFileRes extends AbstractHttpMessage {

	private static Pattern responseLinePattern = Pattern.compile("^(?<version>\\S+) (?<code>\\S+) (?<phrase>\\S+)$");

	private static final Logger LOGGER = LogManager.getLogger();

	public HideFileRes() {

	}

	public HideFileRes(int code, String phrase) {
		this.code = code;
		this.phrase = phrase;
		this.version = PROTOCOL_VERSION;
	}

	public HideFileRes setFile(File file, int max) {
		this.file = file;
		this.maxSize = max;
		return this;
	}

	public HideFileRes setProgress(int total, IProgressUpdate progress) {
		this.total = total;
		this.progress = progress;
		return this;
	}

	public int code;
	public String phrase;
	public String version;

	private File file;
	private int maxSize;
	private int total;
	private IProgressUpdate progress;

	@Override
	public void writeBytes(OutputStream outStream) throws IOException {
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(outStream, StandardCharsets.UTF_8));
		bw.write(version + SP + code + SP + phrase + CRLF);
		writeHeader(bw);

		try (DataInputStream inStream = new DataInputStream(new FileInputStream(file));) {
			int count = 0;
			int i;
			byte[] abyte = new byte[4096];
			while ((i = inStream.read(abyte)) >= 0) {
				count += i;

				if (progress != null) {
					progress.setLoadingProgress((int) (count / total * 100.0F));
				}

				if (maxSize > 0 && count > (float) maxSize) {
					if (progress != null) {
						progress.setDoneWorking();
					}
					throw new IOException("Filesize was bigger than maximum allowed (got >= " + count
							+ ", limit was " + maxSize + ")");
				}

				if (Thread.interrupted()) {
					LOGGER.error("INTERRUPTED");
					if (progress != null) {
						progress.setDoneWorking();
					}
					return;
				}
				outStream.write(abyte, 0, i);
			}
		}
	}

	@Override
	public void readBytes(InputStream inStream) throws IOException {
		//BufferedReader br = new BufferedReader(new InputStreamReader(inStream, StandardCharsets.UTF_8));


		//Matcher matcher = responseLinePattern.matcher(br.readLine());
		//if (!matcher.matches()) {
		//	throw new ParseException(line);
		//}
		//version = matcher.group("version");
		//code = Integer.valueOf(matcher.group("code"));
		//phrase = matcher.group("phrase");

		//readHeader(br);

		try (DataOutputStream outputstream = new DataOutputStream(new FileOutputStream(file));) {
			int count = 0;
			int i;
			byte[] abyte = new byte[4096];
			while ((i = inStream.read(abyte)) >= 0) {
				count += i;

				if (progress != null) {
					progress.setLoadingProgress((int) (count / total * 100.0F));
				}

				if (maxSize > 0 && count > (float) maxSize) {
					if (progress != null) {
						progress.setDoneWorking();
					}
					throw new IOException("Filesize was bigger than maximum allowed (got >= " + count
							+ ", limit was " + maxSize + ")");
				}

				if (Thread.interrupted()) {
					LOGGER.error("INTERRUPTED");
					if (progress != null) {
						progress.setDoneWorking();
					}
					return;
				}
				outputstream.write(abyte, 0, i);
			}
		}
	}

}
