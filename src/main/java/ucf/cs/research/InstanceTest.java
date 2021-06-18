package ucf.cs.research;

public class InstanceTest {

	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		MyClass instance =
			(MyClass) new InstanceBuilder(MyClass.class)
			.setField("field1", 123)
			.setField("field2", 234)
			.build();

		System.out.println(instance.getField1());
		System.out.println(instance.getField2());
	}

}