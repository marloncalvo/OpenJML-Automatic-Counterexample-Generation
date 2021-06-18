package ucf.cs.research;

import org.apache.commons.lang3.reflect.FieldUtils;
import sun.misc.Unsafe;
import java.lang.reflect.Field;

public class InstanceBuilder<T> {

	public final Object instance;
	public final Class cls;

	public InstanceBuilder(Class cls) {
		this.cls = cls;

		try {
			Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
			unsafeField.setAccessible(true);
			Unsafe unsafe = (Unsafe) unsafeField.get(null);

			this.instance = unsafe.allocateInstance(cls);
		} catch (Exception e) {
			throw new RuntimeException("Unable to instantiate " + cls);
		}
	}

	public InstanceBuilder setField(String field, Object value) {
		try {
			Field declaredField = cls.getDeclaredField(field);
			FieldUtils.writeField(declaredField, instance, value, true);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}

		return this;
	}

	public Object build() {
		return instance;
	}
}