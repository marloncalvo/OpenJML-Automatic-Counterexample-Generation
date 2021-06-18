package ucf.cs.research;

public class MyClass {
	
	private int field1;
	public  int field2;
	public final int field3;

	public MyClass(int field1, int field2, int field3) {
		this.field1 = field1;
		this.field2 = field2;
		this.field3 = field3;
	}

	public int getField1() {
		return this.field1;
	}
	public int getField2() { return this.field2; }

}