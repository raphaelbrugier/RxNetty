package io.reactivex.netty.server;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.UnpooledConnectionFactory;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.pipeline.PipelineConfiguratorComposite;
import io.reactivex.netty.pipeline.RxRequiredConfigurator;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Nitesh Kant
 */
@SuppressWarnings("rawtypes")
public class AbstractServer<I, O, B extends AbstractBootstrap<B, C>, C extends Channel, S extends AbstractServer> {

    protected enum ServerState {Created, Starting, Started, Shutdown}

    protected final UnpooledConnectionFactory<I,O> connectionFactory;
    protected final B bootstrap;
    protected final int port;
    protected final AtomicReference<ServerState> serverStateRef;
    protected ErrorHandler errorHandler;
    private ChannelFuture bindFuture;

    public AbstractServer(B bootstrap, int port) {
        if (null == bootstrap) {
            throw new NullPointerException("Bootstrap can not be null.");
        }
        serverStateRef = new AtomicReference<ServerState>(ServerState.Created);
        this.bootstrap = bootstrap;
        this.port = port;
        connectionFactory = new UnpooledConnectionFactory<I, O>();
    }

    public void startAndWait() {
        start();
        try {
            waitTillShutdown();
        } catch (InterruptedException e) {
            Thread.interrupted();
        }
    }

    public S start() {
        if (!serverStateRef.compareAndSet(ServerState.Created, ServerState.Starting)) {
            throw new IllegalStateException("Server already started");
        }

        try {
            bindFuture = bootstrap.bind(port).sync();
            if (!bindFuture.isSuccess()) {
                throw new RuntimeException(bindFuture.cause());
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        serverStateRef.set(ServerState.Started); // It will come here only if this was the thread that transitioned to Starting

        return returnServer();
    }

    /**
     * A catch all error handler which gets invoked if any error happens during connection handling by the configured
     * {@link ConnectionHandler}.
     *
     * @param errorHandler Error handler to invoke when {@link ConnectionHandler} threw an error.
     *
     * @return This server instance.
     */
    public S withErrorHandler(ErrorHandler errorHandler) {
        if (serverStateRef.get() == ServerState.Started) {
            throw new IllegalStateException("Error handler can not be set after starting the server.");
        }
        this.errorHandler = errorHandler;
        return returnServer();
    }

    public void shutdown() throws InterruptedException {
        if (!serverStateRef.compareAndSet(ServerState.Started, ServerState.Shutdown)) {
            throw new IllegalStateException("The server is already shutdown.");
        } else {
            bindFuture.channel().close().sync();
        }
    }

    @SuppressWarnings("fallthrough")
    public void waitTillShutdown() throws InterruptedException {
        ServerState serverState = serverStateRef.get();
        switch (serverState) {
            case Created:
            case Starting:
                throw new IllegalStateException("Server not started yet.");
            case Started:
                bindFuture.channel().closeFuture().await();
                break;
            case Shutdown:
                // Nothing to do as it is already shutdown.
                break;
        }
    }

    @SuppressWarnings("unchecked")
    protected S returnServer() {
        return (S) this;
    }

    protected ChannelInitializer<Channel> newChannelInitializer(final PipelineConfigurator<I, O> pipelineConfigurator,
                                                                      final ConnectionHandler<I, O> connectionHandler) {
        return new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                RxRequiredConfigurator<I, O> requiredConfigurator = new RxRequiredConfigurator<I, O>(connectionHandler,
                                                                                                     connectionFactory,
                                                                                                     errorHandler);
                PipelineConfigurator<I, O> configurator;
                if (null == pipelineConfigurator) {
                    configurator = requiredConfigurator;
                } else {
                    configurator = new PipelineConfiguratorComposite<I, O>(pipelineConfigurator, requiredConfigurator);
                }
                configurator.configureNewPipeline(ch.pipeline());
            }
        };
    }
}
