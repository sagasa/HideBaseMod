package hide.core.sync.http;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.ParseException;

/**Getしかサポートしないリクエスト*/
public class HideFileReq extends AbstractHttpMessage {

	private static Pattern requestLinePattern = Pattern.compile("^(?<method>\\S+) (?<target>\\S+) (?<version>\\S+)$");

	public HideFileReq(String target) {
		method = Method.GET;
		this.target = target;
		version = PROTOCOL_VERSION;
	}

	public HideFileReq() {
	}

	public Method method;
	public String target;
	public String version;

	@Override
	public void writeBytes(OutputStream outStream) throws IOException {
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(outStream, StandardCharsets.UTF_8));

		bw.write(method + SP + target + SP + version + CRLF);
		writeHeader(bw);
	}

	@Override
	public void readBytes(InputStream inStream) throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(inStream, StandardCharsets.UTF_8));
		String line = br.readLine();
		Matcher matcher = requestLinePattern.matcher(line);
		if (!matcher.matches()) {
			throw new ParseException(line);
		}
		method = Method.valueOf(matcher.group("method"));
		target = matcher.group("target");
		version = matcher.group("version");
		readHeader(br);
	}
}
