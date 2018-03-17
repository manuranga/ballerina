import ballerina/io;

io:Socket socket;

function openSocketConnection (string host, int port) {
    socket = io:openSocket(host, port, {});
}
function openSocketConnectionWithProps (string host, int port, io:SocketProperties prop) (io:Socket) {
    return io:openSocket(host, port, prop);
}

function closeSocket () {
    socket.closeSocket();
}

function write (blob content, int size) (int) {
    int numberOfBytesWritten;
    io:ByteChannel channel = socket.channel;
    numberOfBytesWritten, _ = channel.write(content, 0, size);
    return numberOfBytesWritten;
}

function read (int size) (blob, int) {
    io:ByteChannel channel = socket.channel;
    blob readContent;
    int readSize;
    readContent, readSize, _ = channel.read(size, 0);
    return readContent, readSize;
}

function close (io:Socket socket) {
    socket.closeSocket();
}
