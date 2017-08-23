package info.xiaomo.gameCore.protocol;

import info.xiaomo.gameCore.base.common.EncryptUtil;
import info.xiaomo.gameCore.protocol.handler.EchoHandler;
import info.xiaomo.gameCore.protocol.handler.MessageDecoder;
import info.xiaomo.gameCore.protocol.handler.MessageEncoder;
import info.xiaomo.gameCore.protocol.handler.MessageExecutor;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class NetworkService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkService.class);

    private static final int SERVER_OPEN = 1;

    private static final int SERVER_CLOSE = 2;

    private int port;

    private EventLoopGroup bossGroup;

    private EventLoopGroup workerGroup;

    private ServerBootstrap bootstrap;

    private int state = 0;

    private static final byte STATE_STOP = 0;
    private static final byte STATE_START = 1;


    NetworkService(final NetworkServiceBuilder builder) {
        int bossLoopGroupCount = builder.getBossLoopGroupCount();
        int workerLoopGroupCount = builder.getWorkerLoopGroupCount();
        this.port = builder.getPort();

        bossGroup = new NioEventLoopGroup(bossLoopGroupCount);
        workerGroup = new NioEventLoopGroup(workerLoopGroupCount);

        EncryptUtil.initEncryptor(builder.getEncryptor());

        bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup);
        bootstrap.channel(NioServerSocketChannel.class);
        bootstrap.option(ChannelOption.SO_BACKLOG, 1024);
        bootstrap.childOption(ChannelOption.TCP_NODELAY, true);
        bootstrap.childOption(ChannelOption.SO_RCVBUF, 128 * 1024);
        bootstrap.childOption(ChannelOption.SO_SNDBUF, 128 * 1024);

        bootstrap.handler(new LoggingHandler(LogLevel.DEBUG));
        bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pip = ch.pipeline();
                pip.addLast("NettyMessageDecoder", new MessageDecoder(builder.getMessagePool()));
                pip.addLast("EchoHandler", new EchoHandler());
                pip.addLast("NettyMessageEncoder", new MessageEncoder());
                pip.addLast("NettyMessageExecutor", new MessageExecutor(builder.getConsumer(), builder.getListener()));
                for (ChannelHandler handler : builder.getExtraHandlers()) {
                    pip.addLast(handler);
                }
            }
        });

    }

    public void start() {
        try {
            ChannelFuture f = bootstrap.bind(port);
            f.sync();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        this.state = STATE_START;
        LOGGER.info("Server on port:{} is start", port);
    }


    public void stop() {
        this.state = STATE_STOP;
        Future<?> bf = bossGroup.shutdownGracefully();
        Future<?> wf = workerGroup.shutdownGracefully();
        try {
            bf.get(5000, TimeUnit.MILLISECONDS);
            wf.get(5000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            LOGGER.info("Netty服务器关闭失败", e);
        }
        LOGGER.info("Netty Server on port:{} is closed", port);
    }

    public boolean isRunning() {
        return this.state == STATE_START;
    }

}
