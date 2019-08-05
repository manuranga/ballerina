// Copyright (c) 2018 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/http;
import ballerinax/java.jdbc;
import ballerina/io;

@http:ServiceConfig {
    basePath:"/test"
}
service metricsTest on new http:Listener(9090){
    @http:ResourceConfig {
        path: "/"
    }
    resource function getProduct (http:Caller caller, http:Request req) {
        jdbc:Client testDB =  new({
            url: "jdbc:h2:file:../../tempdb/TEST_DB",
            username: "SA",
            password: "",
            poolOptions: { maximumPoolSize: 10 }
        });
        var dbResult = testDB -> select("SELECT * FROM Products", ());
        io:println(dbResult);
        if (dbResult is table<record {}>) {
            var jData = json.constructFrom(dbResult);
            if (jData is json) {
                string result = jData.toString();
                http:Response resp = new;
                resp.setTextPayload(<@untainted> result);
                checkpanic caller->respond(resp);
            }  else {
                error err = error ("error occurred 1111");
                panic err;
            }
        } else {
            error err = error ("error occurred 2222");
            panic err;
        }
    }
}
