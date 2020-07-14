##介绍：
	RabbitMQ是消息代理，能收发消息。
	可以把它想象为邮局，当你往邮箱投递邮件时，邮递员会把邮件派送到收件人手中。
	这个类比中，RabbitMQ是一个邮箱、邮局、邮递员。
	
	RabbitMQ和邮局最大的区别是，RabbitMQ处理的不是纸质，而是接收、存储、转发二进制数据信息。
	通常RabbitMQ和消息传递会用到一些术语。
		生产（producing）就是发送的意思；程序A发送信息，A就是生产者。
		队列（queue）就是上述“邮箱”，驻在RabbitMQ里。尽管信息传递流经RabbitMQ和你的应用，但消息只能存储在队列。
			队列只能绑定在主机的内存和磁盘，它本质上就是一块大的缓冲区。多个生产者可以发送消息到同一个队列，然后
			多个消费者可以尝试从一个队列接收消息。
		消费（consuming）意思类似接收，一个消费者程序主要就是等待接收消息。
		注意：生产者、消费者、代理不一定是在同一个主机上（确实很多应用都是这样），一个应用可以同时是生产者和消费者。
	
###"HELLO WORLD"：
		教程这部分会用java写两个程序，一个发送消息的生产者，和一个接收消息并打印出来的消费者。我们会略过一些java API细节
		下图中的P是生产者，C是消费者。中间的箱子是队列-- 是RabbitMQ代表消费者保留的消息缓冲区。
		
		####java版client库：
			RabbitMQ有多中协议，这里用的是AMQP0-9-1，一个开源、通用的消息协议。可以用很多语言实现RabbitMQ客户端，这里用java。
			下载client库，以及它的依赖SLF4J和SLF4J Simple，复制到你的工作目录，这些文件贯穿整个教程。
			请注意，SLF4J Simple对教程来说够用了，但在生产环境应该用更成熟的日志框架ruLogback。
			（RabbitMQ java客户端同时也在Maven中心库中，gourpId是com.rabbitmq，artifactId是amqp-client）
		
		####“发送”的代码：
		public class Send {
			public final static String QUEUE_NAME = "M1";
			public static void main(String[] args) throws Exception {
				ConnectionFactory factory = new ConnectionFactory();
				factory.setHost("localhost");
				try (Connection connection = factory.newConnection();
					 Channel channel = connection.createChannel()) {
					
				}
			}
		}
			connection是socket connection的抽象，主要处理协议版本协商和认证等，我们在本地机器（localhost）连接RabbitMQ节点
		如果想要连接别的机器的节点，我们可以简单指定主机名或者IP地址。
			接着创建通道（channel），大多数API都在这完成，必须使用try catch捕获，因为Connection和Channel都是java.io.Closeable底下的，用这种方式
		可以不用显示调用close方法。
			我要定义一个队列，这样我们可以将消息发布到队列，这部分都需try catch捕获处理。
			声明一个队列是幂等的--当不存在时才会去创建，消息内容是字节数组形式，所以你可以用你喜欢的编码格式。
	
		“接收”	
			消费者从RabbitMQ监听消息，保持消费者一直运行去监听消息并打印出来。
			DeliverCallback 用来缓冲服务器发送的消息。
			跟生产者一样，创建连接和通道，声明一个我们准备要从中获取消息的队列（要和生产者中定义的队列名匹配）。·
			要告诉服务器从队列中传递消息给我们，因为它是异步推送消息，我们提供了一个回调以一个对象的形式，这个回调会缓冲消息直到我们准备消费。
		
			import com.rabbitmq.client.Channel;
			import com.rabbitmq.client.Connection;
			import com.rabbitmq.client.ConnectionFactory;
			import com.rabbitmq.client.DeliverCallback;

			public class Recv {

				private final static String QUEUE_NAME = "hello";

				public static void main(String[] argv) throws Exception {
					ConnectionFactory factory = new ConnectionFactory();
					factory.setHost("localhost");
					Connection connection = factory.newConnection();
					Channel channel = connection.createChannel();

					channel.queueDeclare(QUEUE_NAME, false, false, false, null);
					System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

					DeliverCallback deliverCallback = (consumerTag, delivery) -> {
						String message = new String(delivery.getBody(), "UTF-8");
						System.out.println(" [x] Received '" + message + "'");
					};
					channel.basicConsume(QUEUE_NAME, true, deliverCallback, consumerTag -> { });
				}
			}
