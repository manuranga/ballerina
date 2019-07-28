// Copyright (c) 2019 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

# Represents ActiveMQ Artemis Session.
public type Session client object {
    boolean anonymousSession = false;

    public function __init(public Connection con, public SessionConfiguration? config = ()) {
        SessionConfiguration configuration = {};
        if (config is SessionConfiguration) {
            configuration = config;
        }
        self.createSession(con, configuration);
    }

    function createSession(Connection con, SessionConfiguration config) = external;

    # Returns true if close was already called
    # 
    # + return - `true` if closed, `false` otherwise.
    public function isClosed() returns boolean = external;

    # Closes the connection and release all its resources
    #
    # + return - `error` if an error occurs closing the connection or nil
    public remote function close() returns Error? = external;
};

# Configurations related to a Artemis Session.
#
# + username - The username
# + password - The password
# + autoCommitSends - `true` to automatically commit message sends, `false` to use transaction block for committing
# + autoCommitAcks - `true` to automatically commit message acknowledgement, `false` to use transaction block for
# committing
public type SessionConfiguration record {|
    string? username = ();
    string? password = ();
    boolean autoCommitSends = true;
    boolean autoCommitAcks = true;
|};
