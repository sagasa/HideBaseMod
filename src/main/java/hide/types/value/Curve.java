package hide.types.value;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.ArrayUtils;

import hide.core.util.ArrayEditor;
import hide.core.util.IExIterator;

/** キーと任意の数値の配列 Larpして取得
 * 範囲外の場合は最も近い値を返す */
public class Curve implements Cloneable {
	public CurveKey[] Keys = new CurveKey[0];

	public Curve() {
		Keys = new CurveKey[] { new CurveKey(0, 0) };
	}

	public Curve setAdd(Curve target) {
		return apply(Operator.ADD, target);
	}

	public Curve setMultiple(Curve target) {
		return apply(Operator.MULTIPLE, target);
	}

	Curve apply(Operator operator, Curve base) {
		IExIterator<CurveKey> itr_a = ArrayEditor.toIterator(Keys);
		IExIterator<CurveKey> itr_b = ArrayEditor.toIterator(base.Keys);

		float minKey = -Float.MAX_VALUE;
		List<CurveKey> keys = new ArrayList<>();
		while (true) {
			if (!itr_a.hasNext() && !itr_b.hasNext()) {
				break;
			}
			CurveKey min;
			if (!itr_b.hasNext() || itr_a.hasNext() && itr_a.pollNext().Key < itr_b.pollNext().Key)
				min = itr_a.next();
			else
				min = itr_b.next();

			//同じ値はスキップ
			if (minKey == min.Key) {
				continue;
			}
			minKey = min.Key;
			keys.add(new CurveKey(minKey, operator.apply(get(minKey), base.get(minKey))));
		}
		Curve curve = new Curve();
		curve.Keys = keys.toArray(new CurveKey[keys.size()]);
		return curve;
	}

	public float get(float key) {
		Float res = null;
		if (Keys.length == 0)
			res = 0f;
		else if (Keys.length == 1)
			res = Keys[0].Value;
		else if (key <= Keys[0].Key)
			res = Keys[0].Value;
		else if (Keys[Keys.length - 1].Key < key)
			res = Keys[Keys.length - 1].Value;
		else {
			for (int i = 0; i < Keys.length; i++) {
				if (key <= Keys[i].Key) {
					float minKey = 0 < i ? Keys[i - 1].Key : 0;
					float minValue = 0 < i ? Keys[i - 1].Value : 0;
					float maxKey = Keys[i].Key;
					float maxValue = Keys[i].Value;
					// System.out.println(minKey + " " + minValue + " " + maxKey + " " + maxValue);
					res = minValue + ((key - minKey) / (maxKey - minKey) * (maxValue - minValue));
					break;
				}
			}
			if (res == null)
				throw new NullPointerException("");
		}
		return res;
	}

	@Override
	public Curve clone() {
		Curve curve;
		try {
			curve = (Curve) super.clone();
			curve.Keys = new CurveKey[Keys.length];
			for (int i = 0; i < Keys.length; i++) {
				curve.Keys[i] = Keys[i].clone();
			}
			return curve;
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Curve) {
			Curve other = (Curve) obj;
			return Objects.deepEquals(Keys, other.Keys);
		}
		return false;
	}

	@Override
	public String toString() {
		return ArrayUtils.toString(Keys);
	}

	public static class CurveKey implements Cloneable {
		public float Key;
		public float Value;

		public CurveKey() {
		}

		public CurveKey(float key, float value) {
			Key = key;
			Value = value;
		}

		@Override
		public String toString() {
			return String.format("key=%s,Value=%s", Key, Value);
		}

		@Override
		protected CurveKey clone() {
			try {
				return (CurveKey) super.clone();
			} catch (CloneNotSupportedException e) {
				e.printStackTrace();
				return null;
			}
		}
	}

}
