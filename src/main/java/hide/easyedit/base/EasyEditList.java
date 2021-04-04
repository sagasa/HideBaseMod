package hide.easyedit.base;

import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.Vec3i;

public class EasyEditList<T> extends EasyEditBase {

	protected List<T> list;
	protected List<DataEntry<T>> entryList;

	public static final DataEntry<Vec3i> vec = of(Vec3i.NULL_VECTOR);

	public static final DataEntry<Vec3i> vec2 = of(Vec3i.NULL_VECTOR);

	public static final DataEntry<Vec3i> vec3 = of(Vec3i.NULL_VECTOR);

	@Override
	public void fromBytes(ByteBuf buf) {

	}

	@Override
	public void toBytes(ByteBuf buf) {

	}

}
