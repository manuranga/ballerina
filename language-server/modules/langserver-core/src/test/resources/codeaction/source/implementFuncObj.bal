public type BaseConnector abstract object {
    public function pushText1(string text) returns error?;
};

public type Connector abstract object {
    *BaseConnector;

    public function pushText2(string text) returns error?;
    public function pushText3(string text) returns error?;
};

public type WSConnector object {
    *Connector;
    public function pushText4(string text) returns error? {
        return ();
    }
};
