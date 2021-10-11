package hide.types.base;

import hide.types.base.DataBase.DataEntry;

public interface IDataHolder {
	default <T> T get(DataEntry<T> key) {
		return get(key, key.Default);
	}

	boolean hasValue(DataEntry key);

	<T> T get(DataEntry<T> key, T base);
}
