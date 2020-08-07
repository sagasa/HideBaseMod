package hide.core.sync.http;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.ParseException;

import net.minecraft.util.StringUtils;

public abstract class AbstractHttpMessage {
	protected Map<String, String> headers;

	public AbstractHttpMessage() {
		this.headers = new HashMap<>();
	}

	protected static final String PROTOCOL_VERSION = "HTTP/1.1";
	protected static final String SP = " ";
	protected static final String CRLF = "\r\n";

	private static Pattern headerPattern = Pattern.compile("^(?<name>\\S+):[ \\t]?(?<value>.+)[ \\t]?$");

	public abstract void writeBytes(OutputStream outStream) throws IOException;

	public abstract void readBytes(InputStream inStream) throws IOException;

	protected void readHeader(BufferedReader br) throws IOException, ParseException {
		while (true) {
			String headerField = br.readLine();
			if (StringUtils.isNullOrEmpty(headerField.trim()))
				break;

			Matcher matcher = headerPattern.matcher(headerField);
			if (matcher.matches()) {
				headers.put(matcher.group("name").toLowerCase(), matcher.group("value"));
			} else {
				throw new ParseException(headerField);
			}
		}
	}

	protected void writeHeader(BufferedWriter bw) throws IOException {
		for (Map.Entry<String, String> entry : headers.entrySet()) {
			bw.write(entry.getKey() + ":" + SP + entry.getValue() + CRLF);
		}
		bw.write(CRLF); // ヘッダーとボディの区切りに空行が必要
		bw.flush();
	}

	public void addHeaderField(String name, String value) {
		this.headers.put(name, value);
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public enum Method {
		GET, HEAD, POST, PUT, DELETE, CONNECT, OPTIONS, TRACE, PATCH
	}
}
