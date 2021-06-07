package hide.core.util;

import java.util.Iterator;

public interface IExIterator<T> extends Iterator<T> {
	/**進めずに取得*/
	T pollNext();
}
