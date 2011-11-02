shared class FloatTest() extends Test() {

	@test
	shared void testEqual() {
		Float one = +1.5;
		Float anotherOne = +1.5;
		Float two = +2.0;
		assertEquals(one, anotherOne);
		assertFalse(one == two);
	}
	
    @test
    shared void testHash() {
        Float one = +1.5;
        Float anotherOne = +1.5;
        Float two = +2.0;
        assertEquals(one.hash, anotherOne.hash);
        assertFalse(one.hash == two.hash);
    }	
    
	@test
	shared void testZero() {
		Float one = +1.1;
		Float two = +2.2;
		Float zero = +0.0;
		assertTrue(zero.zero);
		assertFalse(one.zero);
		assertFalse(two.zero);
	}
	
	@test
	shared void testUnit() {
		Float one = +1.0;
		Float one2 = +1.1;
		Float zero = +0.0;
		assertFalse(zero.unit);
		assertTrue(one.unit);
		assertFalse(one2.unit);
	}
	
	@test
	shared void testNegativeValue() {
		Float positive = +1.5;
		Float negative = -1.5;
		Float zero = +0.0;		
		assertEquals(zero,zero.negativeValue);
		assertEquals(negative,positive.negativeValue);
		assertEquals(positive,negative.negativeValue);
		assertFalse(negative == negative.negativeValue);
		assertFalse(positive == positive.negativeValue);
	}
	
	@test
	shared void testPositiveValue() {
		Float positive = +1.5;
		Float negative = -1.5;
		Float zero = +0.0;		
		assertEquals(zero,zero.positiveValue);
		assertEquals(positive,positive.positiveValue);
		assertFalse(positive == negative.positiveValue);
		assertEquals(negative,negative.positiveValue);
		assertFalse(negative == positive.positiveValue);
	}
	
	@test
	shared void testMinus() {
		Float ten = +10.0;
		Float seven = +7.0;
		Float minusThree = -3.0;
		Float zero = +0.0;		
		assertEquals(zero,ten.minus(ten));
		assertEquals(+3.0,ten.minus(seven));
		assertEquals(minusThree,seven.minus(ten));
		assertEquals(-10.0,minusThree.minus(seven));
		assertEquals(+2.0, +10.0 - +8.0);				
	}
	
    @test
    shared void testPlus() {
        Float ten = +10.0;      
        Float minusTwo = -2.0;
        Float zero = +0.0;       
        assertEquals(ten, ten.plus(zero));
        assertEquals(+10.0, +10.0 + (+0.0));
        assertEquals(+10.0, +10.0 + (-0.0));
        assertEquals(+8.0, ten.plus(minusTwo));
        assertEquals(+8.0, +10.0 + (-2.0));
        assertEquals(minusTwo, minusTwo.plus(zero));
        assertEquals(-2.0, -2.0 + (+0.0));
        assertEquals(-4.0, -2.0 + (-2.0));   
    }	
	
	@test
	shared void testTimes() {
		Float ten = +10.0;
		Float seven = +7.0;
		Float minusThree = -3.0;
		Float zero = +0.0;		
		assertEquals(zero, ten.times(zero));
		assertEquals(zero, zero.times(ten));
		assertEquals(+70.0, ten.times(seven));
		assertEquals(+3.0, minusThree.times(-1.0));
		assertEquals(-10.0, ten.times(-1.0));
		assertEquals(+240.0, +30.0 * +8.0);
	}	
	
	@test
	shared void testPower() {		
		Float two = +2.0;
		Float four = +4.0;		
		Float zero = +0.0;		
		assertEquals(+1.0,two.power(zero));
		assertEquals(four,two.power(+2.0));
	}	
	
	@test
	shared void testDivided() {
		Float ten = +10.0;		
		Float two = +2.0;
		Float minusOne = -1.0;
		Float zero = +0.0;		
		assertEquals(zero,zero.divided(ten));
		assertEquals(+5.0,ten.divided(two));
		assertEquals(+1.0,ten.divided(ten));
		assertEquals(-2.0,two.divided(-1.0));
		assertEquals(+1.5, +3.0 / +2.0);
	}		
	
	@test
	shared void testMagnitude() {
		Float ten = +10.0;		
		Float minusTwo = -2.0;		
		Float minusZero = -0.0;				
		Float zero = +0.0;
		assertEquals(zero,zero.magnitude);
		assertEquals(zero,minusZero.magnitude);
		assertEquals(ten,ten.magnitude);		
		assertEquals(+2.0,minusTwo.magnitude);
		assertEquals(+4.0,(-4.0).magnitude);
		assertEquals(+4.0,(+4.0).magnitude);
	}
	
	@test
	shared void testFractionalPart() {
		Float ten = +10.5;		
		Float minusTwo = -2.3;
		Float zero = +0.0;
		assertEquals(zero,zero.fractionalPart);
		assertEquals(+0.3,minusTwo.fractionalPart);
		assertEquals(+0.5,ten.fractionalPart);		
	}
	
	@test
	shared void testWholePart() {
		Float ten = +10.5;		
		Float minusTwo = -2.3;
		Float zero = +0.0;
		assertEquals(zero,zero.wholePart);
		assertEquals(-2.0,minusTwo.wholePart);
		assertEquals(+10.0,ten.wholePart);		
	}	
	
	@test
	shared void testPositive() {
		Float ten = +10.5;		
		Float minusTwo = -2.3;		
		assertFalse(minusTwo.positive);
		assertTrue(ten.positive);		
		assertFalse((+0.0).positive);
		assertFalse((-0.0).positive);
	}		

	@test
	shared void testNegative() {
		Float ten = +10.5;		
		Float minusTwo = -2.3;		
		assertFalse(ten.negative);
		assertTrue(minusTwo.negative);
		assertFalse((+0.0).negative);
		assertFalse((-0.0).negative);		
	}
	
	@test
	shared void testFloat() {
		Float ten = +10.5;
		Float floatTen = ten.float;			
	}
	
	@test
	shared void testInteger() {
		Float ten = +10.5;
		Integer other = ten.integer;		
	}
	
	@test
	shared void testNatural() {
		Float ten = +10.5;
		Natural other = ten.natural;			
	}
	
	@test
	shared void testSign() {
		Float ten = +10.0;		
		Float minusTwo = -2.0;
		Float zero = +0.0;
		assertEquals(+0, zero.sign);
		assertEquals(+1, ten.sign);
		assertEquals(-1, minusTwo.sign);			
	}

    @test
    shared void testLargerThan() {
        Float ten = +10.5;      
        Float minusTwo = -2.3;
        Float zero = +0.0;
        assertFalse(zero.largerThan(ten));
        assertTrue(zero.largerThan(minusTwo));
        assertFalse(zero.largerThan(zero));        
        assertFalse(minusTwo.largerThan(minusTwo));             
        assertFalse(minusTwo.largerThan(zero));
        assertFalse(minusTwo.largerThan(ten));
        assertTrue(ten.largerThan(zero));
        assertTrue(ten.largerThan(minusTwo));
        assertFalse(ten.largerThan(ten));  
    }   
    
    @test
    shared void testAsLargeAs() {
        Float ten = +10.5;      
        Float minusTwo = -2.3;
        Float zero = +0.0;
        assertFalse(zero.asLargeAs(ten));
        assertTrue(zero.asLargeAs(minusTwo));
        assertTrue(zero.asLargeAs(zero));        
        assertTrue(minusTwo.asLargeAs(minusTwo));             
        assertFalse(minusTwo.asLargeAs(zero));
        assertFalse(minusTwo.asLargeAs(ten));
        assertTrue(ten.asLargeAs(zero));
        assertTrue(ten.asLargeAs(minusTwo));
        assertTrue(ten.asLargeAs(ten));  
    }  
    
    @test
    shared void testSmallerThan() {
        Float ten = +10.5;      
        Float minusTwo = -2.3;
        Float zero = +0.0;
        assertTrue(zero.smallerThan(ten));
        assertFalse(zero.smallerThan(minusTwo));
        assertFalse(zero.smallerThan(zero));        
        assertFalse(minusTwo.smallerThan(minusTwo));             
        assertTrue(minusTwo.smallerThan(zero));
        assertTrue(minusTwo.smallerThan(ten));
        assertFalse(ten.smallerThan(zero));
        assertFalse(ten.smallerThan(minusTwo));
        assertFalse(ten.smallerThan(ten));  
    }


    @test
    shared void testAsSmallAs() {
        Float ten = +10.5;      
        Float minusTwo = -2.3;
        Float zero = +0.0;
        assertTrue(zero.asSmallAs(ten));
        assertFalse(zero.asSmallAs(minusTwo));
        assertTrue(zero.asSmallAs(zero));        
        assertTrue(minusTwo.asSmallAs(minusTwo));             
        assertTrue(minusTwo.asSmallAs(zero));
        assertTrue(minusTwo.asSmallAs(ten));
        assertFalse(ten.asSmallAs(zero));
        assertFalse(ten.asSmallAs(minusTwo));
        assertTrue(ten.asSmallAs(ten));  
    }

    @test
    shared void testComparisionSmaller() {
        Float ten = +10.5;      
        Float minusTwo = -2.3;
        Float zero = +0.0;     
        assertEquals(smaller, minusTwo.compare(ten));
        assertEquals(smaller, minusTwo.compare(zero));
        assertFalse(smaller == minusTwo.compare(minusTwo));
        assertEquals(smaller, zero.compare(ten));
        assertFalse(smaller == zero.compare(minusTwo));
        assertFalse(smaller == zero.compare(zero));
        assertFalse(smaller == ten.compare(ten));
        assertFalse(smaller == ten.compare(zero));
        assertFalse(smaller == ten.compare(minusTwo));          
    }
    
    @test
    shared void testComparisionLarger() {
        Float ten = +10.5;      
        Float minusTwo = -2.3;
        Float zero = +0.0;     
        assertEquals(larger, ten.compare(minusTwo));
        assertEquals(larger, ten.compare(zero));
        assertFalse(larger == ten.compare(ten));
        assertEquals(larger, zero.compare(minusTwo));
        assertFalse(larger == zero.compare(zero));
        assertFalse(larger == zero.compare(ten));
        assertFalse(larger == minusTwo.compare(minusTwo));
        assertFalse(larger == minusTwo.compare(zero));
        assertFalse(larger == minusTwo.compare(ten));                           
    }
    
    @test
    shared void testComparisionEqual() {
        Float ten = +10.5;      
        Float minusTwo = -2.3;
        Float zero = +0.0;     
        assertFalse(equal == ten.compare(minusTwo));
        assertFalse(equal == ten.compare(zero));
        assertEquals(equal, ten.compare(ten));
        assertFalse(equal == zero.compare(minusTwo));
        assertEquals(equal, zero.compare(zero));
        assertFalse(equal == zero.compare(ten));
        assertEquals(equal, minusTwo.compare(minusTwo));
        assertFalse(equal == minusTwo.compare(zero));
        assertFalse(equal == minusTwo.compare(ten));                           
    }              	
		
}	  