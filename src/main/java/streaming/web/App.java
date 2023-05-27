package streaming.web;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public class App {
    private static final int PORT = 5555;
    private static Logger logger;
    private static ExecutorService executor;

    static {
        logger = new Logger(App.class.getName());
        final ThreadFactory f = Thread.ofVirtual().name("routine-", 0).factory();
        executor = Executors.newThreadPerTaskExecutor(f);
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
        executor.submit(() -> {
            try {
                logger.info(Thread.currentThread().toString());
                final ByteBuffer readBuffer = ByteBuffer.allocate(65536);
                while (true) {
                    logger.info("Count: " + count.getAndIncrement() + "channel " + chan);
                    chan.configureBlocking(true);
                    int read = chan.read(readBuffer.clear());
                    if (read == -1 || read == 0) {
                        logger.info("Closing channel. read: " + read);
                        chan.close();
                        break;
                    } else {
                        var bytes = new byte[read];
                        readBuffer.get(0, bytes);
                        String response = HttpProcessor.process(bytes);

                        chan.write(ByteBuffer.wrap(response.getBytes()));
                    }
                }
            } catch (IOException e) {
                try {
                    if (chan.isOpen()){
                        chan.close();
                        logger.error("Closing channel");
                    }
                } catch (IOException ex) {
                    logger.info("throw new RuntimeException(ex");
                }
            } finally {
                try {
                    if (chan.isOpen()) {
                        chan.close();
                        logger.info("Closing channel");
                    }
                } catch (IOException e) {
                    logger.error("Couldn't close chan in virtual thread");
                }
            }
        });
    }
}