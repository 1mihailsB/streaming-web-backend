package streaming.web;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.atomic.AtomicLong;

public class App {
    private static final int PORT = 5555;
    private static Logger logger;

    static {
        logger = new Logger(App.class.getName());
    }

    public static void main(String[] args) throws IOException {
        var serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);

        // bind to local port
        var isa = new InetSocketAddress("localhost", PORT);
        serverSocketChannel.socket().bind(isa);
        // selectors
        var serverSelector = SelectorProvider.provider().openSelector();
        serverSocketChannel.register(serverSelector, SelectionKey.OP_ACCEPT);

        // Examples: sun/net/httpserver/ServerImpl.java:509

        while (true) {
            /**
             * TODO: select() has overload with msTimeout param that will return after given timeout.
             * If sockets didn't get any data during that time - we can close them if we want.
             */
            serverSelector.select();
            var selectedKeys = serverSelector.selectedKeys().iterator();
            while (selectedKeys.hasNext()) {
                var key = selectedKeys.next();
//                logger.info("Key: " + key);

                selectedKeys.remove();
                if (!key.isValid()) {
                    logger.info("Key invalid");
                }

                if (key.channel().equals(serverSocketChannel)) { // or `key.isAcceptable()`
                    var socketChannel = serverSocketChannel.accept(); // new connection from client
                    socketChannel.configureBlocking(false);
                    socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                    socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                    socketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
//                    int interest = SelectionKey.OP_READ | SelectionKey.OP_WRITE;
                    int interest = SelectionKey.OP_READ;
                    socketChannel.register(serverSelector, interest);
                } else if (key.isReadable()) { // incoming data on existing connection
                    key.cancel();
                    // call the selector just to process the cancelled keys
//                    serverSelector.selectNow();
                    var chan = (SocketChannel) key.channel();
                    processIncoming(chan);
                }
            }
        }
    }

    static AtomicLong count = new AtomicLong(0);

    static void processIncoming(SocketChannel chan) {
        Thread.ofVirtual().name("Request handler vThread").start(() -> {
            try {
                final ByteBuffer readBuffer = ByteBuffer.allocate(65536);
                while (true) {
                    logger.info("Count: " + count.getAndIncrement() + "channel " + chan);
//                    Thread.sleep(Duration.ofSeconds(2));
                    chan.configureBlocking(true);
                    int read = chan.read(readBuffer.clear());
                    if (read == -1 || read == 0) {
                        logger.info("Closing channel. read: " + read);
                        chan.close();
                        break;
                    } else {
                        var bytes = new byte[read];
                        readBuffer.get(0, bytes);
                        logger.info((String.format(
                                "Data from client channel:\n%s:\n----------------------------------------\n%s",
                                chan,
                                new String(bytes)
                        )));

                        String html = "{\"data\": \"custom html response\"}";
                        final String CRLF = "\n\r"; // 13, 10
                        String response =
                                "HTTP/2.0 200 OK" + CRLF
                                        + "Access-Control-Allow-Origin: *" + CRLF
                                        + "Content-Length: " + (html.getBytes().length + CRLF.getBytes().length) + CRLF
                                        + CRLF
                                        + html
                                        + CRLF + CRLF;

                        // ((SocketChannel) key.channel()).write(ByteBuffer.wrap(bytes));
                        chan.write(ByteBuffer.wrap(response.getBytes()));
                    }
                }
            } catch (IOException e) {
                try {
                    if (chan.isOpen()) chan.close();
                } catch (IOException ex) {
                    logger.info("throw new RuntimeException(ex");
                }
            } finally {
                try {
                    if (chan.isOpen()) chan.close();
                } catch (IOException e) {
                    logger.info("Couldn't close chan in virtual thread");
                }
            }
        });
    }
}