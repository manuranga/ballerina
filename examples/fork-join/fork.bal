import ballerina/io;

public function main() {

    // The fork-join allows developers to spawn (fork) multiple workers within any execution flow of
    // a Ballerina program.
    fork {
        worker w1 returns (int, string) {
            int i = 23;
            string s = "Colombo";
            io:println("[w1] i: ", i, " s: ", s);
            // Returns via the future of worker w1.
            return (i, s);
        }

        worker w2 returns float {
            float f = 10.344;
            io:println("[w2] f: ", f);
            // Returns via the future of worker w2.
            return f;
        }
    }

    // Workers are visible outside the fork as futures.
    // Curly braced wait will block for both workers to finish.
    record{(int,string) w1; float w2;} results = wait {w1, w2};

    // Resulting record contains returned values from each worker, with field name as worker name.
    var (iW1, sW1) = results.w1;
    var fW2 = results.w2;
    io:println("[main] iW1: ", iW1, " sW1: ", sW1, " fW2: ", fW2);
}
