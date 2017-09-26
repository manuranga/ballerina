import ballerina.lang.system;

function workerDeclTest() {
   worker w1 {
     int a = 10;
     int c = a + 1;
   }
   worker w2 {
     int a = 20;
     a = a + 1;
     int c = 1;
   }
}

function forkJoinWithMessageParsingTest() (int) {
    int x = 5;
    fork {
	   worker w1 {
	     int a = 5;
	     int b = 0;
	     a -> w2;
	     b <- w2;
	   }
	   worker w2 {
	     int a = 0;
	     int b = 15;
	     a <- w1;
	     b -> w1;
	   }
	} join (all) (map results) { system:println(results); }
	return x;
}

function forkJoinWithSingleForkMessages() (int) {
    int x = 5;
    fork {
	   worker w1 {
	     int a = 5;
	     int b = 0;
	     a -> w2;
	     b <- w2;
	     a -> fork;
	   }
	   worker w2 {
	     int a = 0;
	     int b = 15;
	     a <- w1;
	     b -> w1;
	     b -> fork;
	   }
	} join (all) (map results) { system:println(results); }
	return x;
}

function basicForkJoinTest() (int) {
    int x = 10;
    fork {
	   worker w1 {
	     int a = 5;
	     int b = a + 1;
	   }
	   worker w2 {
	     int a = 0;
	     int b = 15;
	   }
	} join (all) (map results) { }
	return x;
}

function forkJoinWithMultipleForkMessages() (int) {
    int x = 5;
    fork {
	   worker w1 {
	     int a = 5;
	     int b = 0;
	     a -> w2;
	     b <- w2;
	     a, b -> fork;
	   }
	   worker w2 {
	     int a = 0;
	     int b = 15;
	     a <- w1;
	     b -> w1;
	     a, b -> fork;
	   }
	} join (all) (map results) {  system:println(results);  }
	return x;
}

function simpleWorkerMessagePassingTest() {
   worker w1 {
     int a = 10;
     a -> w2;
     a <- w2;
   }
   worker w2 {
     int a = 0;
     int b = 15;
     a <- w1;
     b -> w1;
   }
}

