package com.example.demo;

import java.net.Socket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.ip.IpHeaders;
import org.springframework.integration.ip.dsl.Tcp;
import org.springframework.integration.ip.tcp.connection.AbstractServerConnectionFactory;
import org.springframework.integration.ip.tcp.connection.TcpConnectionInterceptorFactory;
import org.springframework.integration.ip.tcp.connection.TcpConnectionInterceptorFactoryChain;
import org.springframework.integration.ip.tcp.connection.TcpConnectionInterceptorSupport;
import org.springframework.integration.ip.tcp.connection.TcpConnectionOpenEvent;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;

@SpringBootApplication
public class ConnectionLeakTestApplication {	

	public static void main(String[] args) {
		SpringApplication.run(ConnectionLeakTestApplication.class, args);
	}

	@Value("${port:5000}")
	private int port;

	@Value("${iterations:100}")
	private int iterations;

	@Bean
	public ApplicationRunner applicationRunner() {
		// simulated port probing
		return args -> {
			int i = iterations;
			while (i-- > 0) {
				Socket socket = new Socket("localhost", 5000);
				socket.close();
			}
		};
	}

	@Bean
	public ApplicationListener<TcpConnectionOpenEvent> listener() {
		return new ApplicationListener<TcpConnectionOpenEvent>() {

			@Override
			public void onApplicationEvent(TcpConnectionOpenEvent event) {
				// this will trigger an exception, as is expected (socket is closed)
				mainChannel().send(MessageBuilder.withPayload("hello")
						.setHeader(IpHeaders.CONNECTION_ID, event.getConnectionId())
						.build());
			}
		};
	}

	@Bean
	TcpConnectionInterceptorFactory interceptorFactory() {
		return new DummyInterceptorFactory();
	}

	@Bean
	TcpConnectionInterceptorFactoryChain interceptorFactoryChain() {
		TcpConnectionInterceptorFactoryChain interceptorFactoryChain = new TcpConnectionInterceptorFactoryChain();
		interceptorFactoryChain.setInterceptors(new TcpConnectionInterceptorFactory[] { interceptorFactory() });
		return interceptorFactoryChain;
	}


	@Bean
	AbstractServerConnectionFactory serverConnectionFactory() {
		return Tcp.netServer(port)
				.interceptorFactoryChain(interceptorFactoryChain())
				.get();
	}

	@Bean
	MessageChannel mainChannel() {
		return MessageChannels.direct().get();
	}

	@Bean
	IntegrationFlow inboundFlow() {
		return IntegrationFlows.from(Tcp.inboundAdapter(serverConnectionFactory())
				.outputChannel(mainChannel()))
				.get();
	}

	@Bean
	IntegrationFlow outboundFlow() {
		return IntegrationFlows.from(mainChannel())
				.handle(Tcp.outboundAdapter(serverConnectionFactory()))
				.get();
	}

	static class DummyInterceptorFactory implements TcpConnectionInterceptorFactory, ApplicationEventPublisherAware { 
		private ApplicationEventPublisher applicationEventPublisher;

		@Override
		public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
			this.applicationEventPublisher = applicationEventPublisher;;
		}

		@Override
		public TcpConnectionInterceptorSupport getInterceptor() {
			return new DummyInterceptor(applicationEventPublisher);
		}
		
	}

	static class DummyInterceptor extends TcpConnectionInterceptorSupport {

		public DummyInterceptor(ApplicationEventPublisher applicationEventPublisher) {
			super(applicationEventPublisher);
		}
	}
}
