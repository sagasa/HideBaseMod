package hide.easyedit.base;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import hide.easyedit.base.EasyEditBase.DataEntry;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**編集のしやすさに重点を置いたデータ型 親クラスのエントリの操作を行わなない
 * Float Integer Boolean Enum EasyEditBase継承クラス に対するシリアライズを提供
 * その他のクラスをメンバーに入れる場合MsgWapperを登録*/
public abstract class EasyEditBase implements IMessage, Iterable<DataEntry<?>> {

	public <R> R get(DataEntry<R> key) {
		R res = data.get(key);
		return res == null ? key.defaultValue : res;
	}

	public <T> void set(DataEntry<T> key, T value) {
		data.set(key, value);
	}

	protected DataMap data = new DataMap();

	/**DataEntryの一覧取得*/
	@Override
	public Iterator<DataEntry<?>> iterator() {
		return classEntryList.get(getClass()).iterator();
	}

	public List<DataEntry<?>> getEntryList() {
		return classEntryList.get(getClass());
	}

	interface MsgWrapper<T> {
		T fromBytes(ByteBuf buf);

		void toBytes(ByteBuf buf, Object value);
	}

	private static Map<Class<?>, MsgWrapper<?>> msgWapper = new HashMap<>();

	protected <T> void registerMsgWrapper(Class<T> clazz, MsgWrapper<T> wapper) {
		msgWapper.put(clazz, wapper);
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		int size = buf.readInt();

		List<DataEntry<?>> list = getEntryList();
		for (int i = 0; i < size; i++) {
			DataEntry entry = list.get(buf.readInt());
			//型判定
			if (entry.Type == ValueType.INTEGER)
				set(entry, buf.readInt());
			else if (entry.Type == ValueType.FLOAT)
				set(entry, buf.readFloat());
			else if (entry.Type == ValueType.BOOL)
				set(entry, buf.readBoolean());
			else if (entry.Type == ValueType.STRING)
				set(entry, ByteBufUtils.readUTF8String(buf));
			else if (entry.Type == ValueType.ENUM)
				set(entry, Enum.valueOf(entry.EnumClass, ByteBufUtils.readUTF8String(buf)));
			else if (entry.Type == ValueType.EASYEDIT) {
				if (get(entry) == null)
					set(entry, makeInstance((Class) entry.defaultValue.getClass()));
				((EasyEditBase) get(entry)).fromBytes(buf);
			} else {
				MsgWrapper encoder = msgWapper.get(entry.defaultValue.getClass());
				if (encoder != null)
					set(entry, encoder.fromBytes(buf));
			}
		}
	}

	@Override
	public void toBytes(ByteBuf buf) {
		Iterator<DataEntry<?>> itr = iterator();
		int lengthIndex = buf.writerIndex();
		buf.writerIndex(lengthIndex + 4);

		int size = 0;
		while (itr.hasNext()) {
			EasyEditBase.DataEntry<?> entry = (EasyEditBase.DataEntry<?>) itr.next();
			Object obj = data.get(entry);
			if (obj != null) {
				buf.writeInt(entry.index);
				//型判定
				if (entry.Type == ValueType.INTEGER)
					buf.writeInt((Integer) obj);
				else if (entry.Type == ValueType.FLOAT)
					buf.writeFloat((Float) obj);
				else if (entry.Type == ValueType.BOOL)
					buf.writeBoolean((Boolean) obj);
				else if (entry.Type == ValueType.BOOL)
					ByteBufUtils.writeUTF8String(buf, (String) obj);
				else if (entry.Type == ValueType.ENUM)
					ByteBufUtils.writeUTF8String(buf, obj.toString());
				else if (entry.Type == ValueType.EASYEDIT)
					((EasyEditBase) obj).toBytes(buf);
				else {
					MsgWrapper encoder = msgWapper.get(obj.getClass());
					if (encoder != null)
						encoder.toBytes(buf, obj);
				}
			}
		}
		buf.setInt(lengthIndex, size);
	}

	public enum ValueType {
		INTEGER, FLOAT, BOOL, STRING, ENUM, EASYEDIT, OBJECT
	}

	protected static <T> DataEntry<T> of(T value) {
		return of(value, 0.0f, 1.0f);
	}

	protected static <T> DataEntry<T> of(T value, float min, float max) {
		//Integer
		if (Integer.class.isAssignableFrom(value.getClass()))
			return new DataEntry<T>(value, ValueType.INTEGER, min, max, null);
		else if (Float.class.isAssignableFrom(value.getClass()))
			return new DataEntry<T>(value, ValueType.FLOAT, min, max, null);
		else if (Boolean.class.isAssignableFrom(value.getClass()))
			return new DataEntry<T>(value, ValueType.BOOL, 0, 0, null);
		else if (String.class.isAssignableFrom(value.getClass()))
			return new DataEntry<T>(value, ValueType.STRING, 0, 0, null);
		else if (value.getClass().isEnum())
			return new DataEntry<T>(value, ValueType.ENUM, 0, 0, (Class<Enum>) value.getClass());
		else if (EasyEditBase.class.isAssignableFrom(value.getClass()))
			return new DataEntry<T>(value, ValueType.EASYEDIT, 0, 0, null);
		return new DataEntry<T>(value, ValueType.OBJECT, 0, 0, null);
	}

	public static class DataEntry<T> {
		protected int index = -1;
		protected String Name;
		public final T defaultValue;

		private DataEntry(T value, ValueType type, float min, float max, Class<? extends Enum> clazz) {
			defaultValue = value;
			Type = type;
			Max = max;
			Min = min;
			EnumClass = clazz;
		}

		/**型判別*/
		public final ValueType Type;
		//数値型の場合
		public final float Max;
		public final float Min;
		//Enumの場合
		public final Class<? extends Enum> EnumClass;

	}

	private static Map<Class<? extends EasyEditBase>, Map<String, DataEntry<?>>> classEntryMap = new HashMap<>();
	private static Map<Class<? extends EasyEditBase>, List<DataEntry<?>>> classEntryList = new HashMap<>();
	private static Map<String, Class<? extends EasyEditBase>> nameTypeMap = new HashMap<>();

	/** Classからシンプルネームに */
	public static String getTypeName(Class<?> clazz) {
		return clazz.getSimpleName();
	}

	/** staticにDataEntryを名前から検索するための登録 */
	private static boolean initEntry(Class<? extends EasyEditBase> clazz) {
		if (!nameTypeMap.containsKey(getTypeName(clazz))) {
			System.out.println("Start init");
			if (!classEntryMap.containsKey(clazz)) {
				// 無ければ作成
				List<DataEntry<?>> list = new ArrayList<>();
				registerEntry(clazz, list);
				Builder<String, DataEntry<?>> builder = ImmutableMap.builder();
				list.forEach((e) -> builder.put(e.Name, e));
				classEntryMap.put(clazz, builder.build());
				classEntryList.put(clazz, ImmutableList.copyOf(list));
			}
			nameTypeMap.put(getTypeName(clazz), clazz);
		}
		return true;
	}

	/** public static final */
	private static final int Modifiers = Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL;

	/** 親クラスから順に登録する -1なら登録エラー */
	private static void registerEntry(Class<? extends EasyEditBase> clazz, List<DataEntry<?>> list) {
		try {
			int index = 0;
			for (Field field : clazz.getDeclaredFields()) {
				if (DataEntry.class.isAssignableFrom(field.getType())) {
					if (field.getModifiers() == Modifiers) {
						DataEntry<?> entry = (DataEntry<?>) field.get(null);
						if (entry == null) {
							throw new NullPointerException("entry field is null");
						}
						// Indexがずれたら
						if (entry.index != -1 && entry.index != index)
							throw new RuntimeException("indexがずれた 再設計 " + entry.index + " " + index);
						entry.index = index;
						index++;
						entry.Name = field.getName();
						list.add(entry);
					}
				}
			}
		} catch (IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		}
	}

	public static class JsonInterface implements JsonSerializer<EasyEditBase>, JsonDeserializer<EasyEditBase> {
		@Override
		public EasyEditBase deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			if (!json.isJsonObject())
				throw new JsonParseException("is not JsonObject");
			JsonObject obj = json.getAsJsonObject();
			Class<? extends EasyEditBase> container = nameTypeMap.get(obj.get("Type").getAsString());

			if (container == null)
				throw new JsonParseException("bad typename " + obj.get("Type"));

			EasyEditBase database = makeInstance(container);

			Iterator<DataEntry<?>> itr = database.iterator();

			JsonObject values = obj.getAsJsonObject("Values");

			while (itr.hasNext()) {
				DataEntry entry = (EasyEditBase.DataEntry<?>) itr.next();
				if (values.has(entry.Name)) {
					Object putValue = context.deserialize(values.get(entry.Name), entry.defaultValue.getClass());
					database.set(entry, putValue);
				}
			}
			return database;
		}

		@Override
		public JsonElement serialize(EasyEditBase src, Type typeOfSrc, JsonSerializationContext context) {

			JsonObject obj = new JsonObject();
			obj.addProperty("Type", getTypeName(src.getClass()));
			JsonObject value = new JsonObject();
			obj.add("Values", value);
			Iterator<DataEntry<?>> itr = src.iterator();
			while (itr.hasNext()) {
				EasyEditBase.DataEntry<?> entry = (EasyEditBase.DataEntry<?>) itr.next();
				Object o = src.data.get(entry);
				if (o != null)
					value.add(entry.Name, context.serialize(o));
			}
			return obj;
		}

	}

	private static <T extends EasyEditBase> T makeInstance(Class<T> clazz) {
		try {
			return clazz.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

	private static Gson gson = new GsonBuilder().setPrettyPrinting()
			.registerTypeHierarchyAdapter(EasyEditBase.class, new JsonInterface())
			.create();

	public String toJson() {
		return gson.toJson(this);
	}

	public static class Path<T> {

	}

	private class DataMap {
		Object[] values;

		public DataMap() {
			initEntry(EasyEditBase.this.getClass());
			Map<String, DataEntry<?>> map = classEntryMap.get(EasyEditBase.this.getClass());
			values = new Object[map.size()];

		}

		<T> T get(DataEntry<T> key) {
			return (T) values[key.index];
		}

		<T> void set(DataEntry<T> key, T value) {
			values[key.index] = value;
		}
	}
}
