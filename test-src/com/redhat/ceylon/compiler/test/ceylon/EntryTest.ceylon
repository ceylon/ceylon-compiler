shared class EntryTest() extends Test() {
	
	@test
	shared void testKey() {
		Integer key = +1;
		String item = "One";		
		Entry<Integer, String> first = Entry(key,item); 
		assertTrue(key == first.key);	
	}
	
	@test
	shared void testItem() {
		Integer key = +1;
		String item = "One";		
		Entry<Integer, String> first = Entry(key,item); 
		assertTrue(item == first.item);	
	}	
		
	@test
	shared void testEquals() {
		Integer key1 = +1;
		String item1 = "One";
		Entry<Integer, String> first = Entry(key1,item1);
		Entry<Integer, String> anotherFirst = Entry(key1,item1);
		Integer key2 = +2;
		String item2 = "Two";
		Entry<Integer, String> second = Entry(key2,item2);
		assertEquals(first, anotherFirst);
		assertFalse(first == second);
		Entry<Integer, String> mixed = Entry(key1,item2);
		assertFalse(first == mixed);
		assertFalse(second == mixed);
	}
	
	@test
	shared void testHash() {
		Entry<Integer, String> first = Entry(+1,"One");
		Integer firstHash = first.hash;
		assertEquals(first.hash, firstHash);
	}
	
	@test
	shared void testString() {
		Entry<Integer, String> first = Entry(+1,"One");
		String name = first.string;		
	}	
	
}	  