import ballerina.lang.system;

function forkJoinWithTimeoutTest1() (map) {
    map m = {};
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
	     system:sleep(5000);
	   }
    } join (all) (map results) { m["x"] = 25; } timeout (1) (map results) { m["x"] = 15; }
    return m;
}

function forkJoinWithTimeoutTest2() (map) {
    map m = {};
    fork {
	   worker w1 {
	     int a = 5;
	     int b = 0;
	   }
	   worker w2 {
	     int a = 0;
	     int b = 15;
	     system:sleep(100);
	   }
    } join (all) (map results) { m["x"] = 25; } timeout (5) (map results) { m["x"] = 15; }
    return m;
}