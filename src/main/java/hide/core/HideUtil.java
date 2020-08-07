package hide.core;

import java.nio.charset.Charset;

import io.netty.buffer.ByteBuf;

public class HideUtil {
	public static final Charset UTF8 = Charset.forName("UTF-8");

	/** バッファに文字列を書き込む */
	public static void writeString(ByteBuf buf, String str) {
		byte[] data = str.getBytes(UTF8);
		buf.writeInt(data.length);
		buf.writeBytes(data);
	}

	/** バッファから文字列を読み込む */
	public static String readString(ByteBuf buf) {
		int length = buf.readInt();
		return buf.readBytes(length).toString(UTF8);
	}

	/** バッファにbyte配列を書き込む */
	public static void writeBytes(ByteBuf buf, byte[] data) {
		buf.writeInt(data.length);
		buf.writeBytes(data);
	}

	/** バッファからbyte配列を読み込む */
	public static byte[] readBytes(ByteBuf buf) {
		byte[] res = new byte[buf.readInt()];
		buf.readBytes(res);
		return res;
	}
}
